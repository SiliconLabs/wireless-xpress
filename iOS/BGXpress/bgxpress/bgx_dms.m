/*
 * Copyright 2018-2019 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import "bgx_dms.h"
#import "SafeType.h"
#import "bgxpress.h"

NSString * kBGX13SPartID         = @"080447D0";
NSString * kBGX13PPartID         = @"4C892A6A";
NSString * kBGX13InvalidPartID   = @"BAD1DEAD";
NSString * kBGXV3SPartID         = @"F65FD7F0";
NSString * kBGXV3PPartID         = @"76786556";

typedef void (^VersionsListCompletionBlock)(NSError *, NSArray *);

const char * kDMSServer = "bgx13.zentri.com";

NSTimeInterval kDMSServerTimeout = 30.0f;

NSString * DMSServerReachabilityChangedNotificationName = @"DMSServerReachabilityChangedNotificationName";

#define DMS_API_KEY @"DMS_API_KEY"

NSString * NewBGXFirmwareListNotificationName = @"NewBGXFirmwareListNotificationName";

@interface bgx_dms()

/*
  Returns the correct local versions file for the current device type
  already expanded.
 */
- (NSString *)localVersionsFile;

- (void)reachabilityCallback:(SCNetworkReachabilityRef)reachRef withFlags:(SCNetworkReachabilityFlags)flags;

- (void)loadFirmwareList;

- (NSURL *)versionsURL;
- (NSURL *)firmwareURL:(NSString *)svers;

@property (nonatomic, strong) NSString * bgx_unique_device_id;
@property (nonatomic) BOOL dms_reachable;

@property (nonatomic, strong) NSTimer * reachabilityListRefreshTimer;

@property (nonatomic, strong) NSTimer * versionsListTimeout;
@property (nonatomic, strong) VersionsListCompletionBlock versionsListCompletion;

@end

static void MyReachabilityCallback(SCNetworkReachabilityRef target, SCNetworkReachabilityFlags flags, void * info)
{
  if ([(__bridge bgx_dms *)info isKindOfClass:[bgx_dms class]]) {
    bgx_dms * me = (__bridge bgx_dms *)info;

    [me reachabilityCallback:target withFlags:flags];
  }
}

@interface bgx_dms()

@property CFRunLoopRef runloop4Reachability;
@end

@implementation bgx_dms

- (id)initWithBGXUniqueDeviceID:(NSString *)bgx_unique_device_id
{
  self = [super init];
  if (self) {

      self.versionsListCompletion = nil;
      
    _reachabilityRef = nil;
      
    self.bgx_unique_device_id = bgx_unique_device_id;

    // load the list from the file.
    [self loadFirmwareList];

    _reachabilityRef = SCNetworkReachabilityCreateWithName(NULL, kDMSServer);
    SCNetworkReachabilityContext context = { 0, (__bridge void *)(self), NULL, NULL, NULL };

    Boolean result = SCNetworkReachabilitySetCallback(_reachabilityRef, MyReachabilityCallback, &context);
    NSAssert(result, @"SCNetworkReachabilitySetCallback failed.");

    self.runloop4Reachability = CFRunLoopGetCurrent();
    result = SCNetworkReachabilityScheduleWithRunLoop(_reachabilityRef, self.runloop4Reachability, kCFRunLoopDefaultMode);

    NSAssert(result, @"SCNetworkReachabilityScheduleWithRunLoop failed.");

  }
  return self;
}

- (void)dealloc
{
    if (_reachabilityRef) {
        SCNetworkReachabilityUnscheduleFromRunLoop(_reachabilityRef, self.runloop4Reachability, kCFRunLoopDefaultMode );
        CFRelease(_reachabilityRef);
        _reachabilityRef = nil;
    }
    self.runloop4Reachability = nil;
}

- (void)reachabilityCallback:(SCNetworkReachabilityRef)reachRef withFlags:(SCNetworkReachabilityFlags)flags
{
  BOOL v = (BOOL) ((flags & kSCNetworkReachabilityFlagsReachable) ? YES : NO);

  if (self.dms_reachable != v) {
    self.dms_reachable = v;
      
      if (self.versionsListCompletion) {
          [self.versionsListTimeout invalidate];
          self.versionsListTimeout = nil;
          [self retrieveAvailableVersions:self.versionsListCompletion];
      }
      
    [[NSNotificationCenter defaultCenter] postNotificationName:DMSServerReachabilityChangedNotificationName object:[NSNumber numberWithBool: flags & kSCNetworkReachabilityFlagsReachable ? YES : NO] ];
  }
}

- (void)reachabilityChanged:(NSNotification *)n
{
  if (self.dms_reachable) {

      /* The user might choose to call retrieveAvailableVersions: themselves
       when the server becomes reachable. Or they can just let this object do
       its job on its own and instead just grab the cached versions list and let
       loading from DMS happen automatically. This timer gives them a 1 second
       window where they can call retrieveAvailableVersions: themselves before we
       do it for them.
       */

    self.reachabilityListRefreshTimer = [NSTimer scheduledTimerWithTimeInterval:1.0
                                                                        repeats:NO
                                                                          block:^(NSTimer * timer) {

        // get the list of available versions.
      [self retrieveAvailableVersions:nil];
    }];
  }
}

+ (NSString *)dms_api_key
{
    NSString * api_key = [[[NSBundle mainBundle] infoDictionary] objectForKey:DMS_API_KEY];
    NSAssert(api_key, @"No API Key in Info.plist file. Add your API key as DMS_API_KEY in your app's Info.plist file.");
    if ( 0 == api_key.length) {
        NSLog(@"Warning: The DMS_API_KEY supplied in your app's Info.plist is blank. Contact Silicon Labs xpress@silabs.com for a DMS API Key for BGX.");
    }
    return api_key;
}

- (void)retrieveAvailableVersions:(void (^)(NSError *, NSArray *))completionBlock
{
  [self.reachabilityListRefreshTimer invalidate];
  self.reachabilityListRefreshTimer = nil;

  if (!self.dms_reachable) {
    if (completionBlock) {
        [self loadFirmwareList];
        
        if (self.firmwareList) {
            (completionBlock)(nil, self.firmwareList);
        } else {
            
            self.versionsListCompletion = completionBlock;
            self.versionsListTimeout = [NSTimer scheduledTimerWithTimeInterval:30.0 repeats:NO block:^(NSTimer * timer){
                self.versionsListTimeout = nil;
                (completionBlock)([NSError errorWithDomain:NSNetServicesErrorDomain code:-1 userInfo:@{@"description" : @"DMS Server Unreachable."}], nil);
                self.versionsListCompletion = nil;
            }];
        }
    }
    return;
  }
  
  NSMutableURLRequest * mur = [[NSMutableURLRequest alloc] initWithURL:[self versionsURL] cachePolicy:NSURLRequestReloadIgnoringCacheData timeoutInterval:kDMSServerTimeout];

  [mur setValue:[bgx_dms dms_api_key] forHTTPHeaderField:@"x-api-key"];

  if (self.bgx_unique_device_id) {
    [mur setValue:self.bgx_unique_device_id forHTTPHeaderField:@"x-device-uuid"];
  }

  [[[NSURLSession sharedSession] downloadTaskWithRequest:mur completionHandler:^(NSURL * location, NSURLResponse * response, NSError * error){

    if (error) {
      NSLog(@"Error encountered during DMS transaction: %@", [error description]);
      (completionBlock)(error, nil);
      return;
    }

    if ([response isKindOfClass:[NSHTTPURLResponse class]]) {
      NSHTTPURLResponse * httpResponse = (NSHTTPURLResponse *)response;
      if (200 == httpResponse.statusCode) {

        NSString * contentType = SafeType([httpResponse.allHeaderFields objectForKey:@"Content-Type"], [NSString class]);

        if ([contentType isEqualToString:@"application/json"]) {

            // This is the happy path.
          NSError * myError = nil;
          NSData * listData = [NSData dataWithContentsOfURL:location];
          self.firmwareList = SafeType([NSJSONSerialization JSONObjectWithData:listData options:0 error:&myError], [NSArray class]);

          [listData writeToFile:[self localVersionsFile] atomically:YES];

          [[NSNotificationCenter defaultCenter] postNotificationName:NewBGXFirmwareListNotificationName object:self.firmwareList];
          if (completionBlock) {
            (completionBlock)(nil, self.firmwareList);
          }
        }

      } else {
        NSLog(@"Unexpected http response from DMS Server: %ld %@", (long) httpResponse.statusCode, [httpResponse description]);
        if (completionBlock) {
          (completionBlock)([NSError errorWithDomain:NSCocoaErrorDomain code:httpResponse.statusCode userInfo:@{ @"NSHTTPURLResponse" : httpResponse }], nil);
        }
      }
    } else {
      NSLog(@"Unexpected response type from DMS Server: %@", [response description]);
      if (completionBlock) {
        (completionBlock)([NSError errorWithDomain:NSCocoaErrorDomain code:-1 userInfo:@{ @"NSURLResponse" : response }], nil);
      }
    }

  }] resume];
}

- (void)loadFirmwareList
{
    if ([[NSFileManager defaultManager] fileExistsAtPath:[self localVersionsFile]] ) {
        NSError * myError = nil;
        NSData * jsonData = [NSData dataWithContentsOfFile:[self localVersionsFile]];

        self.firmwareList = [NSJSONSerialization JSONObjectWithData:jsonData options:0 error:&myError];
        if (myError) {
            NSLog(@"Error loading file: %@", [myError description]);
            myError = nil;
            [[NSFileManager defaultManager] removeItemAtPath:[self localVersionsFile] error:&myError];
            if (myError) {
                NSLog(@"Error failed to remove file: %@", [myError description]);
            }
        }
    }
}

/*
  This call takes a firmware version and downloads it. Then we will take the call
  the completion block with the path to the firmware image.
 */
- (void)loadFirmwareVersion:(NSString *)version completion:(void (^)(NSError * error, NSString * firmware_path))completionBlock
{
  NSMutableURLRequest * mur = [[NSMutableURLRequest alloc] initWithURL:[self firmwareURL: version]
                                                           cachePolicy:NSURLRequestReloadIgnoringCacheData
                                                       timeoutInterval:kDMSServerTimeout];

  [mur setValue:[bgx_dms dms_api_key] forHTTPHeaderField:@"x-api-key"];

  if (self.bgx_unique_device_id) {
    [mur setValue:self.bgx_unique_device_id forHTTPHeaderField:@"x-device-uuid"];
  }

  [[[NSURLSession sharedSession] downloadTaskWithRequest:mur completionHandler:^(NSURL * location, NSURLResponse * response, NSError * error){

    if (error) {
      if (completionBlock) {
        (completionBlock)(error, nil);
      }
      return;
    }

    if ([response isKindOfClass:[NSHTTPURLResponse class]]) {
      NSHTTPURLResponse * httpResponse = (NSHTTPURLResponse *)response;
      if (200 == httpResponse.statusCode) {

        NSAssert([location isFileURL], @"Unexpected URL type.");

        if (completionBlock) {
          (completionBlock)(nil, [location path]);
        }

      } else {
        NSLog(@"Unexpected http response from DMS Server: %ld %@", (long) httpResponse.statusCode, [httpResponse description]);
        if (completionBlock) {
          (completionBlock)([NSError errorWithDomain:NSCocoaErrorDomain code:httpResponse.statusCode userInfo:@{ @"NSHTTPURLResponse" : httpResponse }], nil);
        }
      }
    } else {
      NSLog(@"Unexpected response type from DMS Server: %@", [response description]);
      if (completionBlock) {
        (completionBlock)([NSError errorWithDomain:NSCocoaErrorDomain code:-1 userInfo:@{ @"NSURLResponse" : response }], nil);
      }
    }

  }] resume];
}






- (NSURL *)versionsURL
{
   NSString * partid = [self.bgx_unique_device_id substringWithRange:NSMakeRange(0, 8)];
   return [NSURL URLWithString:[NSString stringWithFormat:@"https://xpress-api.zentri.com/platforms/%@/products/bgx13/versions", partid]];
}

- (NSURL *)firmwareURL:(NSString *)svers
{
  return [[self versionsURL] URLByAppendingPathComponent:svers];
}

- (NSString *)localVersionsFile
{
  return [[NSString stringWithFormat:@"~/Documents/%@/versions.json", [self.bgx_unique_device_id substringWithRange:NSMakeRange(0,8)] ] stringByExpandingTildeInPath];
}

+ (void)reportInstallationResultWithDeviceUUID:(NSString*)bgx_device_uuid version:(NSString*)version
{
  NSMutableURLRequest * mur = [[NSMutableURLRequest alloc] initWithURL: [NSURL URLWithString:
                                                                         [NSString stringWithFormat:@"https://bgx13.zentri.com/devices/%@", bgx_device_uuid]]
                                                           cachePolicy:NSURLRequestReloadIgnoringCacheData
                                                       timeoutInterval:kDMSServerTimeout];

  [mur setValue:[bgx_dms dms_api_key] forHTTPHeaderField:@"x-api-key"];
  [mur setValue:bgx_device_uuid forHTTPHeaderField:@"x-device-uuid"];
  [mur setValue:@"application/json" forHTTPHeaderField:@"content-type"];
  [mur setHTTPMethod:@"POST"];

  [mur setHTTPBody:[[NSString stringWithFormat: @"{\"bundle_id\": \"%@\"", version] dataUsingEncoding:NSUTF8StringEncoding]];

  NSURLSessionDownloadTask * task = [[NSURLSession sharedSession] downloadTaskWithRequest:mur completionHandler:^(NSURL * location, NSURLResponse * response, NSError * error){

    if (error) {
      NSLog(@"An error occurred while attempting to report an installation result: %@", [error description]);
      return;
    }

    NSHTTPURLResponse * httpResponse = SafeType(response, [NSHTTPURLResponse class]);
    if (httpResponse) {
      if (200 != httpResponse.statusCode) {

        NSLog(@"Warning: unexpected http response received when attempting to report installation result. Response: %@", [httpResponse description]);
      }
    }

  }];

  [task resume];

}

@end

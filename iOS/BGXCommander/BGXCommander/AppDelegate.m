/*
 * Copyright 2018-2020 Silicon Labs
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

#import "AppDelegate.h"
#import "DevicesTableViewController.h"
#import "PasswordEntryViewController.h"
#import "dispatch_utils.h"
#import "AboutBoxViewController.h"
#import "UpdateViewController.h"


const NSTimeInterval kScanInterval = 8.0f;

@interface AppDelegate()

@property (nonatomic) NSTimer * timer;

@end

@implementation AppDelegate

+ (AppDelegate *)sharedAppDelegate
{
  return SafeType([UIApplication sharedApplication].delegate, [AppDelegate class]);
}

- (BOOL)application:(UIApplication *)application willFinishLaunchingWithOptions:(NSDictionary *)launchOptions
{
  self.temporary_observer_reference = nil;
  self.bluetoothReady = NO;
  self.isScanning = NO;
  self.lastScan = [NSDate distantPast];
  self.bgxScanner = [[BGXpressScanner alloc] init];
  self.bgxScanner.delegate = self;


  UIStoryboard * drawerStoryboard = [UIStoryboard storyboardWithName:@"Drawer" bundle:nil];

  UIStoryboard * mainStoryboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];



  self.drawerController = [[MMDrawerController alloc] initWithCenterViewController:[mainStoryboard instantiateInitialViewController] rightDrawerViewController:[drawerStoryboard instantiateInitialViewController]];



  UINavigationController * centerNavControl = (UINavigationController *) self.drawerController.centerViewController;

  [centerNavControl.navigationBar setTitleTextAttributes:@{ NSForegroundColorAttributeName : [UIColor whiteColor], NSFontAttributeName : [UIFont fontWithName:@"OpenSans-Regular" size:18.0f] }];

  [centerNavControl.navigationBar setTintColor:[UIColor whiteColor]];


  id appearanceProxy = [[UIBarButtonItem class] appearanceWhenContainedInInstancesOfClasses:@[ [UINavigationBar class] ]];




  [appearanceProxy setTitleTextAttributes:@{ NSForegroundColorAttributeName : [UIColor whiteColor],
                             NSFontAttributeName : [UIFont fontWithName:@"OpenSans-Regular" size:14.0f] } forState:UIControlStateNormal];

  self.window = [[UIWindow alloc] initWithFrame: [UIScreen mainScreen].bounds];

  [self.window setRootViewController:self.drawerController];

  [self.drawerController setShowsShadow:YES];
  [self.drawerController setRestorationIdentifier:@"MMDrawer"];
  [self.drawerController setMaximumRightDrawerWidth:160.0];
  [self.drawerController setOpenDrawerGestureModeMask:MMOpenDrawerGestureModeAll];
  [self.drawerController setCloseDrawerGestureModeMask:MMCloseDrawerGestureModeAll];

  [self.drawerController
   setDrawerVisualStateBlock:


   ^(MMDrawerController * drawerController, MMDrawerSide drawerSide, CGFloat percentVisible){
     CGFloat parallaxFactor = 1.0f;
     CATransform3D transform = CATransform3DIdentity;
     UIViewController * sideDrawerViewController;
     if(drawerSide == MMDrawerSideLeft) {
       sideDrawerViewController = drawerController.leftDrawerViewController;
       CGFloat distance = MAX(drawerController.maximumLeftDrawerWidth,drawerController.visibleLeftDrawerWidth);
       if (percentVisible <= 1.f) {
         transform = CATransform3DMakeTranslation((-distance)/parallaxFactor+(distance*percentVisible/parallaxFactor), 0.0, 0.0);
       }
       else{
         transform = CATransform3DMakeScale(percentVisible, 1.f, 1.f);
         transform = CATransform3DTranslate(transform, drawerController.maximumLeftDrawerWidth*(percentVisible-1.f)/2, 0.f, 0.f);
       }
     }
     else if(drawerSide == MMDrawerSideRight){
       sideDrawerViewController = drawerController.rightDrawerViewController;
       CGFloat distance = MAX(drawerController.maximumRightDrawerWidth,drawerController.visibleRightDrawerWidth);
       if(percentVisible <= 1.f){
         transform = CATransform3DMakeTranslation((distance)/parallaxFactor-(distance*percentVisible)/parallaxFactor, 0.0, 0.0);
       }
       else{
         transform = CATransform3DMakeScale(percentVisible, 1.f, 1.f);
         transform = CATransform3DTranslate(transform, -drawerController.maximumRightDrawerWidth*(percentVisible-1.f)/2, 0.f, 0.f);
       }
     }

     [sideDrawerViewController.view.layer setTransform:transform];
   }];


  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(startTutorial:) name:StartTutorialNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(stopTutorial:) name:CancelTutorialNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserverForName:ConnectedToDeviceNotitficationName object:nil queue:nil usingBlock:^(NSNotification * n){

    if (self.tutorialWindow) {
      dispatch_async(dispatch_get_main_queue(), ^{
        [self.tutorialWindow makeKeyAndVisible];
      });
    }

  }];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(updateFirmware:) name:UpdateFirmwareNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(cleanupUpdateUIObserver:) name:CleanupUpdateUIObserverNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(aboutThisApp:) name:AboutItemNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(appOptions:) name:OptionsItemNotificationName object:nil];
    
  return YES;
}

- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
  // Override point for customization after application launch.


  [self.window makeKeyAndVisible];

  return YES;
}


- (void)applicationWillResignActive:(UIApplication *)application {
  // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
  // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
}


- (void)applicationDidEnterBackground:(UIApplication *)application {
  // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
  // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
}


- (void)applicationWillEnterForeground:(UIApplication *)application {
  // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
}


- (void)applicationDidBecomeActive:(UIApplication *)application {
  // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
}


- (void)applicationWillTerminate:(UIApplication *)application {
  // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
}

- (void)startTutorial:(id)context
{

  UINavigationController * centerNavControl = (UINavigationController *) self.drawerController.centerViewController;

  [centerNavControl popToRootViewControllerAnimated:YES];

  [self.drawerController closeDrawerAnimated:YES completion:nil];

  // create the tutorial.

  if (!self.tutorialWindow) {
    [[NSBundle mainBundle] loadNibNamed:@"TutorialWindow" owner:self options:nil];
  }


  __strong TutorialViewController * vc = self.tutorialViewController;
  if (!self.tutorialViewController) {
    vc = [[TutorialViewController alloc] initWithNibName:@"TutorialViewController" bundle:nil];
    self.tutorialViewController = vc;

  }

  [self.tutorialWindow setRootViewController: vc];

  [self.tutorialWindow makeKeyAndVisible];

  [self.tutorialViewController showStep];

  NSAssert(!self.tutorial_observer, @"observer already setup.");

  __weak AppDelegate * me = self;
  self.tutorial_observer = [[NSNotificationCenter defaultCenter] addObserverForName:TutorialStep3NotificationName object:nil queue:nil usingBlock:^(NSNotification * n){
    [me.tutorialWindow makeKeyAndVisible];
  }];


}

/**
  Closes the tutorial and unloads the objects associated with it.
 */
- (void)stopTutorial:(id)context
{
  [self.tutorialWindow resignKeyWindow];
  self.tutorialWindow = nil;
  self.spotlightView = nil;

  
  [[NSNotificationCenter defaultCenter] removeObserver:self.tutorial_observer name:TutorialStep3NotificationName object:nil];

  self.tutorial_observer = nil;
}

- (void)appOptions:(NSNotification *)n
{
    [self.drawerController closeDrawerAnimated:YES completion:nil];
    UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    UIViewController * vc = [storyboard instantiateViewControllerWithIdentifier:@"Options"];
    
    UIViewController * cvc = self.drawerController.centerViewController;
    
    [cvc presentViewController:vc animated:YES completion:^{}];

}

- (void)aboutThisApp:(NSNotification *)n
{
    [self.drawerController closeDrawerAnimated:YES completion:nil];

    UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    UIViewController * vc = [storyboard instantiateViewControllerWithIdentifier:@"AboutBGXCommander"];
    
    UIViewController * cvc = self.drawerController.centerViewController;
    
    [cvc presentViewController:vc animated:YES completion:^{}];
}



/** Called to begin scanning for devices.
 @Returns bool to indicate whether the scan call was successful.
 */
- (BOOL)scan
{
    BOOL fResult = NO;
    if (self.bluetoothReady) {
        if (!self.isScanning) {
            [self startScanningWithCompletion: ^{}];
            fResult = YES;
        }
        else {
            [self stopScanning];
        }
    }
    return fResult;
}

- (void)startScanningWithCompletion: (void (^)(void))completion {
    [[NSNotificationCenter defaultCenter] postNotificationName:DeviceListChangedNotificationName object: @[]];
    [self.bgxScanner startScan];
    self.isScanning = YES;
    self.lastScan = [NSDate date];
    self.timer = [NSTimer scheduledTimerWithTimeInterval:kScanInterval repeats:NO block: ^(NSTimer *timer){
        [self stopScanning];
        completion();
    }];
}

- (void)stopScanning {
    if(self.timer != nil && [self.timer isValid]) {
        [self.timer invalidate];
        [self.bgxScanner stopScan];
        self.isScanning = NO;
        self.timer = nil;
    }
}

#pragma mark BGXpressDelegate

- (void)deviceDiscovered:(BGXDevice *)device
{
  NSMutableArray * devices = [self.bgxScanner.devicesDiscovered mutableCopy];
   [devices sortUsingComparator:^(BGXDevice * d1, BGXDevice * d2){
    return [d2.rssi compare: d1.rssi];
  }];

  [[NSNotificationCenter defaultCenter] postNotificationName:DeviceListChangedNotificationName object: devices];
}

- (void)bluetoothStateChanged:(CBManagerState)state
{
  NSString * stateName = @"?";

  switch (state) {
    case CBManagerStateUnknown:
      stateName = @"CBManagerStateUnknown";
      self.bluetoothReady = NO;
      break;
    case CBManagerStateResetting:
      stateName = @"CBManagerStateResetting";
      self.bluetoothReady = NO;
      break;
    case CBManagerStateUnsupported:
      stateName = @"CBManagerStateUnsupported";
      self.bluetoothReady = NO;
      break;
    case CBManagerStateUnauthorized:
      stateName = @"CBManagerStateUnauthorized";
      self.bluetoothReady = NO;
      break;
    case CBManagerStatePoweredOff:
      stateName = @"CBManagerStatePoweredOff";
      self.bluetoothReady = NO;
      break;
    case CBManagerStatePoweredOn:
      stateName = @"CBManagerStatePoweredOn";
      self.bluetoothReady = YES;

      if (!self.isScanning) {
        dispatch_async(dispatch_get_main_queue(), ^{
          [self scan];
        });
      }

      break;
  }

  (void)(stateName);
//  NSLog(@"%@", stateName);
}

- (void)stateChangedForDevice:(BGXDevice *)device
{
    NSAssert(self.selectedDevice == device, @"Unexpected device notification.");
    NSString * cs = nil;
    [[NSNotificationCenter defaultCenter] postNotificationName:DeviceStateChangedNotificationName object: [NSNumber numberWithInt: (int)device.deviceState]];
    
    switch (device.deviceState) {
        case Disconnected:
            cs = @"DISCONNECTED";
            [[NSNotificationCenter defaultCenter] postNotificationName:DisableFirmwareUpdateNotificationName object:nil];
            self.selectedDevice.deviceDelegate = nil;
            self.selectedDevice.serialDelegate = nil;
            self.selectedDevice = nil;
            self.selectedDeviceDectorator = NoDecoration;
            break;
        case Connecting:
            cs = @"CONNECTING";
            break;
        case Interrogating:
            cs = @"INTERROGATING";
            break;
        case Connected:
            cs = @"CONNECTED";
            break;
        case Disconnecting:
            cs = @"DISCONNECTING";
            break;
    }
    NSLog(@"New DeviceState %@", cs);
    if (Connected == device.deviceState) {
        // you are now connected.
        [[NSNotificationCenter defaultCenter] postNotificationName:EnableFirmwareUpdateNotificationName object:nil];
        [[NSNotificationCenter defaultCenter] postNotificationName:ConnectedToDeviceNotitficationName object:device];
    }
}

- (void)dataRead:(NSData *) newData forDevice:(nonnull BGXDevice *)device
{
    // this is called when data is received.
    NSLog(@"%@", [[NSString alloc] initWithData:newData encoding:NSUTF8StringEncoding]);
    [[NSNotificationCenter defaultCenter] postNotificationName:DataReceivedNotificationName object:newData];
}

- (void)busModeChanged:(BusMode)newBusMode forDevice:(BGXDevice *)device {
  char * nameNewBusMode = "?";
  switch (newBusMode) {
    case UNKNOWN_MODE:
      nameNewBusMode = "UNKNOWN_MODE";
      break;
    case STREAM_MODE:
      nameNewBusMode = "STREAM_MODE";
      break;
    case LOCAL_COMMAND_MODE:
      nameNewBusMode = "LOCAL_COMMAND_MODE";
      break;
    case REMOTE_COMMAND_MODE:
      nameNewBusMode = "REMOTE_COMMAND_MODE";
      break;
    case UNSUPPORTED_MODE:
      nameNewBusMode = "UNSUPPORTED_MODE";
      break;
  }
  NSLog(@"NewBusMode: %s", nameNewBusMode);
    
  [[NSNotificationCenter defaultCenter] postNotificationName:BusModChangedNotificationName object:device];
}





- (void)dataWrittenForDevice:(BGXDevice *)device {
  NSLog(@"dataWritten for %@", [device description]);
}

#pragma mark - Firmware Update

- (void)updateFirmware:(NSNotification *)n
{
    NSString * device_unique_id = [self.selectedDevice device_unique_id];

    if (device_unique_id) {
        [self.drawerController closeDrawerAnimated:YES completion:nil];

        UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Update" bundle:nil];
        UIViewController * vc = [storyboard instantiateViewControllerWithIdentifier:@"UpdateController"];
        
        UpdateViewController * uvc = SafeType(vc, [UpdateViewController class]);
        
        [uvc setDeviceInfo: @{
            @"device" : self.selectedDevice,
            @"device_unique_id" : device_unique_id,
        } ];
        
        UIViewController * cvc = self.drawerController.centerViewController;
        
        [cvc presentViewController:vc animated:YES completion:^{}];
    } else {
        NSLog(@"Error: could not get unique id of selected device: %@", [self.selectedDevice description]);
    }
}

- (void)cleanupUpdateUIObserver:(NSNotification *)n
{
  if (self.temporary_observer_reference) {
    [[NSNotificationCenter defaultCenter] removeObserver:self.temporary_observer_reference];
    self.temporary_observer_reference = nil;
  }
}



- (void)askUserForPasswordFor:(password_kind_t)passwordKind
                    forDevice:(BGXDevice *)device
               ok_post_action:(dispatch_block_t)ok_post_block
           cancel_post_action:(dispatch_block_t) cancel_post_block
{
    
    
    
    executeBlockOnMainThread(^{
        UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
        
        PasswordEntryViewController * pevc = [storyboard instantiateViewControllerWithIdentifier:@"PasswordEntryViewController"];

        
        pevc.ok_post_action = ok_post_block;
        pevc.cancel_post_action = cancel_post_block;
        pevc.password_kind = passwordKind;
        pevc.device = device;
        
        [self.drawerController.centerViewController presentViewController:pevc animated:YES completion:^{
            NSLog(@"The pevc is done animating now.");
        }];
    });
    
    
    
}

@end



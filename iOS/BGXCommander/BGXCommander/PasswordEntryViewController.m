/*
 * Copyright 2019 Silicon Labs
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

#import "PasswordEntryViewController.h"
#import "AppDelegate.h"

#import <Security/Security.h>
#import <BGXpress/bgxpress.h>

@interface PasswordEntryViewController ()

@end

@implementation PasswordEntryViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view from its nib.
    self.box.layer.cornerRadius = 5;
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/


- (void)viewWillAppear:(BOOL)animated
{
    NSString * password = [PasswordEntryViewController passwordForType:self.password_kind forDevice: self.device];
    NSLog(@"Password Retrieved: %@",  password);
    
    if (!password) {
        password = @"";
    }
    
    self.passwordField.text = password;
    
    switch (_password_kind) {
        case unknown_password_kind:
            self.passwordInstructions.text = @"Enter the password.";
            break;
        case remoteConsolePasswordKind:
            self.passwordInstructions.text = [NSString stringWithFormat:@"A password is required for remote console on %@.", self.device.name];
            break;
        case OTAPasswordKind:
            self.passwordInstructions.text = [NSString stringWithFormat:@"A password is required to update the firmware on %@.", self.device.name];
            
            break;
        default:
            break;
    }
    
    [super viewWillAppear:animated];
}

+ (NSMutableDictionary *)searchDictionaryForPasswordKind:(password_kind_t)kind forDevice:(BGXDevice *)bgxDevice
{
    NSString *identifierName;
    
    NSString * bundleIdentifier = SafeType([[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleIdentifier"], [NSString class]);
    
    switch (kind) {
        case remoteConsolePasswordKind:
            identifierName = [NSString stringWithFormat:@"%@.remoteConsole", bundleIdentifier];
            break;
        case OTAPasswordKind:
            identifierName = [NSString stringWithFormat:@"%@.OTA", bundleIdentifier];
            break;
        case unknown_password_kind:
        case maxPasswordKind:
        default:
            [NSException raise:@"Invalid password kind" format:@"Invalid password kind: %d", (int)kind];
            break;
    }
    
    
    NSMutableDictionary *searchDictionary = [[NSMutableDictionary alloc] init];
    
    [searchDictionary setObject:(id)kSecClassGenericPassword forKey:(id)kSecClass];
    
    NSData *encodedIdentifier = [identifierName dataUsingEncoding:NSUTF8StringEncoding];
    [searchDictionary setObject:encodedIdentifier forKey:(id)kSecAttrGeneric];
    [searchDictionary setObject:encodedIdentifier forKey:(id)kSecAttrAccount];
    [searchDictionary setObject:bgxDevice.name forKey:(id)kSecAttrService];

    return searchDictionary;
}

+ (NSString * _Nullable)passwordForType:(password_kind_t)kind forDevice:(BGXDevice *)device
{
    NSString * thePassword = nil;
    
    NSMutableDictionary * searchDictionary = [PasswordEntryViewController searchDictionaryForPasswordKind:kind forDevice:device];

    // Add search attributes
    [searchDictionary setObject:(id)kSecMatchLimitOne forKey:(id)kSecMatchLimit];
    
    // Add search return types
    [searchDictionary setObject:(id)kCFBooleanTrue forKey:(id)kSecReturnData];
    

    CFTypeRef result = NULL;
    OSStatus status = SecItemCopyMatching((CFDictionaryRef)searchDictionary,
                                          &result);

    NSLog(@"status: %d", status);
    
    switch (status) {
        case errSecSuccess:
            printf("errSecSuccess");
            
            thePassword = [[NSString alloc] initWithData:(__bridge NSData *)result encoding:NSASCIIStringEncoding];
            
            break;
        case errSecItemNotFound:
            printf("errSecItemNotFound");
            break;
        default:
            NSLog(@"%ld", (long) status);
            break;
    }
    
    return thePassword;
}


+ (BOOL)savePassword:(NSString *)password ofKind:(password_kind_t)kind forDevice:(BGXDevice *)device
{
    NSMutableDictionary *dictionary = [PasswordEntryViewController searchDictionaryForPasswordKind:kind forDevice: device];
    NSData *passwordData = [password dataUsingEncoding:NSUTF8StringEncoding];
    [dictionary setObject:passwordData forKey:(id)kSecValueData];
    
    OSStatus status = SecItemAdd((CFDictionaryRef)dictionary, NULL);
    
    switch (status) {
        case errSecSuccess:
            printf("errSecSuccess");
            break;
        case errSecDuplicateItem:
            printf("errSecDuplicateItem");
            // if this error code happens, update the item instead of adding it.
            dictionary = [PasswordEntryViewController searchDictionaryForPasswordKind:kind forDevice: device];
            NSMutableDictionary * dictionary2 = [NSMutableDictionary dictionaryWithCapacity:1];
            [dictionary2 setObject:passwordData forKey:(id)kSecValueData];
            status = SecItemUpdate((CFDictionaryRef)dictionary,
                                   (CFDictionaryRef)dictionary2);
            break;
    }

    return errSecSuccess == status ? YES : NO;
}


- (IBAction)okAction:(id)sender
{
    NSString * password = [self.passwordField text];
    
    NSLog(@"Saving password: %@", password);
    
    BOOL fResult = [PasswordEntryViewController savePassword:password ofKind:self.password_kind forDevice: self.device];
    
    if (fResult) {
        NSLog(@"Saved the password");
    } else {
        NSLog(@"ERROR: Save password failed.");
    }
    
    [self dismissViewControllerAnimated:YES completion:^{ NSLog(@"done dismissing password dialog."); }];
    
    if (self.ok_post_action) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), self.ok_post_action);
    }
}

- (IBAction)cancelAction:(id)sender
{
    [self dismissViewControllerAnimated:YES completion:^{ NSLog(@"done dismissing password dialog."); }];

    if (self.cancel_post_action) {
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH, 0), self.cancel_post_action);
    }
}


- (BOOL)textFieldShouldReturn:(UITextField *)textField
{
    if (textField == self.passwordField) {
        [self okAction:self];
    }
    return NO;
}

@end

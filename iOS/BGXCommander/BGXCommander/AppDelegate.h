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

#import <UIKit/UIKit.h>
#import <BGXpress/bgxpress.h>

#import "MMDrawerController.h"
#import "SpotlightView.h"
#import "TutorialViewController.h"
#import "DecoratedMMDrawerBarButtonItem.h"
#import "PasswordEntryViewController.h"



@interface AppDelegate : UIResponder <UIApplicationDelegate, BGXpressScanDelegate, BGXDeviceDelegate, BGXSerialDelegate>

+ (AppDelegate *)sharedAppDelegate;

- (void)startTutorial:(id)context;
- (void)stopTutorial:(id)context;

@property (strong, nonatomic) UIWindow *window;
@property (nonatomic, strong) MMDrawerController * drawerController;
@property (nonatomic, strong) UINavigationController * leftDrawerNavController;

@property (strong, nonatomic) IBOutlet UIWindow * tutorialWindow;
@property (strong, nonatomic) IBOutlet SpotlightView * spotlightView;
@property (weak, nonatomic) TutorialViewController * tutorialViewController;
@property (strong, nonatomic) id tutorial_observer;

- (BOOL)scan;

@property (strong, nonatomic) BGXpressScanner * bgxScanner;
@property (strong, nonatomic) BGXDevice * selectedDevice;
@property (nonatomic) DecorationState selectedDeviceDectorator;

@property (nonatomic) BOOL bluetoothReady;
@property (nonatomic) BOOL isScanning;
@property (strong, nonatomic) NSDate * lastScan;

@property (nonatomic, strong) id temporary_observer_reference;



- (void)askUserForPasswordFor:(password_kind_t)passwordKind
                    forDevice:(BGXDevice *)device
               ok_post_action:(dispatch_block_t)ok_post_block
           cancel_post_action:(dispatch_block_t) cancel_post_block;

@end


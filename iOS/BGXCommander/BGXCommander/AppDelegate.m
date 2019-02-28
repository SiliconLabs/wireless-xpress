/*
 * Copyright 2018 Silicon Labs
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
#import "OTA_UI_Manager.h"
#import "DevicesTableViewController.h"

@interface AppDelegate ()

@property (nonatomic, strong) OTA_UI_Manager * update_ui_manager;

@end

const NSTimeInterval kScanInterval = 8.0f;

@implementation AppDelegate

+ (AppDelegate *)sharedAppDelegate
{
  return (AppDelegate *)[UIApplication sharedApplication].delegate;
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

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(closeUpdateUI:) name:UpdateCompleteNotificationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(aboutThisApp:) name:AboutItemNotificationName object:nil];

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

/** This is a notification handler that displays the about box.
    It is hooked up to an item in the drawer that the user can choose
    in order to see the app version.
 */
- (void)aboutThisApp:(NSNotification *)n
{
  [self.drawerController closeDrawerAnimated:YES completion:nil];

  [[NSBundle mainBundle] loadNibNamed:@"About" owner:self options:nil];

  self.versionLabel.text = [NSString stringWithFormat:@"%@ (%@)", [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleShortVersionString"], [[[NSBundle mainBundle] infoDictionary] objectForKey:@"CFBundleVersion"]];

  [self.aboutWindow makeKeyAndVisible];
}

/** This IBAction is called when the user presses the close button
    on the about box.
    Set the IBOutlet references from About.xib back to nil.
 */
- (IBAction)closeAboutBox:(id)sender
{
  [self.aboutWindow resignKeyWindow];
  self.aboutWindow = nil;
  self.versionLabel = nil;
}

/** Called to begin scanning for devices.
    @Returns bool to indicate whether the scan call was successful.
 */
- (BOOL)scan
{
  BOOL fResult = NO;
  if (self.bluetoothReady) {

    if (!self.isScanning) {
      [[NSNotificationCenter defaultCenter] postNotificationName:DeviceListChangedNotificationName object: @[]];
      [self.bgxScanner startScan];
      self.isScanning = YES;
      fResult = YES;
      self.lastScan = [NSDate date];
      dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(kScanInterval * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        [self.bgxScanner stopScan];
        self.isScanning = NO;
      });
    }
  }
  return fResult;
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
    [[NSNotificationCenter defaultCenter] postNotificationName:DeviceStateChangedNotificationName object: [NSNumber numberWithInt:device.deviceState]];
    
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
  NSLog(@"updateFirmware: %@", [n description]);
  [self openUpdateUI];
}

- (void)cleanupUpdateUIObserver:(NSNotification *)n
{
  if (self.temporary_observer_reference) {
    [[NSNotificationCenter defaultCenter] removeObserver:self.temporary_observer_reference];
    self.temporary_observer_reference = nil;
  }
}

- (void)openUpdateUI
{
  /*
    In this case, we are connected. If we weren't connected the user shouldn't
    have been able to start the update. We want to close the drawer, disconnect,
    and then wait until we are disconnected. Once we are disconnected we want to
    pop back to the root controller, open the OTA update UI and show it to the user.
    Then we want to tell it to begin the update.

    So to do that, we register for a ConnectionStateChangedNotificationName
    which means we have to clean up the registration once we get one which is
    why we have this temporary_observer_reference property which isn't the most
    elegant solution but seems to work OK. We will get a series of
    ConnectionStateChange notifications and we only care about the one that tells
    us we are disconnected.
   */
  NSAssert(Connected == self.selectedDevice.deviceState, @"Invalid state.");

  NSString * deviceUniqueID = [self.selectedDevice device_unique_id];

  [self.drawerController closeDrawerAnimated:YES completion:^(BOOL finished){

    self.temporary_observer_reference = [[NSNotificationCenter defaultCenter]
                                         addObserverForName:DeviceStateChangedNotificationName
                                         object:nil
                                         queue:nil
                                         usingBlock:^(NSNotification * n){

                                           NSNumber * num = [n object];
                                           DeviceState cs = (DeviceState) [num intValue];

                                           if (Disconnected == cs) {

                                             [[NSNotificationCenter defaultCenter] postNotificationName:CleanupUpdateUIObserverNotificationName object:nil];

                                             UINavigationController * centerNavControl = (UINavigationController *) self.drawerController.centerViewController;
                                             [centerNavControl popToRootViewControllerAnimated:YES];

                                             self.update_ui_manager = [[OTA_UI_Manager alloc] init];

                                             [[UINib nibWithNibName:@"OTA_UI" bundle:nil] instantiateWithOwner:self.update_ui_manager options:nil];

                                             [self.update_ui_manager showUpdateUI];

                                               BGXDevice * device2Update = [AppDelegate sharedAppDelegate].selectedDevice;
//                                             CBPeripheral * device2Update = [DevicesTableViewController devicesTableViewController].selectedPeripheral;

                                             @try {
                                             [self.update_ui_manager updateFirmwareForBGXDevice:device2Update withDeviceID:deviceUniqueID];
                                             } @catch(NSException * exception) {

                                               UIAlertController * alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"Error", @"user alert title") message:exception.reason preferredStyle:UIAlertControllerStyleAlert];

                                               [alertController addAction: [UIAlertAction actionWithTitle:@"Dismiss" style:UIAlertActionStyleCancel handler:^(UIAlertAction * action){

                                               }]];

                                               [self.update_ui_manager closeUpdateUI];

                                               [self.window.rootViewController presentViewController:alertController animated:YES completion:nil];

                                             }

                                           }
                                         }];


    [[AppDelegate sharedAppDelegate].selectedDevice disconnect];

  }];
}

- (void)closeUpdateUI:(NSNotification *)n
{
  [self.update_ui_manager closeUpdateUI];
  self.update_ui_manager = nil;
}



@end



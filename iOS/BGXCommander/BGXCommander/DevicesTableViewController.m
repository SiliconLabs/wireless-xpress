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

#import "DevicesTableViewController.h"
#import "MMDrawerBarButtonItem.h"
#import "DeviceTableViewCell.h"
#import "DeviceDetailsViewController.h"
#import "AppDelegate.h"
#import "ConnectingViewController.h"

DevicesTableViewController * gDevicesTableViewController = nil;

@interface DevicesTableViewController ()

@property (nonatomic, strong) id errorObserverReference;

@property (nonatomic, strong) ConnectingViewController * connectingViewController;

@end

@implementation DevicesTableViewController

+ (instancetype)devicesTableViewController
{
    return gDevicesTableViewController;
}

- (void)viewDidLoad {
    
    [super viewDidLoad];
    
    gDevicesTableViewController = self;
    
    self.textLoading = NSLocalizedString(@"Scanning for BGX Devices…", @"Describes scanning");
    self.textRelease = NSLocalizedString(@"Release to scan again…", @"Release to scan");
    self.textPull = NSLocalizedString(@"Pull to scan again…", @"Pull to scan");
    
#if TARGET_IPHONE_SIMULATOR
    // Create simulated devices.
    self.devices =   @[  @{ @"name" : @"BGX-SAMPLE1", @"UUID" : @"B159CD32-0FC5-4BEA-943C-114AE1920539", @"RSSI" : @-43 }
                         ,@{ @"name" : @"BGX-SAMPLE2", @"UUID" : @"4412F05E-A45B-462A-818B-0ACAFE319423", @"RSSI" : @-97 }];
    
#else
    self.devices = @[];
#endif
    
    
    MMDrawerBarButtonItem * mmDrawerBarButtonItem = [[MMDrawerBarButtonItem alloc] initWithTarget:self action:@selector(rightDrawerButtonPress:)];
    
    [mmDrawerBarButtonItem setTintColor:[UIColor whiteColor]];
    self.navigationItem.rightBarButtonItem = mmDrawerBarButtonItem;
    
    
    self.navigationItem.leftBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Scan" style:UIBarButtonItemStylePlain target:self action:@selector(scanAction:)];
    
    [self.navigationItem.leftBarButtonItem setTitleTextAttributes:@{ NSForegroundColorAttributeName : [UIColor lightGrayColor] } forState:UIControlStateDisabled];
    
    self.navigationItem.leftBarButtonItem.enabled = NO;
    
    self.navigationItem.backBarButtonItem = [[UIBarButtonItem alloc] initWithTitle:@"Disconnect" style:UIBarButtonItemStylePlain target:nil action:nil];
    
    
    [[AppDelegate sharedAppDelegate] addObserver:self forKeyPath:@"bluetoothReady" options:NSKeyValueObservingOptionNew context:nil];
    [[AppDelegate sharedAppDelegate] addObserver:self forKeyPath:@"isScanning" options:NSKeyValueObservingOptionNew context:nil];
    
#if ! TARGET_IPHONE_SIMULATOR
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(deviceListChanged:)
                                                 name:DeviceListChangedNotificationName
                                               object:nil];
    
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(connectedToDevice:)
                                                 name:ConnectedToDeviceNotitficationName
                                               object:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(deviceStateChanged:)
                                                 name:DeviceStateChangedNotificationName
                                               object:nil];
    
#endif
    [[NSNotificationCenter defaultCenter] addObserverForName:TutorialConnectToDeviceNotificationName object:nil queue:nil usingBlock:^(NSNotification * tutorialConnectToDeviceNotification){
        
        if (self.devices.count > 0) {
            
            [self.tableView selectRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:0] animated:YES scrollPosition:UITableViewScrollPositionNone];
            
            [self tableView:self.tableView didSelectRowAtIndexPath:[NSIndexPath indexPathForRow:0 inSection:0]];
        } else {
            // TO DO: Report that we can't select a device.
            NSLog(@"Report that we can't select a device.");
        }
    }];
    
}

- (void)viewWillAppear:(BOOL)animated
{
    [super viewWillAppear:animated];
    
    // Register for errors that may be sent from the bgx framework.
    self.errorObserverReference = [[NSNotificationCenter defaultCenter] addObserverForName:@"Error" object:nil queue:nil usingBlock:^(NSNotification * n){
        
        [[AppDelegate sharedAppDelegate].selectedDevice disconnect];
        [self.connectingViewController dismissViewControllerAnimated:YES completion:^{ self.connectingViewController = nil;}];

        NSError * error = SafeType( [n object], [NSError class]);
        
        UIAlertController * alertController;
        
        if (CBATTErrorDomain == error.domain && 1 == error.code) {
            NSIndexPath * ip = self.tableView.indexPathForSelectedRow;
            
            BGXDevice * device = SafeType([self.devices objectAtIndex: ip.row], [BGXDevice class]);
            
            alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"Error", @"user alert title") message:[NSString stringWithFormat: NSLocalizedString(@"The bonding information on this device is invalid (probably due to a firmware update). You should select %@ in the Bluetooth Settings and choose \"Forget this device\" and then turn Bluetooth off and back on.", @"Argument is device name"), device.name] preferredStyle:UIAlertControllerStyleAlert];

        } else {
        
            alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"Error", @"user alert title") message:[error localizedDescription] preferredStyle:UIAlertControllerStyleAlert];
        }
        
        [alertController addAction: [UIAlertAction actionWithTitle:@"Dismiss" style:UIAlertActionStyleCancel handler:^(UIAlertAction * action){
            
        }]];
        
        [self presentViewController:alertController animated:YES completion:nil];
        
    }];
}

- (void)viewWillDisappear:(BOOL)animated
{
    [super viewWillDisappear:animated];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self.errorObserverReference];
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context
{
    if ([keyPath isEqualToString:@"isScanning"]) {
        if ([AppDelegate sharedAppDelegate].isScanning) {
            [self startLoading];
        } else {
            [self stopLoading];
        }
    }
    
    self.navigationItem.leftBarButtonItem.enabled =  (! [AppDelegate sharedAppDelegate].isScanning) && ([AppDelegate sharedAppDelegate].bluetoothReady);
    
    if ([keyPath isEqualToString:@"bluetoothReady"]) {
        if ([AppDelegate sharedAppDelegate].bluetoothReady && ! [AppDelegate sharedAppDelegate].isScanning) {
            [[AppDelegate sharedAppDelegate] scan];
        } else if (! [AppDelegate sharedAppDelegate].bluetoothReady) {
            self.navigationItem.leftBarButtonItem.enabled = NO;
        }
    }
}

extern const NSTimeInterval kScanInterval; // defined in AppDelegate.m
- (void)refresh
{
    [self scanAction:nil];
}

- (IBAction)scanAction:(id)sender
{
    [[AppDelegate sharedAppDelegate] scan];
}

- (void)deviceListChanged:(NSNotification *)deviceListChangedNotification
{
    self.devices = [deviceListChangedNotification object];
    
    [self.tableView reloadData];
}

- (void)connectedToDevice:(NSNotification *)connectedToDeviceNotification
{
    [self performSegueWithIdentifier:@"pushDetails" sender:self];
    [self.connectingViewController dismissViewControllerAnimated:YES completion:^{self.connectingViewController = nil;}];
}

- (void)deviceStateChanged:(NSNotification *)deviceStateChangedNotification
{
    NSNumber * ds = SafeType([deviceStateChangedNotification object], [NSNumber class]);
    if (nil != ds) {
        if (Disconnected == [ds intValue]) {
            [self.connectingViewController dismissViewControllerAnimated:YES completion:^{self.connectingViewController = nil;}];
            

        }
    }
}

-(void)rightDrawerButtonPress:(id)sender{
    [[AppDelegate sharedAppDelegate].drawerController toggleDrawerSide:MMDrawerSideRight animated:YES completion:nil];
}

-(void)doubleTap:(UITapGestureRecognizer*)gesture{
    [[AppDelegate sharedAppDelegate].drawerController bouncePreviewForDrawerSide:MMDrawerSideLeft completion:nil];
}

-(void)twoFingerDoubleTap:(UITapGestureRecognizer*)gesture{
    
    [[AppDelegate sharedAppDelegate].drawerController bouncePreviewForDrawerSide:MMDrawerSideRight completion:nil];
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark - Table view data source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    
    NSInteger rows = self.devices.count;
    return rows;
}


- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    
    DeviceTableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"DeviceCell" forIndexPath:indexPath];
    
#if TARGET_IPHONE_SIMULATOR
    NSDictionary * idevice = SafeType([self.devices objectAtIndex:indexPath.row], [NSDictionary class]);
    NSString * name = [idevice objectForKey:@"name"];
    NSNumber * rssi = [idevice objectForKey:@"RSSI"];
#else
    BGXDevice * idevice = SafeType([self.devices objectAtIndex:indexPath.row], [BGXDevice class]);
    NSNumber * rssi = idevice.rssi;
    NSString * name = idevice.name;
#endif
    
    cell.deviceNameField.text = name;
    cell.deviceRSSIField.text = [NSString stringWithFormat:@"%d", [rssi intValue]];
    
    
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;

    return cell;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
  return 138.0f;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    self.connectingViewController = [storyboard instantiateViewControllerWithIdentifier:@"Connecting"];

#if TARGET_IPHONE_SIMULATOR
    [self.navigationController presentViewController:self.connectingViewController animated:YES completion:^{
        
        [[NSNotificationCenter defaultCenter] postNotificationName:ConnectedToDeviceNotitficationName object:SafeType([self.devices objectAtIndex: indexPath.row], [BGXDevice class])];
        [tableView deselectRowAtIndexPath:indexPath animated:YES];
        
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.5 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [self.connectingViewController dismissViewControllerAnimated:YES completion:^{

                [self performSegueWithIdentifier:@"pushDetails" sender:self];
            }];
        });
    }];
    
#else
    [AppDelegate sharedAppDelegate].selectedDevice = SafeType([self.devices objectAtIndex:indexPath.row], [BGXDevice class]);
    [AppDelegate sharedAppDelegate].selectedDevice.deviceDelegate = [AppDelegate sharedAppDelegate];
    [AppDelegate sharedAppDelegate].selectedDevice.serialDelegate = [AppDelegate sharedAppDelegate];
    [AppDelegate sharedAppDelegate].selectedDeviceDectorator = NoDecoration;
    
    [self.navigationController presentViewController:self.connectingViewController animated:YES completion:^{}];
    
    if (![[AppDelegate sharedAppDelegate].selectedDevice connect]) {
        [self dismissViewControllerAnimated:YES completion: ^{}];
    }
#endif
}

#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
    DeviceDetailsViewController * ddvc = [segue destinationViewController];
    
    BGXDevice * idevice = SafeType([self.devices objectAtIndex:self.tableView.indexPathForSelectedRow.row], [BGXDevice class]);
    ddvc.title = idevice.name;
}

@end

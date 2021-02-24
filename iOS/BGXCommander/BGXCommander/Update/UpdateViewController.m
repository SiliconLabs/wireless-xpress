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

#import "UpdateViewController.h"
#import "FirmwareReleaseNotesViewController.h"
#import "FirmwareVersionTableViewCell.h"
#import "AppDelegate.h"
#import "OTAViewController.h"

@interface UpdateViewController ()

@property (nonatomic, strong) NSArray * firmware_versions;

@property (nonatomic, strong) BGX_OTA_Updater * bgx_ota_updater;

@property (nonatomic, strong) Version * currentFirmwareVersion;
@property (atomic) NSInteger bootloaderVersion;

@property (nonatomic, strong) BGXDevice * deviceBeingUpdated;

@property (nonatomic, strong) IBOutlet UIImageView * firmwareDecoratorImageView;
@property (weak, nonatomic) IBOutlet UILabel *titleLabel;

@property (nonatomic) BOOL fSetup;
@property (nonatomic, strong) BGXDevice * device2Update;
@property (nonatomic, strong) NSString * device_unique_id;
@property (nonatomic, strong) NSString * bgx_part_id;
@end

enum {
  Release_Notes_Section=0,
  Firmware_Section =1,
};

@implementation UpdateViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.fSetup = NO;

    firmwareVersions.rowHeight = UITableViewAutomaticDimension;
    firmwareVersions.estimatedRowHeight = 72.0f;
}

- (void)viewWillAppear:(BOOL)animated
{
    if (!self.fSetup) {
        [self setup];
    }
    
    [super viewWillAppear:animated];
}

- (void)viewWillDisappear:(BOOL)animated
{
    [super viewWillDisappear:animated];
}

- (void)setup
{
    NSInteger myBootloaderVersion;
    
    [self->firmwareQuerySpinner startAnimating];
    
    NSAssert(self.device2Update, @"No device selected");
    NSAssert(self.device_unique_id, @"No device unique id");
    
    currentFirmwareVersionLabel.text = self.device2Update.firmwareRevision;
    
    self.currentFirmwareVersion = [Version versionFromString:self.device2Update.firmwareRevision];
    
    if ([[NSScanner scannerWithString:self.device2Update.bootloaderVersion] scanInteger:&myBootloaderVersion]) {
        self.bootloaderVersion = myBootloaderVersion;
    }
    self.titleLabel.text = [NSString stringWithFormat:@"Firmware Available for %@", self.device2Update.name];
    
    self.firmwareDecoratorImageView.image = nil;
    
    self.bgx_ota_updater = [[BGX_OTA_Updater alloc] initWithPeripheral: self.device2Update.peripheral bgx_device_uuid:self.device_unique_id];
    [self.bgx_ota_updater retrieveAvailableFirmwareVersions:^(NSError * err, NSArray * versions){
        [self retrieveFirmwareVersionFunctionWithError:err versions:versions];
    }];
    
    self.fSetup = YES;
}

- (void)tearDown
{
    self.fSetup = NO;
    self.device2Update = nil;
}

- (void)retrieveFirmwareVersionFunctionWithError:(NSError *)err versions:(NSArray *)versions
{
    if (err) {
        NSLog(@"Error retriving versions file: %@.", [err description]);
        return;
    }
    
    // sort versions
    self.firmware_versions = [versions sortedArrayUsingComparator:^(id obj1, id obj2){
        NSDictionary * d1 = SafeType(obj1, [NSDictionary class]);
        NSDictionary * d2 = SafeType(obj2, [NSDictionary class]);
        Version * v1 = [Version versionFromString: [d1 objectForKey:@"version"]];
        Version * v2 = [Version versionFromString: [d2 objectForKey:@"version"]];
        
        return [v2 compare:v1];
    }];
    
    dispatch_async(dispatch_get_main_queue(), ^{
        
        if (self.bootloaderVersion < kBootloaderSecurityUpdateVersion) {
            // show the security decorator.
            self.firmwareDecoratorImageView.image = [UIImage imageNamed:@"Security_Decoration"];
        } else {
            for (NSDictionary * iVerRec in  versions) {
                NSString * sversion = SafeType([iVerRec objectForKey:@"version"], [NSString class]);
                Version * iVersion = [Version versionFromString:sversion];
                if (NSOrderedAscending == [self.currentFirmwareVersion compare:iVersion]) {
                    self.firmwareDecoratorImageView.image = [UIImage imageNamed:@"Update_Decoration"];
                    break;
                }
            }
        }
        
        [self->firmwareVersions reloadData];
        [self->firmwareQuerySpinner stopAnimating];
    });
}

- (IBAction)installFirmwareAction:(id)sender
{
    UIStoryboard * sb = [UIStoryboard storyboardWithName:@"Update" bundle:nil];
    OTAViewController * otaVC = SafeType([sb instantiateViewControllerWithIdentifier:@"OTAViewController"], [OTAViewController class]);
    
    NSDictionary * versionToInstall = SafeType([self.firmware_versions objectAtIndex:firmwareVersions.indexPathForSelectedRow.row], [NSDictionary class]);
    
    Version * toVersion = [Version versionFromString: [versionToInstall objectForKey:@"version"] ];
    
    [otaVC prepareToUpdate:@{
                @"toVersion" : toVersion,
                @"fromVersion" : self.currentFirmwareVersion,
                @"device" : self.device2Update,
                @"device_unique_id" : self.device_unique_id,
                @"bgx_part_id" : self.bgx_part_id,
                
            }];
    
    [self presentViewController:otaVC animated:YES completion:^{
        
        [otaVC performUpdate:versionToInstall];
        
    }];
}

- (IBAction)cancelAction:(id)sender
{
    [self tearDown];
    
    [self.presentingViewController dismissViewControllerAnimated:YES completion:^{
        NSLog(@"canceled update");

        UINavigationController * navController = SafeType([AppDelegate sharedAppDelegate].drawerController.centerViewController, [UINavigationController class]);
        if (navController) {
            [navController popToRootViewControllerAnimated:YES];
        }
    }];
    
}

#pragma mark - Table View Methods

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
  return 2;
}

- (CGFloat)tableView:(UITableView *)tableView heightForHeaderInSection:(NSInteger)section
{
    switch (section) {
        case Firmware_Section:
            return 18.0f;
        case Release_Notes_Section:
            return 0;
            break;
    }
    return 0;
}

- (nullable NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section
{
    switch (section) {
        case Firmware_Section:
            return @"Available firmware";
            break;
        case Release_Notes_Section:
            return @"";
            break;
        default:
            return @"Other";
            break;
    }
    return @"?";
}


- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    switch(section) {
        case Firmware_Section:
            return self.firmware_versions ? self.firmware_versions.count : 0;
            break;
            
        case Release_Notes_Section:
            return 1;
            break;
        default:
            return 0;
            break;
    }
    return 0;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    UITableViewCell * cell = nil;
    
    if (Firmware_Section == indexPath.section) {
        FirmwareVersionTableViewCell * fwcell = SafeType([tableView dequeueReusableCellWithIdentifier:@"firmwareVersionCell"], [FirmwareVersionTableViewCell class]);
        
        if (!fwcell) {
            fwcell = [FirmwareVersionTableViewCell newFirmwareVersionTableCell];
        }
        
        
        
        NSDictionary * iDict;
        
        iDict = SafeType([self.firmware_versions objectAtIndex:indexPath.row], [NSDictionary class]);
        
        fwcell.firmwareVersion.text = SafeType([iDict objectForKey:@"version"], [NSString class]);
        fwcell.firmwareVersionDescription.text = SafeType([iDict objectForKey:@"description"], [NSString class]);
        
        NSNumber * numSize = SafeType([iDict objectForKey:@"size"], [NSNumber class]);
        
        fwcell.firmwareFileSize.text = [NSString stringWithFormat:@"%d bytes", (int) numSize.integerValue];
        cell = fwcell;
        
    } else if (Release_Notes_Section == indexPath.section) {
        
        cell = SafeType([tableView dequeueReusableCellWithIdentifier:@"firmwareReleaseNotesCell"], [UITableViewCell class]);
        if (!cell) {
            cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:@"firmwareReleaseNotesCell"];
        }
        
        cell.textLabel.text = NSLocalizedString(@"Show Firmware Release Notes", @"Label");
        
    } else {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleDefault reuseIdentifier:@"blank"];
    }


  return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    installFirmwareButton.enabled = (Firmware_Section == tableView.indexPathForSelectedRow.section) ? YES : NO;
    
    if (Release_Notes_Section == tableView.indexPathForSelectedRow.section) {

        UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Update" bundle:nil];
        FirmwareReleaseNotesViewController * vc = SafeType([storyboard instantiateViewControllerWithIdentifier:@"FirmwareReleaseNotesViewController"], [FirmwareReleaseNotesViewController class]);
        
        [self presentViewController:vc animated:YES completion:^{}];
        
        [tableView deselectRowAtIndexPath:indexPath animated:YES];
    }
}

- (void)setDeviceInfo:(NSDictionary *)deviceInfoDict
{
    self.device_unique_id = SafeType([deviceInfoDict objectForKey:@"device_unique_id"], [NSString class]);
    self.device2Update = SafeType([deviceInfoDict objectForKey:@"device"], [BGXDevice class]);
    self.bgx_part_id = [self.device_unique_id substringWithRange:NSMakeRange(0, 8)];
}

@end

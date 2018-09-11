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


#import "OTA_UI_Manager.h"
#import "AppDelegate.h"
#import "FirmwareVersionTableViewCell.h"

typedef NS_ENUM(NSUInteger, FirmwareType) {
  ReleaseFirmwareType,
  AllFirmwareType
};

@interface OTA_UI_Manager()

@property (nonatomic, strong) BGX_OTA_Updater * bgx_ota_updater;
@property (nonatomic, strong) NSArray * dms_firmware_versions;
@property (nonatomic, strong) NSArray * release_dms_firmware_versions;

@property (nonatomic, strong) bgx_dms * bgx_dms_manager;

@property (nonatomic, strong) NSString * bgx_part_id;
@end

enum {
  DMS_Section =0,

};

@implementation OTA_UI_Manager


- (id)init
{
  self = [super init];
  if (self) {
    waitingForReachable = NO;
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(reachabilityChanged:) name:DMSServerReachabilityChangedNotificationName object:nil];
  }
  return self;
}

- (void)dealloc
{
  [[NSNotificationCenter defaultCenter] removeObserver:self];
}

- (IBAction)cancelAction:(id)sender
{
  NSLog(@"Cancel action");

  [_ota_update_window setHidden:YES];
}

- (void)showUpdateUI
{
  [_ota_update_window makeKeyAndVisible];
}

- (void)closeUpdateUI
{
  [_ota_update_window setHidden:YES];
  [_ota_update_window resignKeyWindow];
}

- (IBAction)firmwareTypeAction:(id)sender
{
  [firmwareVersions reloadData];

  installFirmwareButton.enabled = firmwareVersions.indexPathForSelectedRow ? YES : NO;
}

- (void)reachabilityChanged:(NSNotification *)notification
{
  if (waitingForReachable) {
    NSNumber * reachable = SafeType([notification object], [NSNumber class]);

    if ([reachable boolValue]) {
      waitingForReachable = NO;
      [self.bgx_dms_manager retrieveAvailableVersions:^(NSError * err, NSArray * versions){
        if (err) {
          NSLog(@"Error retriving DMS versions: %@.", [err description]);
          return;
        }

        NSMutableArray * ma = [NSMutableArray arrayWithCapacity:versions.count];
        for (NSDictionary * irec in versions ) {
          NSString * stag = SafeType([irec objectForKey:@"tag"], [NSString class]);
          if ([stag isEqualToString:@"release"]) {

            [ma addObject:irec];
          }
        }
        self.release_dms_firmware_versions = [ma copy];
        self.dms_firmware_versions = versions;

        dispatch_async(dispatch_get_main_queue(), ^{
            [self->firmwareVersions reloadData];

            [self->firmwareQuerySpinner stopAnimating];
        });

      }];
    }
  }
}

- (void)updateFirmwareForBGXDevice:(CBPeripheral *)peripheral2Update withDeviceID:(NSString *)bgx_unique_device_id
{
  waitingForReachable = YES;
  self.bgx_part_id = [bgx_dms bgxPartInfoForDeviceID:bgx_unique_device_id];
  self.bgx_ota_updater = [[BGX_OTA_Updater alloc] initWithPeripheral:peripheral2Update bgx_device_uuid:bgx_unique_device_id];
  [self setupUpdaterObservation];

  self.bgx_dms_manager = [[bgx_dms alloc] initWithBGXUniqueDeviceID:bgx_unique_device_id];
}

- (void)setupUpdaterObservation
{
  for (NSString * keyPath in @[@"operationInProgress", @"ota_step", @"upload_progress"]) {
    [self.bgx_ota_updater addObserver:self forKeyPath:keyPath options:0 context:nil];
  }
}

- (void)tearDownUpdaterObservation
{
  for (NSString * keyPath in @[@"operationInProgress", @"ota_step", @"upload_progress"]) {
    [self.bgx_ota_updater removeObserver:self forKeyPath:keyPath];
  }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context
{
  dispatch_async(dispatch_get_main_queue(), ^{
    
    if ([keyPath isEqualToString:@"ota_step"]) {
      
      switch (self.bgx_ota_updater.ota_step) {
        case ota_step_no_ota:
              [self->determined_progress setHidden: YES];
              [self->spinner setHidden:YES];
              self->updateLabel.text = @"";
          break;
        case ota_step_init:
          [self->determined_progress setHidden: YES];
          [self->spinner setHidden:NO];
          self->updateLabel.text = @"Starting update…";
          break;
        case ota_step_scan:
          self->updateLabel.text = @"Scanning…";
          break;
        case ota_step_connect:
          self->updateLabel.text = @"Connecting…";
          break;
        case ota_step_find_services:
          self->updateLabel.text = @"Connected.";
          break;
        case ota_step_find_characteristics:
          break;
        case ota_step_upload_no_response:
        case ota_step_upload_with_response:
          self->updateLabel.text = @"Updating firmware…";
          [self->determined_progress setHidden: NO];
          [self->spinner setHidden:YES];
          break;
        case ota_step_upload_finish:
          self->updateLabel.text = @"Finishing…";
          [self->determined_progress setHidden: YES];
          [self->spinner setHidden:NO];
          break;
        case ota_step_end:
          self->updateLabel.text = @"Finished.";
          [self->spinner setHidden:YES];

          dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [[NSNotificationCenter defaultCenter] postNotificationName:UpdateCompleteNotificationName object:nil];
          });

          break;
        case ota_step_error:
          self->updateLabel.text = @"Update failed.";
          [self->spinner setHidden:YES];
          [self->determined_progress setHidden:YES];
          [self->cancelButton2 setTitle:@"Close" forState:UIControlStateNormal];
          break;
        default:
          break;
      }
      
    } else if ([keyPath isEqualToString:@"operationInProgress"]) {
      
      switch (self.bgx_ota_updater.operationInProgress) {
        case ota_firmware_update_in_progress:
          self->updateWindowLabel.text = NSLocalizedString(@"Updating firmware", @"Update Window Label Value");
          break;
        case ota_no_operation_in_progress:
          self->updateWindowLabel.text = NSLocalizedString(@"Idle", @"Update Window Label Value");
          break;
        case ota_firmware_update_complete:
          self->updateWindowLabel.text = NSLocalizedString(@"Firmware Update Complete", @"Update Window Label Value");
          break;
        default:
          break;
      }
      
    } else if ([keyPath isEqualToString:@"upload_progress"]) {
      
      if (self.bgx_ota_updater.upload_progress >= 0.0f) {
        [self->determined_progress setProgress:self.bgx_ota_updater.upload_progress animated:YES];
      }
    }
  });
}

#pragma mark - Table View Methods

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
  return 1;
}

- (nullable NSString *)tableView:(UITableView *)tableView titleForHeaderInSection:(NSInteger)section
{
  switch (section) {
    case DMS_Section:
      return @"Available firmware";
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
    case DMS_Section:
      switch(firmwareType.selectedSegmentIndex) {
        case ReleaseFirmwareType:
          return self.release_dms_firmware_versions ? self.release_dms_firmware_versions.count : 0;
          break;
        case AllFirmwareType:
          return self.dms_firmware_versions ? self.dms_firmware_versions.count : 0;
          break;
      }

      break;

    default:
      return 0;
      break;
  }
  return 0;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
  FirmwareVersionTableViewCell * cell = SafeType([tableView dequeueReusableCellWithIdentifier:@"firmwareVersionCell"], [FirmwareVersionTableViewCell class]);

  if (!cell) {
    cell = [FirmwareVersionTableViewCell newFirmwareVersionTableCell];
  }

  if (DMS_Section == indexPath.section) {

    NSDictionary * iDict;

    switch(firmwareType.selectedSegmentIndex) {
      case ReleaseFirmwareType:
        iDict = SafeType([self.release_dms_firmware_versions objectAtIndex:indexPath.row], [NSDictionary class]);
        break;
      case AllFirmwareType:
        iDict = SafeType([self.dms_firmware_versions objectAtIndex:indexPath.row], [NSDictionary class]);
        break;
    }

    cell.firmwareVersion.text = SafeType([iDict objectForKey:@"version"], [NSString class]);
    cell.firmwareVersionTag.text = SafeType([iDict objectForKey:@"tag"], [NSString class]);

    NSNumber * numSize = SafeType([iDict objectForKey:@"size"], [NSNumber class]);

    cell.firmwareFileSize.text = [NSString stringWithFormat:@"%d bytes", (int) numSize.integerValue];

  } else {
    // invalid section.
    cell.firmwareVersion.text = @"Invalid version - error";
    cell.firmwareVersionTag.text = @"Invalid section/row";
  }


  return cell;
}

- (IBAction)installFirmwareAction:(id)sender
{
  // find the version of firmware the user has selected
  // check to see if it needs to be downloaded.
  // if yes, download it.
  // then install it.

  firmwareVersions.hidden = YES;
  installFirmwareButton.hidden = YES;
  cancelButton.hidden = YES;

  [determined_progress setHidden: YES];
  firmwareType.hidden = YES;
  
  [spinner setHidden:NO];
  updateWindowLabel.text = @"Firmware Update";

  NSIndexPath * indexPath = firmwareVersions.indexPathForSelectedRow;
  NSDictionary * iDict = nil;
  NSString * local_file = nil;
  NSString * version = nil;

  switch (indexPath.section) {
    case DMS_Section:
    {

      switch (firmwareType.selectedSegmentIndex) {
        case ReleaseFirmwareType:
          iDict = SafeType([self.release_dms_firmware_versions objectAtIndex:indexPath.row], [NSDictionary class]);
          break;
        case AllFirmwareType:
          iDict = SafeType([self.dms_firmware_versions objectAtIndex:indexPath.row], [NSDictionary class]);
          break;
      }

      NSString * sversion = SafeType([iDict objectForKey:@"version"], [NSString class]);
      local_file = [[NSString stringWithFormat:@"~/Documents/%@/%@.gbl", self.bgx_part_id, sversion] stringByExpandingTildeInPath];
    }
      break;
    default:
      break;
  }

  if (iDict) {
    NSString * tag = SafeType([iDict objectForKey:@"tag"], [NSString class]);
    if ([tag isEqualToString:@"release"]) {
      version = @"release";
    } else {
      version = SafeType([iDict objectForKey:@"version"], [NSString class]);
    }
  }

  if (local_file && ![[NSFileManager defaultManager] fileExistsAtPath:local_file]) {
    // download version.
    updateLabel.text = @"Downloading firmware update...";

    NSString * sversion = SafeType([iDict objectForKey:@"version"], [NSString class]);
    [_bgx_dms_manager loadFirmwareVersion:sversion completion:^(NSError * err, NSString * firmwarePath){
      if (err) {
        NSLog(@"Error downloading version: %@", [err description]);
        return;
      }
      NSLog(@"Firmware is downloaded to path %@", firmwarePath);

      NSError * myError = nil;

      // create folder if needed.

      NSString * partidDirectory = [[NSString stringWithFormat:@"~/Documents/%@/", self.bgx_part_id] stringByExpandingTildeInPath];

      if (![[NSFileManager defaultManager] createDirectoryAtPath:partidDirectory withIntermediateDirectories:YES attributes:nil error:&myError]) {
        if (myError) {
          NSLog(@"An error occured trying to create the directory: %@. The error is %@.", partidDirectory, [myError description]);
        }
      }

      [[NSFileManager defaultManager] moveItemAtPath:firmwarePath toPath:local_file error:&myError];

      NSAssert1(nil == myError, @"Error moving firmware file: %@.", [myError description]);

      [self.bgx_ota_updater updateFirmwareWithImageAtPath: local_file withVersion: version ];
    }];
  } else {
    [self.bgx_ota_updater updateFirmwareWithImageAtPath: local_file withVersion: version];
  }

}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
  installFirmwareButton.enabled = tableView.indexPathForSelectedRow ? YES : NO;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath
{
  return [FirmwareVersionTableViewCell cellHeight];
}

@end


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

#import "OTAViewController.h"
#import "OptionsViewController.h"
#import "AppDelegate.h"
#import "UpdateViewController.h"
#import "dispatch_utils.h"

@interface OTAViewController ()

@property (nonatomic, strong) BGX_OTA_Updater * bgx_ota_updater;
@property (nonatomic, strong) NSDictionary * infoRecord;
@property (nonatomic, strong) NSDictionary * dmsVersionRecord;
@property (nonatomic, strong) NSString * bgx_part_id;
@property (nonatomic, strong) bgx_dms * bgx_dms_manager;
@property (nonatomic, strong) BGXDevice * deviceBeingUpdated;

@end

@implementation OTAViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
}

- (void)viewWillAppear:(BOOL)animated
{
    Version * vtoVersion = SafeType([self.infoRecord objectForKey:@"toVersion"], [Version class]);
    Version * vfromVersion = SafeType([self.infoRecord objectForKey:@"fromVersion"], [Version class]);
    
    [toVersion setText: [NSString stringWithFormat:@"%d.%d.%d.%d", vtoVersion.major, vtoVersion.minor, vtoVersion.build, vtoVersion.revision]];
    
    [fromVersion setText: [NSString stringWithFormat:@"%d.%d.%d.%d", vfromVersion.major, vfromVersion.minor, vfromVersion.build, vfromVersion.revision]];

    [super viewWillAppear:animated];
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

- (IBAction)cancelAction:(id)sender
{
    // cancel the update unless it is already finished.
    if ( !(ota_step_error == self.bgx_ota_updater.ota_step ||
           ota_step_end == self.bgx_ota_updater.ota_step ||
           ota_step_upload_finish == self.bgx_ota_updater.ota_step) ) {
    
        [self.bgx_ota_updater cancelUpdate];
        
        
        
    } else if (ota_step_upload_finish != self.bgx_ota_updater.ota_step) {
    
        UpdateViewController * myParentViewController = SafeType(self.presentingViewController, [UpdateViewController class]);
        
        [self.presentingViewController dismissViewControllerAnimated:YES completion:^{
            
            executeBlockOnMainThread(^{
                [myParentViewController cancelAction:self];
            });
            
        }];
    }
}

- (void)prepareToUpdate:(NSDictionary *)infoDict
{
    NSLog(@"%@", [infoDict description]);
    
    self.infoRecord = infoDict;
    
    self.deviceBeingUpdated = [self.infoRecord objectForKey:@"device"];
    
    NSString * bgx_unique_device_id = [self.infoRecord objectForKey:@"device_unique_id"];
    self.bgx_part_id = [self.infoRecord objectForKey:@"bgx_part_id"];
    
    self.bgx_ota_updater = [[BGX_OTA_Updater alloc] initWithPeripheral: self.deviceBeingUpdated.peripheral bgx_device_uuid:bgx_unique_device_id];
    
    [self.bgx_ota_updater setOTAUploadWithResponse: [[NSUserDefaults standardUserDefaults] boolForKey:(NSString *)kAckdWritesForOTA]];
    
    self.bgx_ota_updater.delegate = self;
    [self setupUpdaterObservation];
    
    self.bgx_dms_manager = [[bgx_dms alloc] initWithBGXUniqueDeviceID:bgx_unique_device_id
                                                          forPlatform:self.deviceBeingUpdated.platformIdentifier];
}

- (void)performUpdate:(NSDictionary *)dmsVersionDict
{
    NSLog(@"Start the update: %@", [dmsVersionDict description]);
    self.dmsVersionRecord = dmsVersionDict;
    [spinner setHidden:YES];

    NSString * version = SafeType([self.dmsVersionRecord objectForKey:@"version"], [NSString class]);
    
    NSString * local_file = [[NSString stringWithFormat:@"~/Documents/%@/%@.gbl", self.bgx_part_id, version] stringByExpandingTildeInPath];
    
    if (![[NSFileManager defaultManager] fileExistsAtPath:local_file]) {
        // download version.
        updateLabel.text = @"Downloading firmware update...";

        
        
        [_bgx_dms_manager loadFirmwareVersion:version completion:^(NSError * err, NSString * firmwarePath){
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
        [self.bgx_ota_updater updateFirmwareWithImageAtPath: local_file withVersion: version ];
    }
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
          self->updateLabel.text = @"Transferring firmware…";
          [self->determined_progress setHidden: NO];
          [self->spinner setHidden:YES];
          break;
        case ota_step_upload_finish:
          self->updateLabel.text = @"Updating firmware - Do not disconnect!";
          [self->determined_progress setHidden: YES];
          [self->spinner setHidden:NO];
          [self->spinner startAnimating];
          [self->cancelButton setEnabled:NO];
          break;
        case ota_step_end:
          self->updateLabel.text = @"Finished.";
          [self->spinner stopAnimating];
          [self->spinner setHidden:YES];
          [self->cancelButton setTitle:@"Close" forState:UIControlStateNormal];
          [self->cancelButton setEnabled:YES];

          {
              UIAlertController * alertController = [UIAlertController alertControllerWithTitle:@"Important" message:[NSString stringWithFormat: @"You should select %@ in the Bluetooth Settings on any paired devices and choose \"Forget\" and then turn Bluetooth off and back on again for correct operation.", self.deviceBeingUpdated.name ] preferredStyle:UIAlertControllerStyleAlert];
              
              [alertController addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction * action){} ] ];
              
              [self presentViewController:alertController animated:YES completion:^{}];
              
          }
              
          break;
        case ota_step_error:
          self->updateLabel.text = @"Update failed.";
          [self->spinner setHidden:YES];
          [self->determined_progress setHidden:YES];
          [self->cancelButton setTitle:@"Close" forState:UIControlStateNormal];
          [self->cancelButton setEnabled:YES];
          break;
        case ota_step_canceled:
          [self dismissViewControllerAnimated:YES completion:^{}];
          break;
        default:
          break;
      }
      
    } else if ([keyPath isEqualToString:@"operationInProgress"]) {
      
      switch (self.bgx_ota_updater.operationInProgress) {
        case ota_firmware_update_in_progress:
          self->updateLabel.text = NSLocalizedString(@"Updating firmware", @"Update Window Label Value");
          break;
        case ota_no_operation_in_progress:
          self->updateLabel.text = NSLocalizedString(@"Idle", @"Update Window Label Value");
          break;
        case ota_firmware_update_complete:
          self->updateLabel.text = NSLocalizedString(@"Firmware Update Complete", @"Update Window Label Value");
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

- (void)ota_requires_password:(NSError *)err {
    NSLog(@"ota_requires_password:");
    
    UIStoryboard * storyboard = [UIStoryboard storyboardWithName:@"Main" bundle:nil];
    
    PasswordEntryViewController * pevc = [storyboard instantiateViewControllerWithIdentifier:@"PasswordEntryViewController"];

    
    pevc.ok_post_action = ^{
        NSString * thePassword = [PasswordEntryViewController passwordForType: OTAPasswordKind forDevice:self.deviceBeingUpdated];

        [self.bgx_ota_updater continueOTAWithPassword:thePassword];
    };
    
    pevc.cancel_post_action = ^{
    
        [self.bgx_ota_updater cancelUpdate];
    };
    
    pevc.password_kind = OTAPasswordKind;
    pevc.device = self.deviceBeingUpdated;
    
    [self presentViewController:pevc animated:YES completion:^{}];

}



@end

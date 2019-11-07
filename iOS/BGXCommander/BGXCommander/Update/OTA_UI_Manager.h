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


#import <Foundation/Foundation.h>
#import <BGXpress/bgxpress.h>



@interface OTA_UI_Manager : NSObject <UITableViewDataSource, UITableViewDelegate, BGX_OTA_Updater_Delegate> {
    
    IBOutlet UIWindow * _ota_update_window;
    IBOutlet UILabel * updateWindowLabel;
    
    IBOutlet UIActivityIndicatorView * spinner;
    IBOutlet UIProgressView * determined_progress;
    
    IBOutlet UILabel * updateLabel;
    
    IBOutlet UITableView * firmwareVersions;
    
    IBOutlet UILabel * currentFirmwareVersionLabel;
    UIImageView * _firmwareDecoratorImageView;
    Version * _currentFirmwareVersion;
    
    BOOL waitingForReachable;
    
    IBOutlet UIButton * installFirmwareButton; // so we can enable/disable as selection changes
    IBOutlet UIButton * cancelButton; // so we can hide it when the download starts.
    IBOutlet UIButton * cancelButton2; // the cancel button visible during update
    
    IBOutlet UIActivityIndicatorView * firmwareQuerySpinner; // show a spinner while pulling the firmware list from server.

    UIWindow * firmwareReleaseNotesWindow;
    
}

- (IBAction)installFirmwareAction:(id)sender;

- (void)showUpdateUI;
- (void)closeUpdateUI;

- (IBAction)cancelAction:(id)sender;

- (void)updateFirmwareForBGXDevice:(BGXDevice *)peripheral2Update withDeviceID:(NSString *)bgx_unique_device_id;

// required delegate method of BGX_OTA_Updater_Delegate
- (void)ota_requires_password:(NSError *)err;

@property (nonatomic, strong) UIWindow * ota_update_window;

@property (nonatomic, strong) IBOutlet UIImageView * firmwareDecoratorImageView;

@end

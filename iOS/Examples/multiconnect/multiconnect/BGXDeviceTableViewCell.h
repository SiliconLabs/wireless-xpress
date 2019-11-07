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
#import <bgxpress/BGXDevice.h>
#import "ColorDot.h"

NS_ASSUME_NONNULL_BEGIN



@interface BGXDeviceTableViewCell : UITableViewCell 

- (IBAction)connectAction:(id)sender;
- (IBAction)disconnectAction:(id)sender;

- (void)setBGXDevice:(BGXDevice *)bgxDevice;

@property (nonatomic, strong) BGXDevice * device;

@property (nonatomic, strong) IBOutlet UILabel * device_name;
@property (nonatomic, strong) IBOutlet UILabel * device_uuid;
@property (nonatomic, strong) IBOutlet UILabel * rssi_label;

@property (nonatomic, strong) IBOutlet UIButton * connectButton;
@property (nonatomic, strong) IBOutlet UIButton * disconnectButton;


@property (nonatomic, weak) IBOutlet ColorDot * dot1;
@property (nonatomic, weak) IBOutlet ColorDot * dot2;
@property (nonatomic, weak) IBOutlet ColorDot * dot3;

@property (nonatomic, weak) IBOutlet UIButton * testButton;

@end

NS_ASSUME_NONNULL_END

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
#import <bgxpress/bgxpress.h>

NS_ASSUME_NONNULL_BEGIN

@interface DeviceDetailsViewController : UIViewController <BGXDeviceDelegate, BGXSerialDelegate, UITextFieldDelegate>


- (IBAction)userSelectedBusMode:(id)sender;
- (IBAction)clearAction:(id)sender;
- (IBAction)sendAction:(id)sender;

@property (nonatomic) BusMode busMode;

@property (nonatomic, weak) IBOutlet UITextField * sendTextField;

@property (nonatomic, weak) IBOutlet UITextView * textView;

@property (nonatomic, weak) IBOutlet UISegmentedControl * busModeSelector;

@property (nonatomic, strong) BGXDevice * device;

@end

NS_ASSUME_NONNULL_END

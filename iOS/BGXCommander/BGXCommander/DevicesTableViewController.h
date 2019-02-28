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

#import <UIKit/UIKit.h>
#import "MMDrawerController.h"
#import "PullRefreshTableViewController.h"

@interface DevicesTableViewController : PullRefreshTableViewController

//@property (strong, nonatomic) CBPeripheral * selectedPeripheral;

+ (instancetype)devicesTableViewController;

@property (nonatomic, strong) NSArray * devices;

#pragma mark Items relevant to Connecting.xib

- (void)showConnectingWindow;
- (void)exitConnectingWindow;

- (IBAction)cancelConnectingAction:(id)sender;

@property (strong, nonatomic) IBOutlet UIWindow * connectingWindow;


@end

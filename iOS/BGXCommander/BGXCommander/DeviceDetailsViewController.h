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

#import <UIKit/UIKit.h>

#import <bgxpress/bgxpress.h>

/**
 The purpose of this controller class is to show
 how to interact with a connected BGX Device using the Stream
 and Command modes.
 */

typedef enum {

  None
  ,CR
  ,LF
  ,CRLF
  ,LFCR

  ,Invalid

} LineEndings;

@interface DeviceDetailsViewController : UIViewController <UITextFieldDelegate>

+ (instancetype)deviceDetailsViewController;

- (IBAction)sendAction:(id)sender;

- (IBAction)userSelectedBusMode:(id)sender;

- (IBAction)clearAction:(id)sender;

@property (nonatomic, weak) IBOutlet UITextField * sendTextField;

@property (nonatomic, weak) IBOutlet UITextView * textView;

@property (nonatomic) BusMode busMode;

@property (nonatomic) LineEndings lineEndings;

@property (nonatomic, weak) IBOutlet UISegmentedControl * busModeSelector;


@end

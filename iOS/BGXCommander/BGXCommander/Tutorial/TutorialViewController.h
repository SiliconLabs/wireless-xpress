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

@class StepView;

@interface TutorialViewController : UIViewController

- (void)showStep;

- (IBAction)closeAction:(id)sender;

- (IBAction)nextAction:(id)sender;
- (IBAction)backAction:(id)sender;

- (IBAction)tutorialConnectToDeviceAction:(id)sender;

@property (nonatomic, strong) IBOutlet StepView * step1View;
@property (nonatomic, strong) IBOutlet StepView * step2View;
@property (nonatomic, strong) IBOutlet StepView * step3View; // waiting to connect
@property (nonatomic, strong) IBOutlet StepView * step4View;
@property (nonatomic, strong) IBOutlet StepView * step5View;
@property (nonatomic, strong) IBOutlet StepView * step6View;
@property (nonatomic, strong) IBOutlet StepView * step7View;
@property (nonatomic, strong) IBOutlet StepView * step8View;
@property (nonatomic, strong) IBOutlet StepView * step9View;

@property (nonatomic, readonly) NSInteger currentStep;


@property (nonatomic, strong) IBOutlet UIButton * connectToDeviceButton;


  // Step specific actions
- (IBAction)step6SendAction:(id)sender;
- (IBAction)step9DisconnectAction:(id)sender;


// Invisible buttons (used for commands in the spotlight)
- (IBAction)invisibleScanAction:(id)sender; // Step 1
- (IBAction)invisibleCancelAction:(id)sender; // Step 3

@property (nonatomic, strong) IBOutlet UIButton * step7NextButton;
@property (nonatomic, strong) IBOutlet UIButton * step8NextButton;

@end

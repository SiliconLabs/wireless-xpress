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

#import "TutorialViewController.h"
#import "SpotlightView.h"
#import "StepView.h"
#import "DevicesTableViewController.h"
#import "AppDelegate.h"
#import "DeviceDetailsViewController.h"

const double kTutorialOpacity = 0.7;

@interface TutorialViewController ()
- (void)setupConnectToDeviceButton;
@end

@implementation TutorialViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view from its nib.
  _currentStep = 1;

  [self.view.window setWindowLevel:UIWindowLevelStatusBar];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(deviceConnected:) name:ConnectedToDeviceNotitficationName object:nil];

  [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(deviceListChanged:) name:DeviceListChangedNotificationName object:nil];
}

- (void)deviceListChanged:(id)n
{
  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(0.1 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
    [self setupConnectToDeviceButton];
  });
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}


- (IBAction)tutorialConnectToDeviceAction:(id)sender
{
  NSAssert (2 == self.currentStep, @"Unexpected state");

  // programatically select a device.
  [[NSNotificationCenter defaultCenter] postNotificationName:TutorialConnectToDeviceNotificationName object:nil];

  ++_currentStep;
  [self showStep];

}

- (void)deviceConnected:(NSNotification *)n
{
  [self nextAction:nil];
}

- (IBAction)closeAction:(id)sender
{
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [[NSNotificationCenter defaultCenter] postNotificationName:CancelTutorialNotificationName object:nil];
}

- (IBAction)nextAction:(id)sender
{
  ++_currentStep;
  [self showStep];
}

- (IBAction)backAction:(id)sender
{
  --_currentStep;
  [self showStep];
}

- (void)showStep
{
  NSString * notification2Send = nil;

  StepView * stepView2Show = nil;
  switch (self.currentStep) {
    case 1:
      stepView2Show = self.step1View;
      break;
    case 2:
      stepView2Show = self.step2View;
      // setup the connect to device button
      [self setupConnectToDeviceButton];
      break;
    case 3:
      stepView2Show = self.step3View;
      notification2Send = TutorialStep3NotificationName;
      break;
    case 4:
      stepView2Show = self.step4View;
      break;
    case 5:
      stepView2Show = self.step5View;
      break;
    case 6:
      stepView2Show = self.step6View;
      notification2Send = TutorialStep6NotificationName;
      break;
    case 7:
      self.step7NextButton.enabled = NO;
      stepView2Show = self.step7View;
      [[AppDelegate sharedAppDelegate].selectedDevice writeBusMode:STREAM_MODE];
      break;
    case 8:
      self.step8NextButton.enabled = NO;
      stepView2Show = self.step8View;
      [self step8Script];
      break;
    case 9:
      stepView2Show = self.step9View;
      break;
  }

  if (stepView2Show && self.view != stepView2Show) {
    UIView * sv = self.view.superview;

    [self.view removeFromSuperview];
    [sv addSubview:stepView2Show];

    self.view = stepView2Show;

    [self.view setFrame:CGRectMake(0, 0, self.view.window.frame.size.width, self.view.window.frame.size.height)];

    // now show the spotlight at that location.

    BOOL fDoAnimation = (BOOL) stepView2Show.spotlightRadius > 0.0 && [SpotlightView spotlightView].spotlightRadius > 0.0;

    CGPoint newLocation = stepView2Show.spotlightLocation;
    CGFloat newRadius = stepView2Show.spotlightRadius;

    // adjust the location and radius.
    [self adjustForStep:stepView2Show spotlightLocation:&newLocation andSize: &newRadius];

    [[SpotlightView spotlightView] setSpotlightPosition:newLocation radius:newRadius opacity:kTutorialOpacity animated:fDoAnimation];

    [sv setNeedsDisplay];

    if (notification2Send) {
      [[NSNotificationCenter defaultCenter] postNotificationName:notification2Send object:nil];
    }
  }
}

#pragma mark - Step Specific Actions

- (IBAction)step6SendAction:(id)sender
{
  [[NSNotificationCenter defaultCenter] postNotificationName:TutorialStep6SendDataNotificationName object:nil];
  [self nextAction: sender];

  dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
    [[NSNotificationCenter defaultCenter] postNotificationName:DataReceivedNotificationName object:[@"Hello, yourself." dataUsingEncoding:NSUTF8StringEncoding]];

    self.step7NextButton.enabled = YES;
  });

}

- (IBAction)step9DisconnectAction:(id)sender
{
  [[NSNotificationCenter defaultCenter] postNotificationName:TutorialStep9DisconnectNotificationName object:nil];
  [self closeAction: sender];
}

- (void)setupConnectToDeviceButton
{
  if ( 0 == [DevicesTableViewController devicesTableViewController].devices.count) {
    [self.connectToDeviceButton setTitle:@"No device available." forState: UIControlStateDisabled];
    self.connectToDeviceButton.enabled = NO;
  } else {
    self.connectToDeviceButton.enabled = YES;
    BGXDevice * deviceRecord = SafeType([[DevicesTableViewController devicesTableViewController].devices firstObject], [BGXDevice class]);
#if TARGET_IPHONE_SIMULATOR
    [self.connectToDeviceButton setTitle:[NSString stringWithFormat:@"Connect to %@", deviceRecord.name] forState:UIControlStateNormal];

#else
    [self.connectToDeviceButton setTitle:[NSString stringWithFormat:@"Connect to %@", deviceRecord.name] forState:UIControlStateNormal];
#endif
  }
}

- (IBAction)invisibleScanAction:(id)sender
{
  if (! [AppDelegate sharedAppDelegate].isScanning) {
    [[AppDelegate sharedAppDelegate] scan];
  }
}

- (IBAction)invisibleCancelAction:(id)sender
{
    [[AppDelegate sharedAppDelegate].selectedDevice disconnect];
    [[AppDelegate sharedAppDelegate] stopTutorial:nil];
}

// executes a timed script to show the action on step 8.
- (void)step8Script
{
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.25 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
        [[AppDelegate sharedAppDelegate].selectedDevice writeBusMode:REMOTE_COMMAND_MODE];
        
        
        [DeviceDetailsViewController deviceDetailsViewController].sendTextField.text = @"get al";
        
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            
            [[SpotlightView spotlightView] setSpotlightPosition:CGPointMake(60,360) radius:50.0f opacity:kTutorialOpacity animated:YES];
            
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                [[NSNotificationCenter defaultCenter] postNotificationName:TutorialStep6SendDataNotificationName object:nil];
                [[SpotlightView spotlightView] setSpotlightPosition:CGPointMake(200,180) radius:200.0f opacity:kTutorialOpacity animated:YES];
                
                dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                    
                    self.step8NextButton.enabled = YES;
                });
                
            });
            
        });
        
    });

}

/*
    This method is a hack that allows us to make alterations to the spotlight point and size
    which is normally in absolute coordinates.

    Code here could make any alterations needed including determining the coordinates of a view. However
    for now it makes adjustments on screen size only.
 */
- (void)adjustForStep:(StepView *)stepView2Show spotlightLocation:(CGPoint  *)location andSize:(CGFloat *)size
{
  if (self.step5View == stepView2Show) {
    location->x = stepView2Show.frame.size.width / 2;
  } else if (self.step8View == stepView2Show) {
    location->x = stepView2Show.frame.size.width * 0.75f;
  }
}

@end

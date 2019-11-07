/*
 * Copyright 2018-2019 Silicon Labs
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in com pliance with the License.
 * You may obtain a copy of the License at
 * {{ http://www.apache.org/licenses/LICENSE-2.0}}
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import "BGXDeviceTableViewCell.h"
#import <bgxpress/SafeType.h>

const NSTimeInterval kAnimationTimeInterval = 0.2;
const NSUInteger kAnimationSteps = 3;

// Test Enum
typedef enum {
     NoTest
    ,StreamTest
    ,CommandTest
    ,TestDone
    
    ,Waiting
} SerialTestStep;

@implementation BGXDeviceTableViewCell
{
    NSTimer * animationTimer;
    NSUInteger animationStep;
    
    SerialTestStep serialTestStep;
}


- (void)awakeFromNib {
    [super awakeFromNib];
    // Initialization code
    
    [self.testButton setImage:[UIImage imageNamed:@"Test"] forState:UIControlStateNormal];
    [self.testButton setImage:[UIImage imageNamed:@"TestDisabled"] forState:UIControlStateDisabled];
    serialTestStep = NoTest;
}

- (void)setSelected:(BOOL)selected animated:(BOOL)animated {
    [super setSelected:selected animated:animated];

    // Configure the view for the selected state
}

- (IBAction)connectAction:(id)sender
{
    [self.device connect];
}

- (IBAction)disconnectAction:(id)sender
{
    if (![self.device disconnect]) {
        NSLog(@"Disconnect failed.");
    }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context
{
    if ([keyPath isEqualToString:@"deviceState"]) {
        BGXDevice * bgxDevice = SafeType(object, [BGXDevice class]);
        if (bgxDevice == self.device) {
            switch (self.device.deviceState) {
                case Connecting:
                case Interrogating:
                case Disconnecting:
                    [self.disconnectButton setEnabled:YES];
                    [self.connectButton setEnabled:NO];
                    [self startAnimation];
                    [self.testButton setEnabled:NO];
                    break;
                case Disconnected:
                    [self.disconnectButton setEnabled:NO];
                    [self.connectButton setEnabled:YES];
                    [self stopAnimation];
                    [self.testButton setEnabled:NO];
                    break;
                case Connected:
                    [self.disconnectButton setEnabled:YES];
                    [self.connectButton setEnabled:NO];
                    [self stopAnimation];
                    [self.testButton setEnabled:YES];
                    break;
            }
        }
    } else if ([keyPath isEqualToString:@"rssi"]) {
        self.rssi_label.text = [NSString stringWithFormat:@"%d", [self.device.rssi intValue]];
    }
}

- (void)setBGXDevice:(BGXDevice *)bgxDevice
{
    self.device_name.text = bgxDevice.name;
    
    self.device_uuid.text = bgxDevice.identifier.UUIDString;
    
    self.rssi_label.text = [NSString stringWithFormat:@"%d", [bgxDevice.rssi intValue]];

    if (self.device) {
        [self.device removeObserver:self forKeyPath:@"deviceState"];
    }
    
    self.device = bgxDevice;

    [self.device addObserver:self forKeyPath:@"deviceState"
                     options: (NSKeyValueObservingOptionInitial | NSKeyValueObservingOptionNew)
                     context:nil];
    
    [self.device addObserver:self forKeyPath:@"rssi" options:NSKeyValueObservingOptionNew context:nil];
    

    
   // self.device.serialDelegate = self;
}

- (void)startAnimation
{
    if (animationTimer)
        return;
    
    animationTimer = [NSTimer scheduledTimerWithTimeInterval:kAnimationTimeInterval repeats:YES block:^(NSTimer * timer){
        ++self->animationStep;
        
        switch (self->animationStep % kAnimationSteps) {
            case 0:
                self.dot1.color = [UIColor blackColor];
                self.dot2.color = [UIColor lightGrayColor];
                self.dot3.color = [UIColor darkGrayColor];
                break;
            case 1:
                self.dot1.color = [UIColor darkGrayColor];
                self.dot2.color = [UIColor blackColor];
                self.dot3.color = [UIColor lightGrayColor];
                break;
            case 2:
                self.dot1.color = [UIColor lightGrayColor];
                self.dot2.color = [UIColor darkGrayColor];
                self.dot3.color = [UIColor blackColor];
                break;
        }
        
    }];
}

- (void)stopAnimation
{
    [animationTimer invalidate];
    animationTimer = nil;
    switch (self.device.deviceState) {
        case Connected:
            self.dot1.color = [UIColor colorWithRed:0.0 green:0.2 blue:0.0 alpha:1.0];
            self.dot2.color = [UIColor colorWithRed:0.0 green:0.6 blue:0.0 alpha:1.0];
            self.dot3.color = [UIColor colorWithRed:0.0 green:1.0 blue:0.0 alpha:1.0];
            break;
        case Disconnected:
            self.dot1.color = [UIColor lightGrayColor];
            self.dot2.color = [UIColor lightGrayColor];
            self.dot3.color = [UIColor lightGrayColor];
            break;
        case Connecting:
        case Disconnecting:
        case Interrogating:
            NSAssert(NO, @"Invalid state");
            break;
    }
}



@end

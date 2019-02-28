//
//  DeviceDetailsViewController.m
//  multiconnect
//
//  Created by Brant Merryman on 10/15/18.
//  Copyright © 2018 Silicon Labs. All rights reserved.
//

#import "DeviceDetailsViewController.h"

typedef enum {
    SEND_MODE
    ,RECEIVE_MODE
    ,BUS_MODE_CHANGE_MODE
    ,ERROR_MODE
    
    ,INVALID_MODE
} TextMode;


@interface DeviceDetailsViewController ()

- (void)writeAttributedTextToConsole:(NSAttributedString *)attrs;

@property (nonatomic) TextMode textMode;

@end

@implementation DeviceDetailsViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    
    self.busMode = UNKNOWN_MODE;
    self.textMode = INVALID_MODE;
    [self.sendTextField becomeFirstResponder];
    self.textView.text = @"";
    
}

- (void)viewWillAppear:(BOOL)animated
{
    self.device.serialDelegate = self;
    self.device.deviceDelegate = self;
    self.busMode = self.device.busMode;
    switch (self.busMode) {
        case STREAM_MODE:
            self.busModeSelector.selectedSegmentIndex = 0;
            break;
        case REMOTE_COMMAND_MODE:
        case LOCAL_COMMAND_MODE:
            self.busModeSelector.selectedSegmentIndex = 1;
            break;
        default:
            break;
    }
    
    self.navigationItem.title = [self.device.name copy];
    
    NSLog(@"%@", self.device.device_unique_id);
}

- (void)viewWillDisappear:(BOOL)animated
{
    self.device.deviceDelegate = nil;
    self.device.serialDelegate = nil;
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/
- (IBAction)userSelectedBusMode:(id)sender
{
    const NSInteger kStreamMode = 0;
    const NSInteger kBusMode = 1;
    
    switch (self.busModeSelector.selectedSegmentIndex) {
        case kStreamMode:
            [self.device writeBusMode:STREAM_MODE];
            break;
        case kBusMode:
            [self.device writeBusMode:REMOTE_COMMAND_MODE];
            break;
    }
}

- (IBAction)sendAction:(id)sender
{
    if (STREAM_MODE == self.busMode) {
        
        NSAttributedString * attr = nil;
        
        if ([self.device canWrite]) {
            
            self.textMode = SEND_MODE;
            
            attr = [[NSAttributedString alloc] initWithString:[NSString stringWithFormat:@"\n< %@", self.sendTextField.text]
                                                   attributes:@{ NSForegroundColorAttributeName : [UIColor whiteColor] }];
            
            
            NSMutableString * string2Send = [self.sendTextField.text mutableCopy];
            
            [string2Send appendString:[NSString stringWithFormat:@"%c%c", 0x0D, 0x0A]];
            
            [self.device writeString:string2Send];

            
            self.sendTextField.text = @"";
            
            [self writeAttributedTextToConsole:attr];
            
        } else {
            NSLog(@"Can't write data");
            
            self.textMode = ERROR_MODE;
            
            attr = [[NSAttributedString alloc] initWithString:@"\nError: cannot write data" attributes:@{ NSForegroundColorAttributeName : [UIColor redColor] }];
            [self writeAttributedTextToConsole:attr];
        }
    } else if (REMOTE_COMMAND_MODE == self.busMode) {
        [self.device sendCommand:self.sendTextField.text args:@""];
        
        self.sendTextField.text = @"";
    }
}

- (void)writeAttributedTextToConsole:(NSAttributedString *)attrs
{
    NSMutableAttributedString * mattrs = [self.textView.attributedText mutableCopy];
    
    [mattrs appendAttributedString:attrs];
    
    self.textView.attributedText = mattrs;
}

- (IBAction)clearAction:(id)sender
{
    self.textView.attributedText = [[NSAttributedString alloc] initWithString:@""];
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField
{
    if (self.sendTextField == textField) {
        dispatch_async(dispatch_get_main_queue(), ^{
            [self sendAction: self.sendTextField];
        });
    }
    
    return NO;
}

#pragma mark - BGXDeviceDelegate Methods

- (void)stateChangedForDevice:(BGXDevice *)device
{
    NSLog(@"Device state changed: %@", [BGXDevice nameForDeviceState:device.deviceState]);
}

#pragma mark - BGXSerialDelegate Methods

- (void)busModeChanged:(BusMode)newBusMode forDevice:(BGXDevice *)device
{
    if (self.busMode != newBusMode) {
        self.busMode = newBusMode;
        self.textMode = BUS_MODE_CHANGE_MODE;
        NSString * busModeName = @"?";
        
        switch (newBusMode) {
            case STREAM_MODE:
                self.busModeSelector.selectedSegmentIndex = 0;
                busModeName = @"STREAM MODE";
                break;
            case LOCAL_COMMAND_MODE:
                self.busModeSelector.selectedSegmentIndex = 1;
                busModeName = @"LOCAL COMMAND MODE";
                break;
            case REMOTE_COMMAND_MODE:
                busModeName = @"REMOTE COMMAND MODE";
                self.busModeSelector.selectedSegmentIndex = 1;
                break;
            default:
                busModeName = @"UNEXPECTED BUS MODE";

                NSLog(@"Unexpected bus mode: %d", newBusMode);
                break;
        }
        
        NSAttributedString * attributedBusMode = [[NSAttributedString alloc] initWithString:[NSString stringWithFormat:@"\n %@", busModeName]
                                                                                 attributes: @{ NSForegroundColorAttributeName : [UIColor whiteColor] }];
        
        [self writeAttributedTextToConsole: attributedBusMode];
    }
}

- (void)dataRead:(NSData *)newData forDevice:(nonnull BGXDevice *)device
{
    if (self.device == device) {
        NSString * plainString;
        
        if ( RECEIVE_MODE != self.textMode) {
            plainString = [NSString stringWithFormat: @"\n> %@", [[NSString alloc] initWithData:newData encoding:NSASCIIStringEncoding] ];
            self.textMode = RECEIVE_MODE;
        } else {
            plainString = [NSString stringWithFormat: @"%@", [[NSString alloc] initWithData:newData encoding:NSASCIIStringEncoding] ];
        }
        NSAttributedString * attributedReceivedString = [[NSAttributedString alloc] initWithString: plainString
                                                                                        attributes: @{ NSForegroundColorAttributeName : [UIColor greenColor] }];
        
        
        [self writeAttributedTextToConsole: attributedReceivedString];
    }
}

- (void)dataWrittenForDevice:(BGXDevice *)device
{
    NSLog(@"dataWritten for device: %@", [device description]);
}

@end

//
//  ThroughputViewController.m
//  throughput
//
//  Created by Brant Merryman on 4/12/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import "ThroughputViewController.h"

@interface ThroughputViewController ()

@property (nonatomic, strong) NSMutableArray * mLoopbackArray;
@property (nonatomic) BOOL loopbackRunning;
@end

@implementation ThroughputViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    self.mLoopbackArray = [NSMutableArray arrayWithCapacity:1000];
    self.loopbackRunning = NO;
    
    // Do any additional setup after loading the view.
}

- (void)viewWillAppear:(BOOL)animated
{
    self.selectedDevice.deviceDelegate = self;
    self.selectedDevice.serialDelegate = self;
    
    [self.selectedDevice connect];
    self.nameLabel.text = self.selectedDevice.name;
    
    self.mBytesRx = 0;
    self.mBPS = 0.0;
    self.firstByteTime = nil;
    self.lastByteTime = nil;
    
    [self.bytesRx setText:@"0"];
    [self.kbps setText:@"-"];
    [self.mLoopback setOn:NO];
    
    [self.mAck setOn:YES];
    self.selectedDevice.writeWithResponse = YES;
    


}

- (void)viewWillDisappear:(BOOL)animated
{
    [self.selectedDevice disconnect];
}

- (void)stateChangedForDevice:(BGXDevice *)device
{
    NSString * labelValue = @"?";
    switch (device.deviceState) {
        case Interrogating:
            labelValue = @"Interrogating";
            break;
        case Disconnected:
            labelValue = @"Disconnected";
            break;
        case Connecting:
            labelValue = @"Connecting";
            break;
        case Connected:
            labelValue = @"Connected";
            
            break;
        case Disconnecting:
            labelValue = @"Disconnecting";
            break;
    }
    self.statusLabel.text = labelValue;
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

- (void)busModeChanged:(BusMode)newBusMode forDevice:(nonnull BGXDevice *)device {
    
}

- (void)writeLoop
{
    self.loopbackRunning = YES;
    NSData * data2Write = [self.mLoopbackArray firstObject];
    if ( data2Write && [self.selectedDevice writeData:data2Write]) {
        [self.mLoopbackArray removeObject:data2Write];
    }
    self.loopbackRunning = NO;
}

- (void)dataRead:(nonnull NSData *)newData forDevice:(nonnull BGXDevice *)device {
    if (nil == self.firstByteTime) {
        self.firstByteTime = [NSDate date];
    }
    self.mBytesRx += newData.length;
    
    self.lastByteTime = [NSDate date];
    
    [self redrawStats];
    
    if (self.mLoopback.isOn) {
        [_mLoopbackArray addObject:newData];
        if (!self.loopbackRunning) {
            dispatch_async(dispatch_get_main_queue(), ^{
                [self writeLoop];
            });
        }
//        BOOL result = [self.selectedDevice writeData:newData];
//        NSLog(@"writeData: %@", result ? @"YES" : @"NO");
    }
}

- (void)dataWrittenForDevice:(nonnull BGXDevice *)device {
    NSLog(@"DataWrittenForDevice");
    dispatch_async(dispatch_get_main_queue(), ^{
        [self writeLoop];
    });
}

- (void)dataWriteFailedForDevice:(BGXDevice *)device error:(NSError *) error
{
    NSLog(@"Data write failed for %@ Error: %@", device.description, error.description);
}

#pragma mark - IB Actions

- (IBAction)clearAction:(id)sender
{
    NSLog(@"clearAction");
    self.mBytesRx = 0;
    self.mBPS = 0;
    self.firstByteTime = nil;
    self.lastByteTime = nil;
    [self redrawStats];
}

const NSString * kDataPattern = @"0123456789";
- (IBAction)transmitAction:(id)sender
{
    int dataTx;
    NSLog(@"transmitAction");
    NSScanner * scanner = [[NSScanner alloc] initWithString:self.dataTxSize.text];
    
    BOOL fScanned = [scanner scanInt:&dataTx];
    if (fScanned) {
        NSMutableString * ms = [[NSMutableString alloc] initWithCapacity: dataTx + 1];

        for (int i = dataTx; i > 0; ) {
            if (i >= kDataPattern.length) {
                [ms appendString: (NSString *) kDataPattern];
                i -= kDataPattern.length;
            } else {
                NSString * ss = [kDataPattern substringFromIndex:kDataPattern.length - i];
                [ms appendString:ss];
                i = 0;
            }
        }
        NSAssert(ms.length == dataTx, @"Invalid length");
        
        BOOL fResult = [self.selectedDevice writeString:ms];
        if (!fResult) {
            NSLog(@"write failed.");
        }
        
    } else {
        NSLog(@"Error scanning integer.");
    }
}

- (IBAction)loopbackAction:(id)sender
{
    NSLog(@"loopbackAction");
}

- (IBAction)ackAction:(id)sender
{
    NSLog(@"ackAction");
    
    self.selectedDevice.writeWithResponse = self.mAck.isOn;
}

- (BOOL)textField:(UITextField *)textField shouldChangeCharactersInRange:(NSRange)range replacementString:(NSString *)string
{
    
    NSCharacterSet *strCharSet = [NSCharacterSet characterSetWithCharactersInString:@"1234567890"];
    
    strCharSet = [strCharSet invertedSet];
    //And you can then use a string method to find if your string contains anything in the inverted set:
    
    NSRange r = [string rangeOfCharacterFromSet:strCharSet];
    if (r.location != NSNotFound) {
        return NO;
    }
    return YES;
}

- (BOOL)textFieldShouldClear:(UITextField *)textField
{
    return YES;
}

- (BOOL)textFieldShouldReturn:(UITextField *)textField
{
    [textField resignFirstResponder];
    return NO;
}


- (void)redrawStats
{
    [self.bytesRx setText:[NSString stringWithFormat:@"%ld", self.mBytesRx]];
    if (self.lastByteTime && self.firstByteTime) {
        NSTimeInterval  ti = [self.lastByteTime timeIntervalSinceDate:self.firstByteTime];
        if (ti > 0) {
            double kbps = ((double) self.mBytesRx * 8) / (ti * 1000.0);
            
            [self.kbps setText:[NSString stringWithFormat:@"%f", kbps]];
        } else {
            [self.kbps setText:@"-"];
        }
        
    } else {
        [self.kbps setText:@"-"];
    }
}



@end

//
//  ThroughputViewController.h
//  throughput
//
//  Created by Brant Merryman on 4/12/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <BGXpress/BGXpress.h>

NS_ASSUME_NONNULL_BEGIN

@interface ThroughputViewController : UIViewController <BGXDeviceDelegate, BGXSerialDelegate, UITextFieldDelegate>



- (IBAction)clearAction:(id)sender;
- (IBAction)transmitAction:(id)sender;

- (IBAction)loopbackAction:(id)sender;

- (IBAction)ackAction:(id)sender;



@property (nonatomic, strong) IBOutlet UILabel * nameLabel;
@property (nonatomic, strong) IBOutlet UILabel * statusLabel;

@property (nonatomic, strong) IBOutlet UITextField * dataTxSize;



@property (nonatomic, strong) IBOutlet UILabel * bytesRx;
@property (nonatomic, strong) IBOutlet UILabel * kbps;

@property (nonatomic, strong) IBOutlet UISwitch * mLoopback;

@property (nonatomic, strong) IBOutlet UISwitch * mAck;

@property (nonatomic, strong) BGXDevice * selectedDevice;

@property (nonatomic) NSInteger mBytesRx;
@property (nonatomic) double mBPS;
@property (nonatomic, strong) NSDate * _Nullable firstByteTime;
@property (nonatomic, strong) NSDate * _Nullable lastByteTime;

@end

NS_ASSUME_NONNULL_END

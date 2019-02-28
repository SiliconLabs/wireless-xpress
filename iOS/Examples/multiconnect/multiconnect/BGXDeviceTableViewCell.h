//
//  BGXDeviceTableViewCell.h
//  multiconnect
//
//  Created by Brant Merryman on 10/11/18.
//  Copyright © 2018 Silicon Labs. All rights reserved.
//

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

//
//  DeviceDetailsViewController.h
//  multiconnect
//
//  Created by Brant Merryman on 10/15/18.
//  Copyright © 2018 Silicon Labs. All rights reserved.
//

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

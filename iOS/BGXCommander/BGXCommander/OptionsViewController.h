//
//  OptionsViewController.h
//  BGXCommander
//
//  Created by Brant Merryman on 11/20/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

extern const NSString * kNewLinesOnSendKeyName;
extern const NSString * kAckdWritesForOTA;

@interface OptionsViewController : UIViewController

- (IBAction)saveAction:(id)sender;
- (IBAction)cancelAction:(id)sender;

@property (nonatomic) IBOutlet UISwitch * newlinesOnSend;
@property (nonatomic) IBOutlet UISwitch * ackdWritesForOTA;

@end

NS_ASSUME_NONNULL_END

//
//  OptionsViewController.m
//  BGXCommander
//
//  Created by Brant Merryman on 11/20/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import "OptionsViewController.h"
#import <bgxpress/SafeType.h>

const NSString * kNewLinesOnSendKeyName = @"newLinesOnSend";
const NSString * kAckdWritesForOTA = @"acknowledgedOTAWrites";

@interface OptionsViewController ()

@end

@implementation OptionsViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    id newLinesOnSend = [[NSUserDefaults standardUserDefaults] objectForKey:(NSString *)kNewLinesOnSendKeyName];
    id ackdWritesOnOTA = [[NSUserDefaults standardUserDefaults] objectForKey:(NSString *)kAckdWritesForOTA];

    NSNumber * numNewLinesOnSend = SafeType(newLinesOnSend, [NSNumber class]);
    NSNumber * numAckdOTAWrites = SafeType(ackdWritesOnOTA, [NSNumber class]);
    
    if (nil == numNewLinesOnSend) {
        numNewLinesOnSend = [NSNumber numberWithBool:YES];
    }
    
    if (nil == numAckdOTAWrites) {
        numAckdOTAWrites = [NSNumber numberWithBool:NO];
    }

    [self.newlinesOnSend setOn: [numNewLinesOnSend boolValue]];
    [self.ackdWritesForOTA setOn: [numAckdOTAWrites boolValue]];
}

/*
#pragma mark - Navigation

// In a storyboard-based application, you will often want to do a little preparation before navigation
- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
    // Get the new view controller using [segue destinationViewController].
    // Pass the selected object to the new view controller.
}
*/

- (IBAction)saveAction:(id)sender
{
    BOOL switchState = self.newlinesOnSend.isOn;
    BOOL switchState2 = self.ackdWritesForOTA.isOn;
    
    [self.presentingViewController dismissViewControllerAnimated:YES completion:^{
        [[NSUserDefaults standardUserDefaults] setBool: switchState forKey:(NSString *)kNewLinesOnSendKeyName];
        [[NSUserDefaults standardUserDefaults] setBool: switchState2 forKey:(NSString *)kAckdWritesForOTA];
        [[NSUserDefaults standardUserDefaults] synchronize];
        
        [[NSNotificationCenter defaultCenter] postNotificationName:OptionsChangedNotificationName object:@{ kNewLinesOnSendKeyName : [NSNumber numberWithBool:switchState] }];
    }];
}

- (IBAction)cancelAction:(id)sender
{
    [self.presentingViewController dismissViewControllerAnimated:YES completion:^{  }];
}

@end

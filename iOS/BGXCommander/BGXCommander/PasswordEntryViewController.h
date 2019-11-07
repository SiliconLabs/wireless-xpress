/*
 * Copyright 2019 Silicon Labs
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

NS_ASSUME_NONNULL_BEGIN

typedef enum {
     unknown_password_kind
    ,remoteConsolePasswordKind
    ,OTAPasswordKind
    ,maxPasswordKind
} password_kind_t;



@class BGXDevice;

@interface PasswordEntryViewController : UIViewController <UITextFieldDelegate>

// a useful class method.
+ (NSString * _Nullable)passwordForType:(password_kind_t)kind forDevice:(BGXDevice *)device;


- (IBAction)okAction:(id)sender;
- (IBAction)cancelAction:(id)sender;

@property (nonatomic, weak) IBOutlet UILabel * passwordInstructions;
@property (nonatomic, weak) IBOutlet UITextField * passwordField;
@property (nonatomic, weak) IBOutlet UIView * box;


@property (nonatomic) password_kind_t password_kind;
@property (nonatomic, strong) BGXDevice * device;

/*
 * Some dispatch blocks that will be invoked after the view controller is
 * dismissed by the user pressing OK or Cancel.
 */
@property (nonatomic, strong) dispatch_block_t ok_post_action;
@property (nonatomic, strong) dispatch_block_t cancel_post_action;

@end

NS_ASSUME_NONNULL_END

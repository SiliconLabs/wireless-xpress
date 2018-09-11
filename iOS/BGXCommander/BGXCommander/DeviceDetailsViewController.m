/*
 * Copyright 2018 Silicon Labs
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

#import "DeviceDetailsViewController.h"

#import "MMDrawerBarButtonItem.h"
#import "AppDelegate.h"

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

@property (nonatomic, strong) NSArray * observerReferences;

@end

__weak DeviceDetailsViewController * gDeviceDetailsViewController = nil;

@implementation DeviceDetailsViewController

+ (instancetype)deviceDetailsViewController
{
    return gDeviceDetailsViewController;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    gDeviceDetailsViewController = self;
    
    self.busMode = UNKNOWN_MODE;
    self.textMode = INVALID_MODE;
    
    self.lineEndings = CRLF;
    
    MMDrawerBarButtonItem * mmDrawerBarButtonItem = [[MMDrawerBarButtonItem alloc] initWithTarget:self action:@selector(rightDrawerButtonPress:)];
    
    [mmDrawerBarButtonItem setTintColor:[UIColor whiteColor]];
    self.navigationItem.rightBarButtonItem = mmDrawerBarButtonItem;
    
    [self.sendTextField becomeFirstResponder];
    self.textView.text = @"";
    
    
    __weak DeviceDetailsViewController * me = self;
    
    id oref1 = [[NSNotificationCenter defaultCenter] addObserverForName:TutorialStep6NotificationName object:nil queue:nil usingBlock:^(NSNotification *n){
        me.sendTextField.text = @"Hello, world!";
    }];
    
    id oref2 = [[NSNotificationCenter defaultCenter] addObserverForName:TutorialStep6SendDataNotificationName object:nil queue:nil usingBlock:^(NSNotification * n){
        
        [me sendAction:nil];
    }];
    
    id oref3 = [[NSNotificationCenter defaultCenter] addObserverForName:TutorialStep9DisconnectNotificationName object:nil queue:nil usingBlock:^(NSNotification * n){
        NSLog(@"dismissing view controller.");
        [me.navigationController popViewControllerAnimated:YES];
    }];
    
    self.observerReferences = @[oref1, oref2, oref3];
    
}

- (void)viewWillAppear:(BOOL)animated
{
    
    [[AppDelegate sharedAppDelegate].bgxManager addObserver:self forKeyPath:@"busMode" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionInitial context:nil];
    
    [[AppDelegate sharedAppDelegate].bgxManager addObserver:self forKeyPath:@"connectionState" options:NSKeyValueObservingOptionNew context:nil];
    
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(dataReceived:) name:DataReceivedNotificationName object:nil];
    
    [super viewWillAppear:animated];
    
}

- (void)viewWillDisappear:(BOOL)animated
{
    
    [[AppDelegate sharedAppDelegate].bgxManager removeObserver:self forKeyPath:@"busMode"];
    [[AppDelegate sharedAppDelegate].bgxManager removeObserver:self forKeyPath:@"connectionState"];
    
    [[NSNotificationCenter defaultCenter] removeObserver:self name:DataReceivedNotificationName object:nil];
    
    for (id iRef in self.observerReferences) {
        [[NSNotificationCenter defaultCenter] removeObserver:iRef];
    }
    
    [[AppDelegate sharedAppDelegate].bgxManager disconnect];
    
    [super viewWillDisappear:animated];
}

/** In this example, sending happens when the user presses a Send button
 and then a line of text is sent all at once. Line endings are appended as per
 the line endings setting (currently CRLF, but you could set this in a UI if desired).
 
 If you are connected to a device, it will usually be in either STREAM_MODE or
 REMOTE_COMMAND_MODE.
 */
- (IBAction)sendAction:(id)sender
{
    if (STREAM_MODE == self.busMode) {
        
        NSAttributedString * attr = nil;
        
        if ([[AppDelegate sharedAppDelegate].bgxManager canWrite]) {
            
            self.textMode = SEND_MODE;
            
            attr = [[NSAttributedString alloc] initWithString:[NSString stringWithFormat:@"\n< %@", self.sendTextField.text]
                                                   attributes:@{ NSForegroundColorAttributeName : [UIColor whiteColor] }];
            
            
            NSMutableString * string2Send = [self.sendTextField.text mutableCopy];
            
            switch (self.lineEndings) {
                case None:
                    break;
                case CR:
                    [string2Send appendString:[NSString stringWithFormat:@"%c", 0x0D]];
                    break;
                case LF:
                    [string2Send appendString:[NSString stringWithFormat:@"%c", 0x0A]];
                    break;
                case CRLF:
                    [string2Send appendString:[NSString stringWithFormat:@"%c%c", 0x0D, 0x0A]];
                    break;
                case LFCR:
                    [string2Send appendString:[NSString stringWithFormat:@"%c%c", 0x0A, 0x0D]];
                    break;
                default:
                    break;
            }
            
            [[AppDelegate sharedAppDelegate].bgxManager writeString:string2Send];
            
            self.sendTextField.text = @"";
            
            [self writeAttributedTextToConsole:attr];
            
        } else {
            NSLog(@"Can't write data");
            
            self.textMode = ERROR_MODE;
            
            attr = [[NSAttributedString alloc] initWithString:@"\nError: cannot write data" attributes:@{ NSForegroundColorAttributeName : [UIColor redColor] }];
            [self writeAttributedTextToConsole:attr];
        }
    } else if (REMOTE_COMMAND_MODE == self.busMode) {
        [[AppDelegate sharedAppDelegate].bgxManager sendCommand:self.sendTextField.text args:@""];
        
        self.sendTextField.text = @"";
    }
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context
{
    if ([keyPath isEqualToString:@"connectionState"]) {
        switch([AppDelegate sharedAppDelegate].bgxManager.connectionState) {
            case DISCONNECTED:
                [self.navigationController popViewControllerAnimated:YES];
                break;
            default:
                NSLog(@"Detected a connectionState change.");
                break;
        }
    } else if ([keyPath isEqualToString:@"busMode"]) {
        
        if (self.busMode != [AppDelegate sharedAppDelegate].bgxManager.busMode) {
            
            self.textMode = BUS_MODE_CHANGE_MODE;
            
            self.busMode = [AppDelegate sharedAppDelegate].bgxManager.busMode;
            NSString * modeName = @"?";
            
            switch([AppDelegate sharedAppDelegate].bgxManager.busMode) {
                case STREAM_MODE:
                    modeName = @"STREAM_MODE";
                    self.busModeSelector.selectedSegmentIndex = 0;
                    break;
                case LOCAL_COMMAND_MODE: /// This case ordinarily wouldn't happen while you are connected.
                    modeName = @"LOCAL_COMMAND_MODE";
                    self.busModeSelector.selectedSegmentIndex = 1;
                    break;
                case REMOTE_COMMAND_MODE:
                    modeName = @"REMOTE_COMMAND_MODE";
                    self.busModeSelector.selectedSegmentIndex = 1;
                    break;
                default:
                    modeName = @"SOME_OTHER_MODE";
                    break;
            }
            
            
            NSAttributedString * attributedBusMode = [[NSAttributedString alloc] initWithString:[NSString stringWithFormat:@"\n %@", modeName]
                                                                                     attributes: @{ NSForegroundColorAttributeName : [UIColor whiteColor] }];
            
            [self writeAttributedTextToConsole: attributedBusMode];
            
        }
    }
}

/** User is changing the command mode using the segmented control.
 */
- (IBAction)userSelectedBusMode:(id)sender
{
    const NSInteger kStreamMode = 0;
    const NSInteger kBusMode = 1;
    
    switch (self.busModeSelector.selectedSegmentIndex) {
        case kStreamMode:
            [[AppDelegate sharedAppDelegate].bgxManager writeBusMode: STREAM_MODE];
            break;
        case kBusMode:
            [[AppDelegate sharedAppDelegate].bgxManager writeBusMode: REMOTE_COMMAND_MODE];
            break;
    }
}

/** Data received from the BGX device is passed to the BGXCommanderDelegate which
 in this app is the AppDelegate. Then the app delegate posts it as a notification
 which we pick up here. You may wish to do this differently.
 */
- (void)dataReceived:(NSNotification *)n
{
    NSString * plainString;
    
    NSData * data = [n object];
    
    if ( RECEIVE_MODE != self.textMode) {
        plainString = [NSString stringWithFormat: @"\n> %@", [[NSString alloc] initWithData:data encoding:NSASCIIStringEncoding] ];
        self.textMode = RECEIVE_MODE;
    } else {
        plainString = [NSString stringWithFormat: @"%@", [[NSString alloc] initWithData:data encoding:NSASCIIStringEncoding] ];
    }
    NSAttributedString * attributedReceivedString = [[NSAttributedString alloc] initWithString: plainString
                                                                                    attributes: @{ NSForegroundColorAttributeName : [UIColor greenColor] }];
    
    
    [self writeAttributedTextToConsole: attributedReceivedString];
    
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
            [self sendAction: nil];
        });
    }
    
    return NO;
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

/*
 #pragma mark - Navigation
 
 // In a storyboard-based application, you will often want to do a little preparation before navigation
 - (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender {
 // Get the new view controller using [segue destinationViewController].
 // Pass the selected object to the new view controller.
 }
 */

-(void)rightDrawerButtonPress:(id)sender{
    [self.sendTextField resignFirstResponder]; // we want the keyboard to hide when the user opens the drawer.
    
    [[AppDelegate sharedAppDelegate].drawerController toggleDrawerSide:MMDrawerSideRight animated:YES completion:nil];
}

@end

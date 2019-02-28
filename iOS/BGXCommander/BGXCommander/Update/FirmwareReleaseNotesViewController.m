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

#import "FirmwareReleaseNotesViewController.h"



@interface FirmwareReleaseNotesViewController ()

@end

@implementation FirmwareReleaseNotesViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    
    
    /* The reason we are creating the WKWebView in code rather than in InterfaceBuilder is because
     * of a bug that exists in WKWebView in iOS8 - iOS11. Apple addressed the bug by creating a build error
     * when you add a WKWebView into a xib or storyboard file.
     */
    wkv = [[WKWebView alloc] initWithFrame:CGRectMake(0, 0, [UIScreen mainScreen].bounds.size.width, [UIScreen mainScreen].bounds.size.height - 65.0f)];
    
    [webContainer addSubview:wkv];
 

    [wkv loadRequest:[NSURLRequest requestWithURL:[NSURL URLWithString:@"https://docs.silabs.com/gecko-os/1/bgx/latest/relnotes"]]];
}

- (void)viewWillLayoutSubviews
{
    [super viewWillLayoutSubviews];
    
    [wkv setFrame:CGRectMake(0,0,webContainer.frame.size.width, webContainer.frame.size.height)];
    
}

- (IBAction)doneAction:(id)sender
{
    [[NSNotificationCenter defaultCenter] postNotificationName:FirmwareReleaseNotesShouldCloseNotificationName object:nil];
}

@end

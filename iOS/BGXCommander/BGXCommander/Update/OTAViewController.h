/*
* Copyright 2018-2020 Silicon Labs
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
#import <bgxpress/bgxpress.h>

NS_ASSUME_NONNULL_BEGIN

@interface OTAViewController : UIViewController <BGX_OTA_Updater_Delegate> {

    
    IBOutlet UIActivityIndicatorView * spinner;
    IBOutlet UIProgressView * determined_progress;
    
    IBOutlet UILabel * updateLabel;
    
    IBOutlet UILabel * toVersion;
    IBOutlet UILabel * fromVersion;
    
    IBOutlet UIButton * cancelButton;
}

/**
 * @param infoDict contains some keys to tell this controller what ought to be updated.
 */
- (void)prepareToUpdate:(NSDictionary *)infoDict;


// start the update.
- (void)performUpdate:(NSDictionary *)versionDict;

- (IBAction)cancelAction:(id)sender;

@end

NS_ASSUME_NONNULL_END

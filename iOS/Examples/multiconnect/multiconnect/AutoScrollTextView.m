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

#import "AutoScrollTextView.h"

const NSTimeInterval kAutoScrollTimeInterval = 0.2f;

@interface AutoScrollTextView ()

@property (nonatomic, strong) NSTimer * autoScrollTimer;

@end

@implementation AutoScrollTextView

- (void)layoutSubviews
{
  [super layoutSubviews];

  BOOL contentChanged = NO;
  unsigned char tempBuf[CC_MD5_DIGEST_LENGTH];
  CC_MD5([self.text UTF8String], (CC_LONG) self.text.length, tempBuf);

  for (int i=0;i<CC_MD5_DIGEST_LENGTH; ++i) {
    if (hashBuffer[i] != tempBuf[i]) {
      contentChanged = YES;
      hashBuffer[i] = tempBuf[i];
    }
  }

  if (contentChanged) {

    if (self.autoScrollTimer) {
      [self.autoScrollTimer invalidate];
    }

    self.autoScrollTimer = [NSTimer scheduledTimerWithTimeInterval:kAutoScrollTimeInterval repeats:NO block:^(NSTimer * timer){
      if (self.text.length > 1) {
        [self flashScrollIndicators];
        [self scrollRangeToVisible:NSMakeRange(self.text.length - 1, 1)];
      }
      self.autoScrollTimer = nil;
    }];

  }
}


@end

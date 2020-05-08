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

#import "NSAttributedString+AttributedStringFromRTF.h"

@implementation NSAttributedString (AttributedStringFromRTF)

- (instancetype)initWithRTFFileName:(NSString *)rtfFileName
{

  NSAssert([rtfFileName hasSuffix:@"rtf"], @"Invalid filename");

  NSString * filePath = [[NSBundle mainBundle] pathForResource:[rtfFileName substringWithRange:NSMakeRange(0, rtfFileName.length - 4)]
                                                        ofType:@"rtf"];


  NSError * err = nil;
  NSDictionary * documentAttributes = nil;
  self = [self initWithData:[NSData dataWithContentsOfFile:filePath]
                    options:@{}
          documentAttributes:&documentAttributes
                       error:&err];


  if (err) {
    @throw [NSException exceptionWithName:@"ObjectNotInitialized" reason:@"Unable to create attributed string" userInfo:@{@"error" : err }];
  }

  return self;
}

@end

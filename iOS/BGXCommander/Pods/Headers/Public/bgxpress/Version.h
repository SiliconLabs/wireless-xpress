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

#import <Foundation/Foundation.h>


/**
 * @addtogroup Version Version
 *
 * Version is a class used to work with (parse, enforce, and compare) the
 * version scheme used for BGX Firmware.
 * @{
 */

NS_ASSUME_NONNULL_BEGIN
/*
 Version scheme:
 1.1.1229.0
 ^ major
   ^ minor
      ^ build number
          ^revision
*/
@interface Version : NSObject {
    int _major;
    int _minor;
    int _build;
    int _revision;
}

/**
 * Creates a new version object from a string object from the BGX.
 * One good place to get this is the firmwareRevision property of BGXDevice.
 * Also, the DMS firmware list will contain a version string. You can use
 * Version class to compare these strings.
 */
+ (Version *)versionFromString:(NSString *)versionString;

/**
 * Initializes an instance from a string object from the BGX.
 * One good place to get this is the firmwareRevision property of BGXDevice.
 * Also, the DMS firmware list will contain a version string. You can use
 * Version class to compare these strings.
 */
- (id)initWithString:(NSString *)versionString;

/**
 * Compares two version objects and returns NSComparisonResult.
 *
 */
- (NSComparisonResult)compare:(Version *)otherVersion;

@property (nonatomic, readonly) int major;
@property (nonatomic, readonly) int minor;
@property (nonatomic, readonly) int build;
@property (nonatomic, readonly) int revision;


@end

NS_ASSUME_NONNULL_END

/** @} */

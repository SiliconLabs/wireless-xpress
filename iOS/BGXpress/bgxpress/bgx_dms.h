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

#import <Foundation/Foundation.h>

/**
 * @addtogroup bgx_dms BGX DMS
 *
 * The BGX DMS module is used for interacting with the Silicon Labs Device Management Service (DMS)
 * in order to get a list of firmware versions that are available for your BGX device and to download firmware images.
 *
 * @{
 */

/**
 * bgx_dms is used to get a list of firmware versions that are compatible with your BGX device
 * and to download firmware images that can be loaded using BGX_OTA_Updater.
 *
 * @remark The bgx_dms class depends on the ability to reach the Silicon Labs DMS servers. As soon as an instance of this class is created you
 * may begin to receive NSNotifications of type DMSServerReachabilityChangedNotificationName. Therefore, your app should reigster for these notifications
 * before creating an instance of this class.
 */
@interface bgx_dms : NSObject {

  NSArray * _firmwareList;
}

/**
 * Retrieve a string containing the part id for a specific device id.
 *
 * This will return nil if the device id indicates an invalid part.
 *
 * @param device_uuid the UUID for the individual BGX part.
 * @returns part id for the device. This is currently either bgx13s or bgx13p.
 */
+ (NSString *)bgxPartInfoForDeviceID:(NSString *)device_uuid;

/**
 * Initialize DMS for a specific BGX device ID.
 *
 * @param bgx_unique_device_id need a description here.
 * @returns what does it return
 */
- (id)initWithBGXUniqueDeviceID:(NSString *)bgx_unique_device_id;

/**
 * Pull a list of available firmware
 *
 * @param completionBlock description of the variable
 */
- (void)retrieveAvailableVersions:(void (^)(NSError *, NSArray *))completionBlock;

/**
 * Retrieve the specified firmware image by version number.
 *
 * If unable to load it, a non-nil error parameter will result and
 * firmware_path will be nil. One parameter or the other will be nil
 * depending on whether the image was loaded.
 *
 * @param version is the firmware version to load
 * @param completionBlock needs explanation
 */
- (void)loadFirmwareVersion:(NSString *)version completion:(void (^)(NSError *, NSString * firmware_path))completionBlock;

/**
 * Reports the installation to the DMS system for the purpose of analytic
 * tracking.
 *
 * @param bgx_device_uuid is the UUID of the BGX device to report.
 * @param bundleid is the firmware bundle ID that was loaded.
*/
+ (void)reportInstallationResultWithDeviceUUID:(NSString*)bgx_device_uuid version:(NSString*)bundleid;

/**
 * This array contains NSDictionary objects with
 * the following keys:
 *
 * - version - NSString containing the version
 * - tag - NSString containing the firmware flavor.
 * - size - NSNumber containing the size of the firmware in bytes.
 */
@property (nonatomic, strong) NSArray * firmwareList;

@end

/**
 * This notification is sent when the reachability of the dms server changes.
 *
 * Object is an NSNumber containing a BOOL. YES means the server is reachable.
 * NO means it isn't reachable. This is only sent when this changes.
 * Wait for it before trying to call loadFirmwareVersion: or retrieveAvailableVersions:
 */
extern NSString * DMSServerReachabilityChangedNotificationName;

/**
 * This notification is sent when a new list of BGX Firmware versions
 * is loaded from DMS.
 *
 * The object is an NSArray containing NSDictionary objects with
 * the following keys:
 * - version - NSString containing the version
 * - tag - NSString containing the firmware flavor.
 * - size - NSNumber containing the size of the firmware in bytes.
 */
extern NSString * NewBGXFirmwareListNotificationName;

/** @} */

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

#import <Foundation/Foundation.h>
#import <SystemConfiguration/SystemConfiguration.h>

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
    SCNetworkReachabilityRef _reachabilityRef;
}

/**
 * Initialize DMS for a specific BGX device ID, specify platformIdentifier.
 * This new version allows you to specify the platformIdentifier which
 * should be read from the platformIdentifier property of the BGXDevice.
 * @param bgx_unique_device_id - the unique device ID of the device being updated
 * @param platformIdentifier - a string identifying the platform usually "bgx13" or "bgx220"
 * @returns the intialized instance of bgx_dms
 */
- (id)initWithBGXUniqueDeviceID:(NSString *)bgx_unique_device_id
                    forPlatform:(NSString *)platformIdentifier
                            __attribute__((deprecated));

/**
 * Initialize DMS for a specific BGX device ID.
 * @deprecated - use initWithBGXUniqueDeviceID:forPlatform: instead.
 * @param bgx_unique_device_id - the unique device ID of the device being updated
 * @returns the intialized instance of bgx_dms
 */
- (id)initWithBGXUniqueDeviceID:(NSString *)bgx_unique_device_id
                            __attribute__((deprecated));

/**
 * Pull a list of available firmware
 *
 * @param completionBlock to be called when the operation is complete.
 *        Parameters to this block include an error (nil on success, non-nil on error)
 *        and an NSArray of the available versions of firmware.
 */
- (void)retrieveAvailableVersions:(void (^)(NSError *, NSArray *))completionBlock
                            __attribute__((deprecated("Use method retrieveAvailableFirmwareVersions:completionBlock from bgx_ota_update")));

/**
 * Retrieve the specified firmware image from DMS by version number.
 *
 * If unable to load it, a non-nil error parameter will result and
 * firmware_path will be nil. One parameter or the other will be nil
 * depending on whether the image was loaded.
 *
 * @param version is the firmware version to load
 * @param completionBlock Block to be called when the operation is complete.
 */
- (void)loadFirmwareVersion:(NSString *)version completion:(void (^)(NSError *, NSString * firmware_path))completionBlock
                            __attribute__((deprecated("Use method getPathOfFirmwareFileWithVersion:version from bgx_ota_update")));

/**
 * Reports the installation to the DMS system for the purpose of analytic
 * tracking.
 *
 * @param bgx_device_uuid is the UUID of the BGX device to report.
 * @param bundleid is the firmware bundle ID that was loaded.
*/
+ (void)reportInstallationResultWithDeviceUUID:(NSString*)bgx_device_uuid version:(NSString*)bundleid
                                        __attribute__((deprecated));

/**
 * This array contains NSDictionary objects with
 * the following keys:
 *
 * - version - NSString containing the version
 * - description - NSString containing the description of the firmware version.
 * - tag - NSString containing the firmware flavor.
 * - size - NSNumber containing the size of the firmware in bytes.
 */
@property (nonatomic, strong) NSArray * firmwareList __attribute__((deprecated));;

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

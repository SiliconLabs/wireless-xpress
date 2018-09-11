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
#import <CoreBluetooth/CoreBluetooth.h>

/**
 * @addtogroup bgx_ota BGX OTA Update
 *
 * bgx_ota_update contains a class (BGX_OTA_Updater) and other datatypes that are used to perform
 * an OTA update operation (i.e. Firmware Update) of a Silicon Labs BGX device.
 *
 * To perform a firmware update, your app should:
 * 1. Get the CBPeripheral object of the BGX to be updated.
 * 2. Get the unique device id for the peripheral. You can use the BGXpressManager class method
 *    + (NSString *)uniqueIDForPeripheral:(CBPeripheral *)peripheral for this if needed.
 * 3. Create an instance of BGX_OTA_Updater.
 * 4. Setup Key Value Observing (KVO) on this instance to drive your user interface. You probably want to observe operationInProgress, ota_step, and upload_progress.
 * 5. Call - (void)updateFirmwareWithImageAtPath:(NSString *)path2FWImage withVersion:(NSString *)version.
 * 6. When the update is complete, release your instance of BGX_OTA_Updater.
 * 
 * @{
 */

/**
 * @enum ota_step_t
 *
 * This enum indicates the steps that occur during an OTA operation.
 */
typedef NS_ENUM(int, ota_step_t) {
   ota_step_no_ota ///< no OTA update is in progress.
  ,ota_step_init ///< An OTA update has been started but no action has happened yet.
  ,ota_step_scan ///< Scanning for peripheral.
  ,ota_step_connect ///< Connecting to the BGX being updated.
  ,ota_step_find_services ///< Discovering services for the peripheral.
  ,ota_step_find_characteristics ///< Discovering characteristics for services.
  ,ota_step_upload_no_response ///< Performing an OTA update without write verification.
  ,ota_step_upload_with_response ///< Performing an OTA update with write verification.
  ,ota_step_upload_finish ///< Finished uploading gbl image. Performing finishing operations.
  ,ota_step_end ///< This value indicates that the update operation is at an end.

  ,ota_step_error /// < An error has occurred during the OTA.

  ,ota_max_step ///< all step values greater or equal to this value are defined as invalid.

};

/**
 * @enum ota_operation_t
 *
 * This enum describes the overall state of the BGX_OTA_Updater object.
 */
typedef NS_ENUM(int, ota_operation_t) {

   ota_firmware_update_in_progress = 2 ///< An OTA Firmware update is in progress.
  ,ota_firmware_update_complete ///< An OTA firmware update is complete.
  ,ota_no_operation_in_progress ///< No OTA update operation is in progress.

  ,ota_max_operations ///< all operation values greater or equal to this value are defined as invalid.
};

/**
 * Performs OTA operations on a BGX device.
 *
 * The BGX_OTA_Updater class performs OTA operations on a BGX device to update the firmware on the BGX.
 * An iOS app performing an OTA update should observe the properties of this object in order to track
 * the progress of the OTA operation and present a user interface to the user if desired.
 *
 *
 * @remark When the OTA is complete, this object should be discarded.
 */
@interface BGX_OTA_Updater : NSObject <CBCentralManagerDelegate, CBPeripheralDelegate> {

  __strong CBPeripheral * _peripheral;
  float _upload_progress;
  ota_operation_t _operationInProgress;
  ota_step_t  _ota_step;
}

/**
 * Initialize a BGX OTA instance for the specified BGX peripheral.
 *
 * @param peripheral The BGX Peripheral to update.
 * @param bgx_device_id description of the param
 *
 * @returns An instance of BGX_OTA_Updater.
 */
- (id)initWithPeripheral:(CBPeripheral *)peripheral bgx_device_uuid:(NSString *)bgx_device_id;

/**
 * Update the firmware using the specified image and bundle ID.
 *
 * @param path2FWImage The path to the firmware image to be used for the update.
 * The update will be finished when the opeartionInProgress is ota_firmware_update_complete.
 * At that point, you can release the instances of the BGX_OTA_Updater.
 *
 * @param version This can be one of three values: 'release', a specific version number, or nil.
 * The value passed should match what you are trying to install. If you are installing the latest
 * release from DMS, then pass the string 'release'. If you are installing DMS firmware other than
 * the latest release, then pass a version number. Pass nil if you are installing firmware from a
 * source other than DMS or wish to suppress analytics.
 */
- (void)updateFirmwareWithImageAtPath:(NSString *)path2FWImage withVersion:(NSString *)version;

/** You may observe these properties to populate a user interface if desired.
 */
@property (nonatomic, readonly) ota_operation_t operationInProgress;
@property (nonatomic, readonly) ota_step_t ota_step;

/// Indicates the progress of gbl upload. Pertinent only during ota_step_upload_with_response and ota_step_upload_no_response.
@property (nonatomic, readonly) float upload_progress;

@end

/** @} */

/*
 * Copyright 2018-2019 Silicon Labs
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

package com.silabs.bgxpress;

/**
 * This is the OTA status that can be used to
 * drive a user interface as part of the
 * OTA_STATUS_MESSAGE.
 */
public enum OTA_Status {
    Invalid             /**< Should never see this. It indicates an error has occured */
    ,Idle               /**< No OTA is happening. */
    ,Password_Required  /**< a password is required for OTA. */
    ,Downloading        /**< the firmware image is being downloaded through DMS. */
    ,Installing         /**< the firmware image is being sent to the BGX device. */
    ,Finishing          /**< the firmware image has been written and the bgx is being commanded to load it. */
    ,Finished           /**< the BGX has acknowledged the command to load the firmware. The BGX is being rebooted. */
    ,Failed             /**< The OTA operation has failed. */
    ,UserCanceled       /**< the intent ACTION_OTA_CANCEL has been received and the OTA operation is being canceled */
}


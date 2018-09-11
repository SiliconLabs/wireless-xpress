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


#ifndef UUID_H
#define UUID_H

// UUIDs
// Normal UUIDs for iOS and reversed for FM


//SIG
#define CHARACTERISTIC_FIRMWARE_REVISION_STRING_UUID    @"2A26"
#define CHARACTERISTIC_MODEL_STRING_UUID                @"2A24"
#define SERVICE_DEVICE_INFORMATION_UUID                 @"180A"

//BGXSS

#define SERVICE_BGXSS_UUID                              @"331a36f5-2459-45ea-9d95-6142f0c4b307"

#define CHARACTERISTIC_SS_PER_RX_UUID                   @"a9da6040-0823-4995-94ec-9ce41ca28833"

#define CHARACTERISTIC_SS_PER_TX_UUID                   @"a73e9a10-628f-4494-a099-12efaf72258f"

#define CHARACTERISTIC_SS_MODE_UUID                     @"75a9f022-af03-4e41-b4bc-9de90a47d50b"


//OTA

#define SERVICE_OTA_UPGRADE_UUID                        @"b2e7d564-c077-404e-9d29-b547f4512dce"

#define CHARACTERISTIC_OTA_UPGRADE_CONTROL_POINT_UUID   @"48cbe15e-642d-4555-ac66-576209c50c1e"

#define CHARACTERISTIC_OTA_DATA_UUID                    @"db96492d-cf53-4a43-b896-14cbbf3bf4f3"

#define CHARACTERISTIC_OTA_APP_INFO_UUID                @"ddcc1893-3e58-45a8-b9e5-491b7279d870"


#define SLAB_OTA_SERVICE_UUID                           @"169b52a0-b7fd-40da-998c-dd9238327e55"
#define SLAB_OTA_CONTROL_CHARACTERISTIC_UUID            @"902ee692-6ef9-48a8-a430-5212eeb3e5a2"
#define SLAB_OTA_DATA_CHARACTERISTIC                    @"503a5d70-b443-466e-9aeb-c342802b184e"
#define SLAB_OTA_DEVICE_ID_CHARACTERISTIC               @"12e868e7-c926-4906-96c8-a7ee81d4b1b3"

#define SLAB_OTA_BOOTLOADER_VERSION_CHARACTERISTIC      @"25f05c0a-e917-46e9-b2a5-aa2be1245afe"
#define SLAB_OTA_APPLOADER_VERSION_CHARACTERISTIC       @"4f4a2368-8cca-451e-bfff-cf0e2ee23e9f"
#define SLAB_OTA_VERSION_CHARACTERISTIC                 @"4cc07bcf-0868-4b32-9dad-ba4cc41e5316"
#define SLAB_OTA_APPLICATION_VERSION_CHARACTERISTIC     @"0d77cc11-4ac1-49f2-bfa9-cd96ac7a92f8"

#endif //UUID_H

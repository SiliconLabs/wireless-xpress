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

#ifndef NotificationNames_h
#define NotificationNames_h


/** Sent when the user chooses tutorial from the drawer menu.
    Received by the AppDelegate which starts the tutorial.
 */
extern NSString * StartTutorialNotificationName;

/** Sent when the user cancels or finishes the tutorial.
    Received by the AppDelegate and used to shut down the tutorial
    and dispose the resources.
 */
extern NSString * CancelTutorialNotificationName;

/** Sent when a BGX device is connected, or if you are running
    in the simulator when the user selects one of the fake devices.
    Used to control segue into the device details view controller.
    Object is a BGXDevice.
 */
extern NSString * ConnectedToDeviceNotitficationName;

/** Sent to indicate that data was received from the BGX device.
    The AppDelegate is the BGXpressDelegate which receives the data
    and posts this notification with the data as the object for the notification.
 */
extern NSString * DataReceivedNotificationName;

/** Sent to indicate that the bus mode of the BGX Device has changed.
 The object attached to the notification is the BGXDevice which changed.
 */
extern NSString * BusModChangedNotificationName;

/** Posted by the AppDelegate to indicate that the list of BGXDevices has changed.
 */
extern NSString * DeviceListChangedNotificationName;

/** These notifications are used by the tutorial. */
extern NSString * TutorialConnectToDeviceNotificationName;

extern NSString * TutorialStep3NotificationName;

extern NSString * TutorialStep6NotificationName;

extern NSString * TutorialStep6SendDataNotificationName;

extern NSString * TutorialStep9DisconnectNotificationName;

/** This is posted to indicate that Firmware Updates are possible because a
    device is connected. Used to cause the update firmware drawer item to be
    shown.
 */
extern NSString * EnableFirmwareUpdateNotificationName;

/** This is posted to indicate that Firmware Updates are not possible because
 no device is connected. Used to cause the update firmware drawer item to be
 hidden.
 */
extern NSString * DisableFirmwareUpdateNotificationName;

/** Posted to indicate that the user has chosen to update the firmware.
    Firmware updates use a gbl file which was current at the time the app was
    built.
 */
extern NSString * UpdateFirmwareNotificationName;

/** Posted to indicate that the firmware update is finished.
 */
extern NSString * UpdateCompleteNotificationName;

/** Posted to indicate the connection state of the BGXpressManager has changed.
 */
extern NSString * DeviceStateChangedNotificationName;

extern NSString * CleanupUpdateUIObserverNotificationName;

/** Posted to indicate that the user selected the about item in the drawer menu.
 */
extern NSString * AboutItemNotificationName;

/** Posted to indicate that the user pressed the Done button on the firmware release notes view controller.
 */
extern NSString * FirmwareReleaseNotesShouldCloseNotificationName;

#endif /* NotificationNames_h */

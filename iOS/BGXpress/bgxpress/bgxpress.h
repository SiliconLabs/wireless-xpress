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
#import <bgxpress/bgx_ota_update.h>
#import <bgxpress/bgx_dms.h>
#import <bgxpress/SafeType.h>

/**
 * @addtogroup bgx_framework BGX Framework
 *
 * The Silicon Labs BGX device provides a bridge between Bluetooth Low Energy (BLE) and Serial communication.
 * BGXpress.framework makes it easier to write an iOS app that interacts with BGX. It supports discovery of
 * BGX devices, connecting and disconnecting, setting and detecting the bus mode of the BGX, sending and receiving
 * data, and OTA (Over The Air) Firmware Updates of the BGX accessed through the Silicon Labs DMS (Device Management Service).
 *
 * @remark BGXpress.framework is designed for use with iOS.
 * @remark BGXpress.framework is currently designed for connecting to one BGX at a time.
 *
 * @{
 */

/**
 * Possible values for the connection state of the BGXpressManager.
 */
typedef enum
{
    DISCONNECTED,       ///< The BGXpressManager is not connected to any BGX device and is not scanning.
    SCANNING,           ///< The BGXpressManager is scanning for BGX devices.
    CONNECTING,         ///< The BGXpressManager is connecting to a BGX device.
    INTERROGATING,      ///< The BGXpressManager has connected to a BGX device and is discovering bluetooth services and characteristics.
    CONNECTED,          ///< The BGXpressManager is connected to a BGX device.
    DISCONNECTING,      ///< The BGXpressManager is in the process of disconnecting from a BGX device.
    CONNECTIONTIMEDOUT  ///< A connection that was in progress has timed out.
} ConnectionState;

/**
 * Values of the bus mode.
 */
typedef enum
{
    UNKNOWN_MODE = 0,       ///< an invalid mode used to indicate that the
                            ///< mode has not been determined
    STREAM_MODE,            ///< send and receive serial data to the device to
                            ///< which the BGX is connected
    LOCAL_COMMAND_MODE,     ///< data received over the serial lines is
                            ///< processed as a command by the BGX and results
                            ///< returned over the serial lines
    REMOTE_COMMAND_MODE,    ///< data received over bluetooth is processed as
                            ///< a command by the BGX and results returned
                            ///< over bluetooth
    UNSUPPORTED_MODE        ///< an invalid mode state
} BusMode;

/**
 * The NSBGXErrorDomain is used as an error domain for NSError objects passed by BGXpress.framework.
 */
extern NSErrorDomain NSBGXErrorDomain;

/**
 * Delegate class for the BGXpressManager object.
 *
 * Your application should supply an object conforming to the BGXpressDelegate protocol
 * which has a lifecycle similar to the instance of `BGXpressManager` you create.
 * The BGXCommander example app uses the AppDelegate for this purpose.
 * The various methods in this protocol are called in order to indicate changes to the Bluetooth state, ConnectionState, BusMode,
 * when devices are discovered, when data is read or data is written.
 */
@protocol BGXpressDelegate <NSObject>
@required
/**
 * Detect connection state changes.
 *
 * Delegate method advising when the connection state has changed and the new
 * connection state
 *
 * @param newConnectionState ConnectionState of the current connection
 */
- (void)connectionStateChanged:(ConnectionState)newConnectionState;

/**
 * Detect bus mode changes.
 *
 * Delegate method called when the bus mode changes or is read.
 * If you call the BGXpressManager method -(BOOL)readBusMode, this delegate will
 * be called with the bus mode that was read whether or not it was
 * different than the previous bus mode.
 *
 * @param newBusMode the new bus mode for the connection
 */
- (void)busModeChanged:(BusMode)newBusMode;

/**
 * Delegate method is called when data is received.
 *
 * @param newData updated values
 */
- (void)dataRead:(NSData *)newData;

/**
 * Delegate method used to advise that the data has been written.
 */
- (void)dataWritten;

/**
 * Delegate method called when a device is discovered.
 * Check the devicesDiscovered property of BGXpressManager for the
 * list of device that have been discovered.
 *
 */
- (void)deviceDiscovered;

@optional
/**
 * Notify application about bluetooth state changes.
 *
 * Passes the notifications from the CBManager through so that the calling
 * app will know when to start/stop scanning (you get an error when you start
 * scanning before you we get the bluetooth state change notification saying that
 * bluetooth is on which seems like a flaw in the design of this API).
 *
 * @param state The state of the CBManager which in theory the calling
 * application shouldn't have to know about.
 */
- (void)bluetoothStateChanged:(CBManagerState)state;

@end

/**
 * This class is used to interact with a BGX device.
 *
 * Create one of these objects in your application and store it in a place where it will be retained while your application is running.
 * The BGXCommander example app uses the AppDelegate class to store an instance of the BGXpressManager class.
 * Your app should do this or something that is equivalent. We recommend against storing it as a property of a UIViewController instance
 * because view controllers may be created and destroyed during the lifecycle of your app.
 *
 * @remark Most of the methods of this class are asynchronous. To determine that an operation has occurred, rely on the BGXpressDelegate.
 * @remark Operations may fail because of an incompatible state of this object (e.g. connectToDevice:), or the state of the Bluetooth hardware (e.g. startScan).
 *
 */
@interface BGXpressManager : NSObject <CBCentralManagerDelegate,CBPeripheralDelegate>

@property(nonatomic, weak) id <BGXpressDelegate> delegate;
@property(nonatomic, strong) NSString *modelNumber;
@property(nonatomic, strong) NSMutableArray *devicesDiscovered;
@property(nonatomic, assign) ConnectionState connectionState;
@property(nonatomic, assign) BusMode busMode;

/**
 * Call this method to start scanning for devices.
 *
 * @return Boolean value to indicate if scanning will start.
 *
 * @remark Calling this method will clear the contents of the devicesDiscovered mutable array property.
 */
- (BOOL)startScan;

/**
 * Call this method to stop scanning for devices.
 *
 * @return Boolean value to indicate if scanning will stop.
 */
- (BOOL)stopScan;

/**
 * Method to connect to a device
 *
 * @param deviceName The name of the device you wish to connect to
 * @returns Boolean value to indicate if there has been a successful connection to the device
 */
- (BOOL)connectToDevice:(NSString *)deviceName;

/**
 * Method used to disconnect from a device.
 */
- (BOOL)disconnect;


/**
 * Method used to read the BusMode for a device eg Stream, Local or Remote commands
 *
 * @returns Boolean value, FALSE if not connected else TRUE.
 */
- (BOOL)readBusMode;


/**
 * Method used to write new bus mode for device
 *
 * @param newBusMode The new BusMode for the device
 * @return Boolean value, FALSE if not connected or BusMode not changed. TRUE if BusMode written.
 */
- (BOOL)writeBusMode:(BusMode)newBusMode;


/**
 * Method to see if you can write to the device.
 *
 * @return Boolean value, TRUE if device is connected and no data waiting to be written to the device, else FALSE.
 */
- (BOOL)canWrite;

/**
 * Method used to write data to a device.
 *
 * @param data Data to be writted to device.
 * @return Boolean value advising if the data has been written to the device or not.
 */
- (BOOL)writeData:(NSData *)data;

/**
 * Method used to write a string to device.
 *
 * @param string String value to be writted to device.
 * @return Boolean value advising if the string has been written to the device or not.
 */
- (BOOL)writeString:(NSString *)string;

/**
 * Method used to send commands to the device
 *
 * @param command Command
 * @param args Arguments
 * @return Boolean value advising if the command has been written to the device or not.
 */
- (BOOL)sendCommand:(NSString *)command args:(NSString *)args;

/**
 * Returns the device unique id.
 *
 * @return NSString with the unique id of the device. Nil on error.
 */
- (NSString *)device_unique_id;

/**
 * Get the unique ID for the specified peripheral.
 *
 * @param peripheral the peripheral to be read.
 *
 * This call assumes that the services and characteristics have been discovered
 * and that the device id characteristic has already been read once.
 *
 * @return NSString with the unique id of the device. Nil on error.
 */
+ (NSString *)uniqueIDForPeripheral:(CBPeripheral *)peripheral;

@end

/** @} */

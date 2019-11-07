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


#import <Foundation/Foundation.h>
#import <CoreBluetooth/CoreBluetooth.h>

/**
 * @addtogroup BGXDevice BGXDevice
 * The BGXDevice class is used to represent the BGX device. It contains a group
 * of properties as well as methods used to perform appropriate actions on a
 * BGXDevice.
 *
 * Supports two delegates, deviceDelegate and serialDelegate, as well as KVO.
 * @{
 */

NS_ASSUME_NONNULL_BEGIN

/**
 * DeviceState values
 */
typedef NS_ENUM(NSUInteger, DeviceState) {
     Interrogating  ///< The BGXDevice has connected and Bluetooth services and characteristics are being discovered.
    ,Disconnected   ///< The BGXDevice is not connected (to this iOS device).
    ,Connecting     ///< The BGXDevice is in the process of connecting.
    ,Connected      ///< The BGXDevice is connected.
    ,Disconnecting  ///< The BGXDevice is in the process of disconnecting.
};

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
    REMOTE_COMMAND_MODE,    ///< data received over Bluetooth is processed as
    ///< a command by the BGX and results returned
    ///< over Bluetooth
    UNSUPPORTED_MODE        ///< an invalid mode state
} BusMode;

@class BGXDevice;

typedef void (^busmode_write_completion_handler_t)(BGXDevice *, NSError * err);

/**
 * BGXSerialDelegate protocol
 *
 * The BGXSerialDelegate is used to perform serial read/write operations
 * with the BGX.
 */
@protocol BGXSerialDelegate
@required
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
- (void)busModeChanged:(BusMode)newBusMode forDevice:(BGXDevice *)device;

/**
 * Delegate method is called when data is received.
 *
 * @param newData updated values
 */
- (void)dataRead:(NSData *)newData forDevice:(BGXDevice *)device;

/**
 * Delegate method used to advise that the data has been written.
 */
- (void)dataWrittenForDevice:(BGXDevice *)device;
@end

/**
 * BGXDeviceDelegate protocol is used to be notified of state changes
 * and device-level events.
 */
@protocol BGXDeviceDelegate
@optional

/**
 * The device was connecting but the connection operation failed
 * to finish within the timeout period. The device is probably
 * disconnected as a result.
 *
 * @param device The device whose connection timed out.
 */
- (void)connectionTimeoutForDevice:(BGXDevice *)device;

/**
 * The value of the deviceState variable changed. This could mean
 * that the device connected, disconnected, and so on.
 * The typical flow is:
 * Connect operation: Disconnected -> Connecting -> Interrogating -> Connected.
 *
 * Disconnect operation: Connected -> Disconnecting -> Disconnected
 *
 * @param device The device whose state changed.
 */
- (void)stateChangedForDevice:(BGXDevice *)device;

/**
 * The RSSI value changed for the device.
 *
 * @param device The device for which the RSSI changed.
 */
- (void)rssiChangedForDevice:(BGXDevice *)device;
@end

@class BGXpressScanner;

@interface BGXDevice : NSObject<CBPeripheralDelegate> {
    DeviceState _deviceState;
    BusMode _busMode;
    BOOL _writeWithResponse;
}

/**
 * Method to connect to the device. Operation happens asynchronously. Observe
 * the deviceState property to determine the outcome of the operation.
 *
 * @returns BOOL value to indicate if the connection operation started successfully.
 *
 */
- (BOOL)connect;


/**
 * Method to disconnect to the device. Operation happens asynchronously. Observe
 * the deviceState property to determine the outcome of the operation.
 *
 * @returns BOOL value to indicate if the disconnection operation started successfully.
 */
- (BOOL)disconnect;

/**
 * Method used to read the BusMode for a device e.g., Stream, Local or Remote commands.
 * This operation occurs asynchronously. Observe the changes to the busMode property.
 *
 * @returns BOOL value, YES to indicate that the read operation could be initiated.
 * NO if the read operation could not be started.
 *
 */
- (BOOL)readBusMode;


/**
 * *Deprecated* Use **writeBusMode: password: completionHandler:** instead.
 * Method used to write new bus mode for device. Operation occurs asynchronously.
 *
 * @param newBusMode The new BusMode for the device
 * @return BOOL value, YES if BusMode write operation could be started.
 */
- (BOOL)writeBusMode:(BusMode)newBusMode;
/**
 * Method used to write new bus mode for device. Operation occurs asynchronously.
 * You may get an error if the password you pass is wrong.
 * @param newBusMode The new BusMode for the device
 * @param password The password for the bus mode characteristic. Pass nil for no password.
 * @param completionHandler This will be called when the operation completes. You will get an error as a
 * parameter to the completionHandler if the password is wrong or if you pass nil when a password is required.
 */
- (BOOL)writeBusMode:(BusMode)newBusMode password:(NSString * _Nullable )password completionHandler:(busmode_write_completion_handler_t)completionHandler;

/**
 * Method to see if you can write to the device.
 *
 * @return BOOL value, YES if device is connected and no data waiting to be written to the device, else NO.
 */
- (BOOL)canWrite;

/**
 * Method used to write data to a device.
 *
 * @param data Data to be written to device.
 * @return BOOL value advising if the data has been written to the device or not.
 */
- (BOOL)writeData:(NSData *)data;

/**
 * Method used to write a string to device.
 *
 * @param string String value to be written to device.
 * @return BOOL value advising if the string has been written to the device or not.
 */
- (BOOL)writeString:(NSString *)string;

/**
 * Method used to send commands to the device
 *
 * @param command Command
 * @param args Arguments
 * @return BOOL value advising if the command has been written to the device or not.
 */
- (BOOL)sendCommand:(NSString *)command args:(NSString *)args;

/**
 * Returns the device unique id.
 *
 * @return NSString with the unique id of the device. Nil on error.
 */
- (nullable NSString *)device_unique_id;


/**
 * Printable name for a device state value.
 */
+ (NSString *)nameForDeviceState:(DeviceState)ds;

/**
 * Access to the CBPeripheral.
 */
- (CBPeripheral *)peripheral;

@property (nonatomic, weak) NSObject<BGXDeviceDelegate> * deviceDelegate;
@property (nonatomic, weak) NSObject<BGXSerialDelegate> * serialDelegate;
@property (nonatomic, readonly) NSString * name;
@property (nonatomic, readonly) NSNumber * rssi;
@property (nonatomic, readonly) NSUUID * identifier;
@property (nonatomic, readonly) DeviceState deviceState;
@property (nonatomic, readonly) BusMode busMode;
@property (nonatomic, strong) NSString *modelNumber;
@property (nonatomic, strong) NSString *firmwareRevision;
@property (nonatomic, strong) NSString *bootloaderVersion;

/**
 * YES = The device supports fastAck and will operate in fastAck mode.
 * NO  = The device does not support fastAck and will operate in legacy mode.
 */
@property (nonatomic, readonly) BOOL fastAck;

/**
 * YES = Writes occur using CBCharacteristicWriteWithResponse
 * NO = Writes occur using CBCharacteristicWriteWithoutResponse
 * @default = YES
 *
 * Note: If the device supports fastAck, writeWithoutResponse will always be used.
 *
 */
@property (nonatomic) BOOL writeWithResponse;

@end

NS_ASSUME_NONNULL_END

/** @} */

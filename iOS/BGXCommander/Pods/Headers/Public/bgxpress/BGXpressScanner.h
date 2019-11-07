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
 * @addtogroup BGXpressScanner BGXpressScanner
 *
 * BGXpressScanner is used to detect changes to the Bluetooth hardware
 * and scan for devices. It also contains a list of devices.
 *
 * It has a delegate property using the BGXpressScanDelegate protocol.
 *
 * @{
 */

@class BGXDevice;

NS_ASSUME_NONNULL_BEGIN

typedef NS_ENUM(NSUInteger, ScanState) {
     CantScan ///< Scanning cannot occur. Calls to startScan will fail. Wait for Bluetooth to be enabled.
    ,Idle ///< Not currently scanning.
    ,Scanning ///< Currently scanning.
};

/**
 * A protocol for keeping track of goings on with your BGXpressScanner.
 */
@protocol BGXpressScanDelegate <NSObject>

@optional

/**
 * bluetoothStateChanged
 * This is called to indicate that system state of Bluetooth changed.
 */
- (void)bluetoothStateChanged:(CBManagerState)state;

/**
 * This is called to indicate that the scanner has found a device.
 * You can access each device as its discovered (via the parameter)
 * or access them all using the devicesDiscovered property which
 * is an array of BGXDevice objects.
 */
- (void)deviceDiscovered:(BGXDevice *)device;

/**
 * This method is called when the scanState has changed.
 */
- (void)scanStateChanged:(ScanState)scanState;


@end


@interface BGXpressScanner : NSObject <CBCentralManagerDelegate> {
    ScanState _scanState;
}

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
 * Assign this property to be your scan delegate.
 */
@property (nonatomic, strong) NSObject<BGXpressScanDelegate> * delegate;

/**
 * This is an array of BGXDevice objects discovered by the scanner.
 * This property will be made read only and immutable in the future.
 */
@property(nonatomic, strong) NSMutableArray * devicesDiscovered;

/**
 * The current state of the scanner.
 */
@property (nonatomic, readonly) ScanState scanState;

@end

NS_ASSUME_NONNULL_END
/** @} */

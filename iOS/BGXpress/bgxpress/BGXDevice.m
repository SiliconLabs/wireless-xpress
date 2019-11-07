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

#import "BGXDevice.h"
#import "BGXpressScanner.h"
#import "BGXUUID.h"

@interface BGXpressScanner(CentralManagerAccess)
- (CBCentralManager *)centralManager;
@end

const NSTimeInterval kConnectionTimeout = 30.0f;
const NSTimeInterval kRSSIReadInterval = 15.0f;

@interface BGXDevice()

- (id)initWithCBPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner;

@end

@implementation BGXDevice
{
    __strong CBPeripheral * peripheral;
    __strong NSString * device_name;
    __strong NSNumber * _rssi;
    __strong BGXpressScanner * _scanner;
    __strong NSTimer * _connectionTimer;
    
    __strong CBCharacteristic *perRXchar;
    __strong CBCharacteristic *perTXchar;
    __strong CBCharacteristic *modeChar;
    __strong CBCharacteristic *otaCtlPointChar;
    __strong CBCharacteristic *otaDataChar;
    __strong CBCharacteristic *firRevStrChar;
    __strong CBCharacteristic *modelNumberChar;
    __strong CBCharacteristic *deviceUniqueIdenChar;
    
    __strong NSData *dataToWrite;
    NSRange range;
    
    __strong NSMutableData *dataBuffer;

    __weak NSTimer * rssiTimer;
    BOOL _acknowledgedWrites;
    
    
    NSUInteger _dataWriteSize;

    BOOL _fastAck;
    NSInteger _fastAckRxBytes;
    NSInteger _fastAckTxBytes;
    
    NSMutableArray * mBusModeCompletionHandlers;
}

const NSInteger kInitialFastAckRxBytes = 0x7FFF;

const NSUInteger kHandlerDefaultCapacity = 0x10;

+ (instancetype)deviceWithCBPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner
{
    return [[BGXDevice alloc] initWithCBPeripheral:peripheral advertisementData:advData rssi:rssi discoveredBy:scanner];
}

- (id)initWithCBPeripheral:(CBPeripheral *)peripheralParam advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner
{
    self = [super init];
    if (self) {
        device_name = [advData objectForKey:@"kCBAdvDataLocalName"];
        
        _scanner = scanner;
        peripheral = peripheralParam;
        peripheral.delegate = self;
        _rssi = rssi;
        _deviceState = Disconnected;
        _busMode = UNKNOWN_MODE;
        _connectionTimer = nil;
        rssiTimer = nil;
        _acknowledgedWrites = YES;
        _dataWriteSize = 0;
        _writeWithResponse = YES;
        _fastAck = NO;
        _fastAckRxBytes = 0;
        _fastAckTxBytes = 0;
        mBusModeCompletionHandlers = [NSMutableArray arrayWithCapacity:kHandlerDefaultCapacity];
        
    }
    return self;
}

- (BOOL)connect
{
    [self changeDeviceState:Connecting];
    
    [[_scanner centralManager] connectPeripheral:peripheral options:nil];
    _connectionTimer = [NSTimer scheduledTimerWithTimeInterval:kConnectionTimeout repeats:NO block:^(NSTimer * tmr){
        if ([self.deviceDelegate respondsToSelector: @selector(connectionTimeoutForDevice:)]) {
            [self.deviceDelegate connectionTimeoutForDevice:self];
        }
        [self disconnect];
    }];
    return YES;
}

- (BOOL)disconnect
{
    
    [_connectionTimer invalidate];
    if ((self.deviceState  == Connecting) || (self.deviceState  == Connected) || (self.deviceState  == Interrogating))
    {
        [self changeDeviceState:Disconnecting];

        [[_scanner centralManager]  cancelPeripheralConnection:peripheral];
        
        return YES;
    }
    else
    {
        return NO;
    }
}

- (NSUUID *)identifier
{
    return peripheral.identifier;
}

- (NSString *)name
{
    return device_name;
}

- (NSString *)description
{
    NSString * description = [NSString stringWithFormat:@"%@ rssi=%d id=%@ DeviceState=%@", self.name, self.rssi.intValue, self.identifier.UUIDString, [BGXDevice nameForDeviceState:self.deviceState] ];
    return description;
}

#pragma mark - Calls from Scanner

- (void)deviceDidConnect
{
    [_connectionTimer invalidate];
    _connectionTimer = nil;
    // set the device state and start discovering the services.
    [self changeDeviceState:Interrogating];
    [peripheral discoverServices:nil];
    
    rssiTimer = [NSTimer scheduledTimerWithTimeInterval:kRSSIReadInterval repeats:YES block:^(NSTimer * timer){
        if (Connected == self.deviceState || Interrogating == self.deviceState) {
            [self->peripheral readRSSI];
        }
    }];

}

- (void)deviceDidFailToConnect
{
    [_connectionTimer invalidate];
    _connectionTimer = nil;
    
    if ([self.deviceDelegate respondsToSelector: @selector(connectionTimeoutForDevice:)]) {
        [self.deviceDelegate connectionTimeoutForDevice:self];
    }
    
    [self changeDeviceState:Disconnected];
}

- (void)deviceDidDisconnect
{
    [_connectionTimer invalidate];
    _connectionTimer = nil;
    [rssiTimer invalidate];
    rssiTimer = nil;
    [self changeDeviceState:Disconnected];

    perRXchar = nil;
    perTXchar = nil;
    modeChar  = nil;
    firRevStrChar = nil;
    deviceUniqueIdenChar = nil;
    _dataWriteSize = 0;
    _busMode = UNKNOWN_MODE;
}



// Call this to change the device state from inside the class. Calls deviceDelegate as needed.
- (void)changeDeviceState:(DeviceState)ds
{
    [self willChangeValueForKey:@"deviceState"];
    if (_deviceState != ds) {
        NSLog(@"deviceState changing from %@ to %@", [BGXDevice nameForDeviceState:_deviceState], [BGXDevice nameForDeviceState:ds]);
    }
    _deviceState = ds;
    [self didChangeValueForKey:@"deviceState"];
    
    if ([self.deviceDelegate respondsToSelector:@selector(stateChangedForDevice:)]) {
        [self.deviceDelegate stateChangedForDevice:self];
    }
    
    if ( self.fastAck && Connected == _deviceState) {
        char bytes[3];
        assert(_fastAckRxBytes >=0 && _fastAckRxBytes <= kInitialFastAckRxBytes);

        bytes[0] = 0x00;
        bytes[1] = _fastAckRxBytes & 0xFF;
        bytes[2] = (_fastAckRxBytes & 0xFF00) >> 8;

        [self.peripheral writeValue:[NSData dataWithBytes:bytes length:3] forCharacteristic:perTXchar type:CBCharacteristicWriteWithoutResponse];
    }
    
}

#pragma mark - BGX Serial Methods

- (BOOL)canWrite
{
    if (Connected == self.deviceState) {
        
        if (_fastAck) {
            return _fastAckTxBytes > 0 ? YES : NO;
        }
        
        if (!self.writeWithResponse) {
            return self.peripheral.canSendWriteWithoutResponse;
        } else if (!dataToWrite) {
            return YES;
        }
    }

    return NO;
}

- (BOOL)readBusMode
{
    if (self.deviceState  != Connected)
    {
        return NO;
    }
    [peripheral readValueForCharacteristic:modeChar];
    
    return YES;
}

- (BOOL)writeBusMode:(BusMode)newBusMode password:(NSString * _Nullable )password completionHandler:(busmode_write_completion_handler_t)completionHandler
{
    [mBusModeCompletionHandlers addObject:completionHandler];

    if (self.deviceState != Connected)
    {
        return NO;
    }
    
    NSMutableData * md = [[NSMutableData alloc] initWithCapacity:[password lengthOfBytesUsingEncoding:NSASCIIStringEncoding] + 1];
    
    [md appendBytes:&newBusMode length:1];
    if (password) {
        unsigned char zero = 0;
        [md appendData: [password dataUsingEncoding:NSASCIIStringEncoding]];
        [md appendBytes:&zero length:1];
    }
    
    if (self.busMode != newBusMode)
    {
        [peripheral writeValue:md forCharacteristic:modeChar type:CBCharacteristicWriteWithResponse];
        return YES;
    }
    else
    {
        return NO;
    }
}

- (BOOL)writeBusMode:(BusMode)newBusMode
{
    if (self.deviceState != Connected)
    {
        return NO;
    }
    
    if (self.busMode != newBusMode)
    {
        [peripheral writeValue:[NSData dataWithBytes:&newBusMode length:1] forCharacteristic:modeChar type:CBCharacteristicWriteWithResponse];
        return YES;
    }
    else
    {
        return NO;
    }
}

- (BOOL) writeData:(NSData *) data
{
    if ((self.deviceState != Connected) || dataToWrite)
    {
        return NO;
    }
    
    if ( 0 == _dataWriteSize) {
        NSUInteger writeWithout = [self.peripheral maximumWriteValueLengthForType: CBCharacteristicWriteWithoutResponse];
        NSUInteger writeWith = [self.peripheral maximumWriteValueLengthForType: CBCharacteristicWriteWithResponse];
        
        if (writeWithout < writeWith) {
            _dataWriteSize = writeWithout;
        } else {
            _dataWriteSize = writeWith;
        }
    }
    
    dataToWrite = [NSData dataWithData:data];
    range.location = 0;
    [self writeChunkOfData];
    
    return YES;
}

- (void) writeChunkOfData
{
    if (!_fastAck || _fastAckTxBytes > 0) {
        NSAssert(0 != _dataWriteSize, @"Invalid write size");
        range.length = dataToWrite.length - range.location;
        
        NSUInteger maxWriteSz = self.fastAck ? MIN(_fastAckTxBytes, _dataWriteSize) : _dataWriteSize;
        
        if (range.length > maxWriteSz)
        {
            range.length = maxWriteSz;
        }
        NSData * data2Write = [dataToWrite subdataWithRange:range];
        if (data2Write != nil) {
        
            if (self.fastAck) {
                [peripheral writeValue:data2Write forCharacteristic:perRXchar type: CBCharacteristicWriteWithoutResponse];
            } else {
                [peripheral writeValue:data2Write forCharacteristic:perRXchar type: self.writeWithResponse ? CBCharacteristicWriteWithResponse : CBCharacteristicWriteWithoutResponse];
            }
            range.location += range.length;
            
            if (_fastAck) {
                _fastAckTxBytes -= data2Write.length;
//                NSLog(@"_fastAckTxBytes: %ld (subtracted %ld)", (long int) _fastAckTxBytes, data2Write.length);
            }
        }
    }
}

- (BOOL) writeString:(NSString *) string
{
    return [self writeData:[string dataUsingEncoding:NSUTF8StringEncoding]];
}

- (BOOL) sendCommand:(NSString *) command args:(NSString *) args
{
    return [self writeString:[NSString stringWithFormat:@"%@ %@\r\n", command, args]];
}

#pragma mark - CBPeripheralDelegate Methods

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(NSError *)error
{
    NSLog(@"peripheral: %@ didDiscoverServices", peripheral.name);
    if (!error)
    {
        perRXchar = nil;
        perTXchar = nil;
        modeChar  = nil;
        firRevStrChar = nil;
        deviceUniqueIdenChar = nil;
        
        for (CBService *service in [peripheral services])
        {
            [peripheral discoverCharacteristics:nil forService:service];
        }
    }
}

- (void) peripheral:(CBPeripheral *)peripheral didDiscoverCharacteristicsForService:(CBService *)service error:(NSError *)error
{
    NSLog(@"peripheral: %@ didDiscoverCharacteristicsForService: %@", peripheral.name, service.UUID.UUIDString);
    if (!error)
    {
        if ([service.UUID isEqual:[CBUUID UUIDWithString:SERVICE_DEVICE_INFORMATION_UUID]])
        {
            for (CBCharacteristic *characteristic in service.characteristics)
            {
                if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_MODEL_STRING_UUID]])
                {
                    
                    modelNumberChar = characteristic;
                } else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_FIRMWARE_REVISION_STRING_UUID]]) {
                    firRevStrChar = characteristic;
                    [peripheral readValueForCharacteristic:firRevStrChar];
                }
            }
        } else {
            for (CBCharacteristic *characteristic in service.characteristics)
            {
                if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_RX_UUID]])
                {
                    perRXchar = characteristic;
                    
                    if (perRXchar.properties & CBCharacteristicPropertyNotify) {
                        [peripheral setNotifyValue:YES forCharacteristic:perRXchar];
                        _fastAck = YES;
                        NSLog(@"_fastAck: YES");
                        _writeWithResponse = NO;
                        _fastAckRxBytes = kInitialFastAckRxBytes;
//                        NSLog(@"_fastAckRxBytes: %ld (initial)", (long int)_fastAckRxBytes);

                    }
                }
                else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_TX_UUID]])
                {
                    perTXchar = characteristic;
                    //                NSLog(@"perTXchar discovered");
                    [peripheral setNotifyValue:YES forCharacteristic:perTXchar];
                }
                else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_MODE_UUID]])
                {
                    modeChar = characteristic;
                    //                NSLog(@"modeChar discovered");
                    [peripheral setNotifyValue:YES forCharacteristic:modeChar];
                }
                else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_DEVICE_ID_CHARACTERISTIC]])
                {
                    deviceUniqueIdenChar = characteristic;
                    [peripheral readValueForCharacteristic:deviceUniqueIdenChar];
                }
            }
        }
        
        
        
        if ( (perRXchar != nil) && (perTXchar != nil) && (modeChar != nil) && (modelNumberChar != nil))
        {
            [peripheral readValueForCharacteristic:modelNumberChar];
        }
    }
}


- (void) peripheral:(CBPeripheral *)peripheral didUpdateValueForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error
{
    if (error) {
        
        [self disconnectWithError:error];
        return;
    }
    
    if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_RX_UUID]]) {
        
        
        
        // fast-ack backchannel data received.
        if (!self.fastAck) {
            NSLog(@"Warning: fastAck received but fastAck is not enabled.");
        }
        
        NSUInteger length = [characteristic.value length];
        
        if (3 == length) {
            char bytes[3];
            memcpy(bytes, characteristic.value.bytes, length);
            BOOL fNeedToCallWriteChunkOfData = NO;
            uint8_t opcode = bytes[0];
            uint16_t txbytes;
            memcpy(&txbytes, bytes + 1, 2);
            
            switch (opcode) {
                case 0x00: // initial credit value
                    _fastAckTxBytes = txbytes;
//                    NSLog(@"_fastAckTxBytes: %ld", (long int) _fastAckTxBytes);
                    break;
                case 0x01: // update credit
                    if (_dataWriteSize > 0 && _fastAckTxBytes == 0) {
                        fNeedToCallWriteChunkOfData = YES;
                    }
                    _fastAckTxBytes += txbytes;
//                    NSLog(@"_fastAckTxBytes: %ld (added %ld)", (long int) _fastAckTxBytes, (long int) txbytes);
                    break;
            }
            
            if (fNeedToCallWriteChunkOfData) {
                dispatch_async(dispatch_get_main_queue(), ^{ [self writeChunkOfData]; });
            }
        }

    } else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_FIRMWARE_REVISION_STRING_UUID]]) {

        NSString * rawVersionString =  [[NSString alloc] initWithData:characteristic.value encoding:NSUTF8StringEncoding];
        NSArray * comps = [rawVersionString componentsSeparatedByString:@"-"];
        // there should be three. Bootloader version should be the middle one.
        if (3 == comps.count) {
            
            NSString * comp0Ver = comps[0];
            NSString * versionPrefix = @"BGX13";
            if ([comp0Ver hasPrefix:versionPrefix]) {
                // it starts with BGX13 - find the first . (dot) and trim from there.
                
                NSRange r = [comp0Ver rangeOfString:@"."];
                if (NSNotFound != r.location) {
                    self.firmwareRevision = [comp0Ver substringFromIndex:r.location + r.length];
                }
            } else {
                NSLog(@"Warning: Unexpected firmware revision string format: %@", rawVersionString);
                self.firmwareRevision = rawVersionString;
            }
            
            self.bootloaderVersion = comps[1];
        } else {
            
            // Treat this as Invalid GATT handles.
            [self disconnectWithError:[NSError errorWithDomain:CBATTErrorDomain code:1 userInfo:@{}]];
            
        }
        
        
    } else  if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_TX_UUID]])
    {
        NSInteger rxDataLength = characteristic.value.length;
        if (self.fastAck) {
            _fastAckRxBytes -= rxDataLength;
//            NSLog(@"_fastAckRxBytes: %ld (subtracted %ld)", (long int)_fastAckRxBytes, (long int)rxDataLength);
        }
        
        [[self serialDelegate] dataRead:characteristic.value forDevice: self];

        if (self.fastAck) {
            dispatch_async(dispatch_get_main_queue(), ^{
                self->_fastAckRxBytes += rxDataLength;
//                NSLog(@"_fastAckRxBytes: %ld (added %ld)", (long int)_fastAckRxBytes, (long int)rxDataLength);
                assert(self->_fastAckRxBytes >=0 && self->_fastAckRxBytes <= kInitialFastAckRxBytes);
                char bytes[3];
                bytes[0] = 0x01;
                bytes[1] = rxDataLength & 0x00FF;
                bytes[2] = (rxDataLength & 0xFF00) >> 8;

                [self.peripheral writeValue:[NSData dataWithBytes:bytes length:3] forCharacteristic:self->perTXchar type:CBCharacteristicWriteWithoutResponse];
            });
        }
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_MODE_UUID]])
    {
        char mode;
        [characteristic.value getBytes:&mode length:1];
        
        BusMode bm = (BusMode) mode;
        if (_busMode != bm) {
            [self willChangeValueForKey:@"busMode"];
            _busMode = (BusMode)mode;
            [self didChangeValueForKey:@"busMode"];
            
            [[self serialDelegate] busModeChanged:self.busMode forDevice: self];
            
            if (self.deviceState  == Interrogating)
            {
                [self changeDeviceState: Connected];

            }
        }
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_MODEL_STRING_UUID]])
    {
        NSLog(@"%ld", (long int) characteristic.value.length);
//        _modelNumber = [[NSString alloc] initWithUTF8String:[characteristic.value bytes]];
        
        _modelNumber = [[NSString alloc] initWithData:characteristic.value encoding:NSUTF8StringEncoding];
        
        NSLog(@"Model Number: %@", _modelNumber);
        [peripheral readValueForCharacteristic:modeChar];
    }
}

- (void) peripheral:(CBPeripheral *)peripheral didWriteValueForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error
{
    
    if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_MODE_UUID]]) {
        busmode_write_completion_handler_t handler = [mBusModeCompletionHandlers firstObject];

        if (handler) {
            [mBusModeCompletionHandlers removeObject:handler];
            (handler)(self, error);
        }
    }
    else if (!error)
    {
        if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_RX_UUID]])
        {
            if (dataToWrite.length == range.location)
            {
                dataToWrite = nil;
                [[self serialDelegate] dataWrittenForDevice: self];
            }
            else if (dataToWrite.length > range.location)
            {
                [self writeChunkOfData];
            }
        }
    }
    else
    {

        NSLog(@"Error %@ writing to the characteristic (UUID): %@", [error description], characteristic.UUID);
    }
}

- (void)peripheralIsReadyToSendWriteWithoutResponse:(CBPeripheral *)peripheral
{

    if (dataToWrite) {
        if (dataToWrite.length == range.location) {
            dataToWrite = nil;
            [[self serialDelegate] dataWrittenForDevice:self];
        } else {
            [self writeChunkOfData];
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didReadRSSI:(NSNumber *)RSSI error:(nullable NSError *)error
{
    if (nil == error) {
        if ([_rssi intValue] != [RSSI intValue]) {
            [self willChangeValueForKey:@"rssi"];
            _rssi = RSSI;
            
            if ([[self deviceDelegate] respondsToSelector:@selector(rssiChangedForDevice:)]) {
                [[self deviceDelegate] rssiChangedForDevice:self];
            }
            
            [self didChangeValueForKey:@"rssi"];
        }
    } else {
        NSLog(@"Error reading RSSI: %@", [error description]);
    }
    
}

- (nullable NSString *)device_unique_id
{
    NSString * result = nil;
    if (deviceUniqueIdenChar) {
        NSData * dvalue = deviceUniqueIdenChar.value;
        NSMutableString * mr = [[NSMutableString alloc] initWithCapacity: 1 + (dvalue.length) * 2 ];
        if (dvalue) {
            for (int i = 0; i < dvalue.length; ++i) {
                unsigned char c = ((unsigned char *)dvalue.bytes)[i];
                [mr appendFormat:@"%02X", c];
            }
            
            result = [mr copy];
        }
    }
    return result;
}

#pragma mark -



- (CBPeripheral *)peripheral
{
    return peripheral;
}

- (void)setWriteWithResponse:(BOOL)writeWithResponse
{
    if (self.fastAck) {
        _writeWithResponse = NO;
        if (writeWithResponse) {
            NSLog(@"BGX Warning: Attempt to set writeWithResponse YES failed because fastAck is enabled for the current device.");
        }
    } else {
        _writeWithResponse = writeWithResponse;
    }
}

- (BOOL)writeWithResponse
{
    if (self.fastAck) {
        return NO;
    } else {
        return _writeWithResponse;
    }
}

/*
 * disconnectWithError
 *
 * Do not call this. The intention is that it will be
 * called from inside the framework when an unrecoverable
 * error is detected such as Invalid Gatt Handles.
 */
- (void)disconnectWithError:(NSError *)error
{
    [self disconnect];
    [[NSNotificationCenter defaultCenter] postNotificationName:@"Error" object:[NSError errorWithDomain:CBATTErrorDomain code:1 userInfo:@{}]];
}

#pragma mark - Misc Class Methods

+ (NSString *)nameForDeviceState:(DeviceState)ds
{
    switch (ds) {
        case Interrogating:
            return @"Interrogating";
            break;
        case Disconnected:
            return @"Disconnected";
            break;
        case Connecting:
            return @"Connecting";
            break;
        case Connected:
            return @"Connected";
            break;
        case Disconnecting:
            return @"Disconnecting";
            break;
    }
    return @"?";
}

@end

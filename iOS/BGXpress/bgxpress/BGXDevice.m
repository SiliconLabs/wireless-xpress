//
//  BGXDevice.m
//  BGXpress
//
//  Created by Brant Merryman on 10/11/18.
//  Copyright © 2018 Zentri. All rights reserved.
//

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
}



+ (instancetype)deviceWithCBPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner
{
    return [[BGXDevice alloc] initWithCBPeripheral:peripheral advertisementData:advData rssi:rssi discoveredBy:scanner];
}

- (id)initWithCBPeripheral:(CBPeripheral *)peripheralParam advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner
{
    self = [super init];
    if (self) {
        _scanner = scanner;
        peripheral = peripheralParam;
        peripheral.delegate = self;
        _rssi = rssi;
        _deviceState = Disconnected;
        _busMode = UNKNOWN_MODE;
        _connectionTimer = nil;
        rssiTimer = nil;
        
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
    return peripheral.name;
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
}



// Call this to change the device state from inside the class. Calls deviceDelegate as needed.
- (void)changeDeviceState:(DeviceState)ds
{
//    NSLog(@"%@ changing DeviceState: %@", [self description], [BGXDevice nameForDeviceState: ds] );
    [self willChangeValueForKey:@"deviceState"];
    _deviceState = ds;
    [self didChangeValueForKey:@"deviceState"];
    
    if ([self.deviceDelegate respondsToSelector:@selector(stateChangedForDevice:)]) {
        [self.deviceDelegate stateChangedForDevice:self];
    }
}

#pragma mark - BGX Serial Methods

- (BOOL) canWrite
{
    if (( Connected == self.deviceState ) && (!dataToWrite)) {
        return YES;
    }

    return NO;
}

- (BOOL) readBusMode
{
    if (self.deviceState  != Connected)
    {
        return NO;
    }
    [peripheral readValueForCharacteristic:modeChar];
    
    return YES;
}

- (BOOL)writeBusMode:(BusMode)newBusMode
{
    if (self.deviceState  != Connected)
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
    if ((self.deviceState  != Connected) || dataToWrite)
    {
        return NO;
    }
    dataToWrite = [NSData dataWithData:data];
    range.location = 0;
    [self writeChunkOfData];
    
    return YES;
}

- (void) writeChunkOfData
{
    range.length = dataToWrite.length - range.location;
    if (range.length > 20)
    {
        range.length = 20;
    }
    [peripheral writeValue:[dataToWrite subdataWithRange:range] forCharacteristic:perRXchar type:CBCharacteristicWriteWithResponse];
    range.location += range.length;
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
                    //                NSLog(@"perRXchar discovered");
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
        
        [[NSNotificationCenter defaultCenter] postNotificationName:@"Error" object:error];
        
        [self changeDeviceState:Disconnected];
        return;
    }
    
    if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_FIRMWARE_REVISION_STRING_UUID]]) {

        NSString * rawVersionString =  [[NSString alloc] initWithData:characteristic.value encoding:NSUTF8StringEncoding];
        NSArray * comps = [rawVersionString componentsSeparatedByString:@"-"];
        // there should be three. Bootloader version should be the middle one.
        if (3 == comps.count) {
            self.bootloaderVersion = comps[1];
            
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
        }
        
        
    } else  if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_TX_UUID]])
    {
        [[self serialDelegate] dataRead:characteristic.value forDevice: self];
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_MODE_UUID]])
    {
        char mode;
        [characteristic.value getBytes:&mode length:1];
        
        [self willChangeValueForKey:@"busMode"];
        _busMode = (BusMode)mode;
        [self didChangeValueForKey:@"busMode"];
        
        [[self serialDelegate] busModeChanged:self.busMode forDevice: self];
        
        if (self.deviceState  == Interrogating)
        {
            [self changeDeviceState: Connected];

        }
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_MODEL_STRING_UUID]])
    {
        _modelNumber = [[NSString alloc] initWithUTF8String:[characteristic.value bytes]];
        [peripheral readValueForCharacteristic:modeChar];
    }
}

- (void) peripheral:(CBPeripheral *)peripheral didWriteValueForCharacteristic:(CBCharacteristic *)characteristic error:(NSError *)error
{
    if (!error)
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

- (NSString *)device_unique_id
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

- (CBPeripheral *)peripheral
{
    return peripheral;
}



@end

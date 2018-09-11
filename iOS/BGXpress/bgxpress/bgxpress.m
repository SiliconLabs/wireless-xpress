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


#import "bgxpress.h"
#import "UUID.h"

#define HEADER_SIZE 9

NSErrorDomain NSBGXErrorDomain = @"BGX Error";

@interface BGXpressManager ()
@property (nonatomic, strong) NSTimer *connectionTimer;
@end

@implementation BGXpressManager
{
    //private variables
    CBCentralManager *manager;
    CBPeripheral     *per;
    CBCharacteristic *perRXchar;
    CBCharacteristic *perTXchar;
    CBCharacteristic *modeChar;
    CBCharacteristic *otaCtlPointChar;
    CBCharacteristic *otaDataChar;
    CBCharacteristic *firRevStrChar;
    CBCharacteristic *modelNumberChar;
    CBCharacteristic *deviceUniqueIdenChar;

    NSData *dataToWrite;
    NSRange range;
    
    NSMutableData *dataBuffer;
    
}

@synthesize delegate;
@synthesize devicesDiscovered;
@synthesize modelNumber;

- (void) centralManagerDidUpdateState:(CBCentralManager *)central
{
  if ([self.delegate respondsToSelector:@selector(bluetoothStateChanged:)]) {
    [self.delegate bluetoothStateChanged:central.state];
  }
}

- (id)init
{
    self = [super init];
    if (self)
    {
        //Initialize Central
        manager = [[CBCentralManager alloc] initWithDelegate:self queue:nil options:nil];
        devicesDiscovered = [NSMutableArray array];
        _connectionState  = DISCONNECTED;
        _busMode = UNKNOWN_MODE;
        dataToWrite = nil;
    }
    return self;
}

- (void)setConnectionState:(ConnectionState)connectionState
{
    _connectionState = connectionState;
    [[self delegate] connectionStateChanged:self.connectionState];
}

- (BOOL) startScan
{
    [devicesDiscovered removeAllObjects];
    
    if ((self.connectionState  == DISCONNECTED) || ([self disconnect]))
    {
      [manager scanForPeripheralsWithServices:@[ [CBUUID UUIDWithString:SERVICE_BGXSS_UUID] ] options:nil];
        self.connectionState  = SCANNING;
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}

- (BOOL) stopScan
{
    if (self.connectionState  == SCANNING)
    {
        [manager stopScan];
        self.connectionState  = DISCONNECTED;
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}

- (void) centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI
{
    NSUInteger deviceIndex = [devicesDiscovered indexOfObjectPassingTest:^BOOL(id obj, NSUInteger idx, BOOL *stop)
                              {
                                  if ([[(NSDictionary *)obj valueForKey:@"peripheral"] isEqual:peripheral])
                                  {
                                      *stop = YES;
                                      return YES;
                                  }
                                  return NO;
                              }];
    
    if (deviceIndex == NSNotFound)
    {
        NSString *deviceName = [advertisementData valueForKey:@"kCBAdvDataLocalName"];
        if (deviceName != NULL)
        {
            NSDictionary *device = [NSDictionary dictionaryWithObjectsAndKeys:peripheral, @"peripheral", advertisementData, @"adv", RSSI, @"RSSI", nil];
            [devicesDiscovered addObject:device];
            [[self delegate] deviceDiscovered];
        }
    }
}

- (BOOL)connectToDevice:(NSString *)deviceName
{
    if ((self.connectionState == DISCONNECTED) || (self.connectionState == SCANNING))
    {
        NSUInteger deviceIndex = [devicesDiscovered indexOfObjectPassingTest:^BOOL(id obj, NSUInteger idx, BOOL *stop)
                                  {
                                      if ([[[(NSDictionary *)obj valueForKey:@"adv"] valueForKey:CBAdvertisementDataLocalNameKey] isEqual:deviceName])
                                      {
                                          *stop = YES;
                                          return YES;
                                      }
                                      return NO;
                                  }];
        
        if (deviceIndex != NSNotFound)
        {
            [manager stopScan];
            [manager connectPeripheral:[[devicesDiscovered objectAtIndex:deviceIndex] valueForKey:@"peripheral"] options:nil];
            self.connectionState = CONNECTING;
            
            [self.connectionTimer invalidate];
            self.connectionTimer = [NSTimer scheduledTimerWithTimeInterval:10.0 target:self selector:@selector(connectionTimedOut) userInfo:nil repeats:false];
            
            return TRUE;
        }
        else
        {
            return FALSE;
        }
    }
    else
    {
        return FALSE;
    }
}

- (void)connectionTimedOut
{
    [self.delegate connectionStateChanged:CONNECTIONTIMEDOUT];
}

- (BOOL)disconnect
{
    [self.connectionTimer invalidate];
    if ((self.connectionState  == CONNECTING) || (self.connectionState  == CONNECTED) || (self.connectionState  == INTERROGATING))
    {
        if (per)
        {
            [manager cancelPeripheralConnection:per];
        }
        
        self.connectionState  = DISCONNECTING;
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}

- (void) centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral
{
    [self.connectionTimer invalidate];
    peripheral.delegate = self;
    per = peripheral;
    
    self.busMode = UNKNOWN_MODE;
    self.connectionState  = INTERROGATING;
    
    [peripheral discoverServices:nil];
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error
{
    [self.connectionTimer invalidate];
    NSLog(@"Failed to connect");
    [self.delegate connectionStateChanged:CONNECTIONTIMEDOUT];
}

- (void) centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error
{
//    [devicesDiscovered removeAllObjects];
    per = nil;
    perRXchar = nil;
    perTXchar = nil;
    modeChar  = nil;
    firRevStrChar = nil;
    deviceUniqueIdenChar = nil;
    self.connectionState  = DISCONNECTED;
}

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
              [per readValueForCharacteristic:deviceUniqueIdenChar];
            }
        }
        
        if ([service.UUID isEqual:[CBUUID UUIDWithString:SERVICE_DEVICE_INFORMATION_UUID]])
        {
            for (CBCharacteristic *characteristic in service.characteristics)
            {
                if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_MODEL_STRING_UUID]])
                {

                    modelNumberChar = characteristic;
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

/*
      UIAlertController * alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"Error", @"user alert title") message:[error localizedDescription] preferredStyle:UIAlertControllerStyleAlert];

      [alertController addAction: [UIAlertAction actionWithTitle:@"Dismiss" style:UIAlertActionStyleCancel handler:^(UIAlertAction * action){
        NSLog(@"Dissmiss error.");
      }]];

       [self presentViewController:alertController animated:YES completion:nil];
*/
      self.connectionState = DISCONNECTED;
      return;
    }
    if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_PER_TX_UUID]])
    {
        [[self delegate] dataRead:characteristic.value];
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_SS_MODE_UUID]])
    {
        char mode;
        [characteristic.value getBytes:&mode length:1];
        self.busMode = (BusMode)mode;
        [[self delegate] busModeChanged:self.busMode];
        
        if (self.connectionState  == INTERROGATING)
        {
            self.connectionState  = CONNECTED;
        }
    }
    else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:CHARACTERISTIC_MODEL_STRING_UUID]])
    {
        modelNumber = [[NSString alloc] initWithUTF8String:[characteristic.value bytes]];
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
                [[self delegate] dataWritten];
            }
            else if (dataToWrite.length > range.location)
            {
                [self writeChunkOfData];
            }
        }
    }
    else
    {
        //todo: deal with error
        NSLog(@"Error %@ writing to the characteristic (UUID): %@", [error description], characteristic.UUID);
    }
}

- (BOOL) canWrite
{
    if ((self.connectionState  == CONNECTED) && (!dataToWrite))
    {
        return TRUE;
    }
    else
    {
      NSString * cs = @"?";
      switch (self.connectionState) {
        case DISCONNECTED:
          cs = @"DISCONNECTED";
          break;
        case SCANNING:
          cs = @"SCANNING";
          break;
        case CONNECTING:
          cs = @"CONNECTING";
          break;
        case INTERROGATING:
          cs = @"INTERROGATING";
          break;
        case CONNECTED:
          cs = @"CONNECTED";
          break;
        case DISCONNECTING:
          cs = @"DISCONNECTING";
          break;
        case CONNECTIONTIMEDOUT:
          cs = @"CONNECTIONTIMEDOUT";
          break;
      }
      NSLog(@"canWrite will return false. ConnectionState is %@. There is %sdataToWrite (%@)", cs, dataToWrite ? "" : "no ", [dataToWrite description] );

        return FALSE;
    }
}

- (void) writeChunkOfData
{
    range.length = dataToWrite.length - range.location;
    if (range.length > 20)
    {
        range.length = 20;
    }
    [per writeValue:[dataToWrite subdataWithRange:range] forCharacteristic:perRXchar type:CBCharacteristicWriteWithResponse];
    range.location += range.length;
}

- (BOOL) readBusMode
{
    if (self.connectionState  != CONNECTED)
    {
        return FALSE;
    }
    [per readValueForCharacteristic:modeChar];
    
    return TRUE;
}

- (BOOL)writeBusMode:(BusMode)newBusMode
{
    if (self.connectionState  != CONNECTED)
    {
        return FALSE;
    }
    
    if ((self.busMode != LOCAL_COMMAND_MODE) && (newBusMode != LOCAL_COMMAND_MODE) && (self.busMode != newBusMode))
    {
        [per writeValue:[NSData dataWithBytes:&newBusMode length:1] forCharacteristic:modeChar type:CBCharacteristicWriteWithResponse];
        return TRUE;
    }
    else
    {
        return FALSE;
    }
}

- (BOOL) writeData:(NSData *) data
{
    if ((self.connectionState  != CONNECTED) || dataToWrite)
    {
        return FALSE;
    }
    dataToWrite = [NSData dataWithData:data];
    range.location = 0;
    [self writeChunkOfData];
    
    return TRUE;
}

- (BOOL) writeString:(NSString *) string
{
    return [self writeData:[string dataUsingEncoding:NSUTF8StringEncoding]];
}

- (BOOL) sendCommand:(NSString *) command args:(NSString *) args
{
    return [self writeString:[NSString stringWithFormat:@"%@ %@\r\n", command, args]];
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
    } else {
        // the characteristic isn't there. We could read it using the command API
        // but why bother?
    }
  }
  return result;
}

+ (NSString *)uniqueIDForPeripheral:(CBPeripheral *)peripheral
{
  NSString * result = nil;
  CBService * iservice;
  CBService * ota_service;

  if (!peripheral.services) {
    NSLog(@"Services have not been discovered for CBPeripheral instances so device id cannot be determined.");
  }

  for (iservice in peripheral.services) {
    if ([iservice.UUID isEqual: [CBUUID UUIDWithString:SLAB_OTA_SERVICE_UUID]]) {
      ota_service = iservice;
      break;
    }
  }

  if (ota_service) {
    for (CBCharacteristic * ichar in [ota_service characteristics]) {
      if ([ichar.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_DEVICE_ID_CHARACTERISTIC]]) {
        NSData * dvalue = ichar.value;
        if (dvalue) {
          NSMutableString * mr = [[NSMutableString alloc] initWithCapacity: 1 + (dvalue.length) * 2 ];
          for (int i = 0; i < dvalue.length; ++i) {
            unsigned char c = ((unsigned char *)dvalue.bytes)[i];
            [mr appendFormat:@"%02X", c];
          }

          result = [mr copy];
        }
        break;
      }
    }
  }

  return result;
}

@end

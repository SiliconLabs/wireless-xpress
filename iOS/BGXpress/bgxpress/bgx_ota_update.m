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

#import "bgx_ota_update.h"
#import "BGXUUID.h"
#import "bgxpress.h"

const NSUInteger kChunkSize = 244;

@interface BGX_OTA_Updater ()

- (BOOL)readyForUpdate;

- (void)writeChunkOfPayload;


@property (nonatomic, strong) CBCentralManager * cb_central_manager;

@property (nonatomic, strong) NSString * fw_image_path;
@property (nonatomic, strong) NSString * apploader_image_path;

@property (nonatomic, readwrite) ota_operation_t operationInProgress;
@property (nonatomic, readwrite) ota_step_t ota_step;
@property (nonatomic, readwrite) float upload_progress;

@property (nonatomic) dispatch_queue_t update_dispatch_queue;

@property (nonatomic) NSUInteger data_offset;
@property (nonatomic) NSUInteger amount2write;
@property (nonatomic, strong) NSData * payload;

/*
 This field is used for installation result analytics. If it is nil,
 we simply won't report the analytics.
 */
@property (nonatomic, strong) NSString * installation_version;
@property (nonatomic, strong) NSString * bgx_device_uuid;

@property (nonatomic) BOOL verbose;
@property (nonatomic, strong) NSString * password;

@end

@implementation BGX_OTA_Updater

- (id)initWithPeripheral:(CBPeripheral *)peripheral bgx_device_uuid:(NSString *)bgx_device_uuid
{
    self = [super init];
    if (self) {
        _peripheral = peripheral;
        self.verbose = NO;
        self.operationInProgress = ota_no_operation_in_progress;
        self.ota_step = ota_step_no_ota;
        self.upload_progress = -1.0f;
        self.update_dispatch_queue = dispatch_queue_create("bgx_ota_dispatch_queue",  NULL);
        self.bgx_device_uuid = bgx_device_uuid;
    }
    return self;
}

- (void)centralManagerDidUpdateState:(CBCentralManager *)central
{
    if (self.verbose) {
        printf("centralManagerDidUpdateState: ");
    }
    switch(central.state) {
        case CBManagerStateUnknown:
            if (self.verbose) {
                printf("Unknown.\n");
            }
            break;
        case CBManagerStateResetting:
            if (self.verbose) {
                printf("CBManagerStateResetting.\n");
            }
            break;
        case CBManagerStateUnsupported:
            if (self.verbose) {
                printf("CBManagerStateUnsupported.\n");
            }
            break;
        case CBManagerStateUnauthorized:
            if (self.verbose) {
                printf("CBManagerStateUnauthorized.\n");
            }
            break;
        case CBManagerStatePoweredOff:
            if (self.verbose) {
                printf("CBManagerStatePoweredOff.\n");
            }
            break;
        case CBManagerStatePoweredOn:
            if (self.verbose) {
                printf("Powered on.\n");
            }
            if (ota_step_scan == self.ota_step) {
                [self.cb_central_manager scanForPeripheralsWithServices: @[ [CBUUID UUIDWithString: SERVICE_BGXSS_UUID] ] options:nil];
            }
            break;
    }
}

- (void)centralManager:(CBCentralManager *)central didDiscoverPeripheral:(nonnull CBPeripheral *)peripheral advertisementData:(nonnull NSDictionary<NSString *,id> *)advertisementData RSSI:(nonnull NSNumber *)RSSI
{
    if ([peripheral.identifier isEqual: _peripheral.identifier]) {
        [central stopScan];
        _peripheral = peripheral;
        if ([self readyForUpdate]) {
            self.ota_step = ota_step_upload_no_response;
        } else {
            self.ota_step = ota_step_connect;
        }
        
        dispatch_async(self.update_dispatch_queue, ^{
            [self takeStep];
        });
    }
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral
{
    if ([peripheral.identifier isEqual: _peripheral.identifier]) {
        self.ota_step = ota_step_find_services;
        dispatch_async(self.update_dispatch_queue, ^{
            
            [self takeStep];
        });
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverServices:(nullable NSError *)error
{
    if (error) {
        NSLog(@"Error: %@", [error description]);
        self.ota_step = ota_step_error;
        return;
        
    }
    for (CBService * iservice in peripheral.services) {
        if ([iservice.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_SERVICE_UUID]]) {
            self.ota_step = ota_step_find_characteristics;
            dispatch_async(self.update_dispatch_queue, ^{
                [self takeStep];
            });
            
            break;
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didDiscoverCharacteristicsForService:(CBService *)service error:(nullable NSError *)error
{
    if (error) {
        NSLog(@"Error: %@", [error description]);
        self.ota_step = ota_step_error;
        return;
    }
    
    if ([self readyForUpdate]) {
        self.ota_step = ota_step_upload_with_response;
        dispatch_async(self.update_dispatch_queue, ^{
            [self takeStep];
        });
    } else {
        NSLog(@"Error: discovered characteristics, but peripheral is not ready for update.");
        self.ota_step = ota_step_error;
        return;
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didWriteValueForCharacteristic:(nonnull CBCharacteristic *)characteristic error:(nullable NSError *)error
{
    if (ota_step_password_required == self.ota_step) {
        return;
    }
    
    if (error) {
        
        /** * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * *
         * In BGX Firmware 1.2 a security feature allowing a password OTA_Control
         * to be set for the OTA_Control characteristic. If a password is
         * required and the password is either not supplied or is an incorrect
         * password an error will occur when trying to write the initial value
         * of zero to the OTA Control Characterisitic (but not the final value
         * of 3). The error from the BGX will be a vendor specific
         * error code, ATT_ERROR_OTA_SERVICE_INVALID_PASSWORD (0x04FE), which on
         * iOS as of this writing is reinterpreted by CoreBluetooth to be
         * Unknown ATT error. However, since the error code returned by
         * CoreBluetooth could change in a future iOS release, any
         * error for the OTA_Control characteristic while ota_step is
         * ota_step_upload_no_response or ota_step_upload_with_response will
         * be treated as if the password is required or incorrect.
         * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * * */
        if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_CONTROL_CHARACTERISTIC_UUID]] &&
            (ota_step_upload_no_response == self.ota_step || ota_step_upload_with_response == self.ota_step)) {
            
            [self.delegate ota_requires_password:error];
            self.ota_step = ota_step_password_required;
        } else {
            self.ota_step = ota_step_error;
            NSLog(@"Error in peripheral didWriteValueForCharacteristic: %@.\n", [error description]);
            [self.cb_central_manager cancelPeripheralConnection:_peripheral];
        }
        
        return;
    }
    
    if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_CONTROL_CHARACTERISTIC_UUID]]) {
        if (self.verbose) {
            printf("Did write value for OTA Control characteristic.\n");
        }
        
        if (ota_step_upload_with_response == self.ota_step) {
            [self writeChunkOfPayload];
        } else if (ota_step_upload_finish == self.ota_step) {
            
            if (self.verbose) {
                printf("OTA_Control write succeeded during ota_step_upload_finish. Disconnecting from peripheral…\n");
            }
            
            [self.cb_central_manager cancelPeripheralConnection:_peripheral];
            
            if (self.verbose) {
                printf("Disconnected.\n");
            }
            
            
            // wait 15 seconds in order for the BGX to load the new firmware.
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(15 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
                self.ota_step = ota_step_end;
                self.operationInProgress = ota_firmware_update_complete;
                
                dispatch_async(self.update_dispatch_queue, ^{
                    [self takeStep];
                });
                
            });
            
        } else if (ota_step_canceled == self.ota_step) {
            self.operationInProgress = ota_no_operation_in_progress;
        }
        
    } else if ([characteristic.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_DATA_CHARACTERISTIC]]) {
        if (self.verbose) {
            printf("Did write value for OTA Data characteristic.\n");
        }
        if (ota_step_upload_with_response == self.ota_step) {
            [self writeChunkOfPayload];
        }
    }
}

- (void)peripheral:(CBPeripheral *)peripheral didModifyServices:(NSArray<CBService *> *)invalidatedServices
{
    NSLog(@"peripheral %@ didModifyServices: %@.", [peripheral description], [invalidatedServices description]);
}

#pragma mark -

- (void)updateFirmwareWithImageAtPath:(NSString *)path2FWImage withVersion:(NSString *)version
{
    dispatch_async(self.update_dispatch_queue, ^{
        self.installation_version = version;
        NSAssert(ota_no_operation_in_progress == self->_operationInProgress, @"Invalid operation in progress value.");
        NSAssert(ota_step_no_ota == self->_ota_step, @"Invalid OTA Step.");
        self.upload_progress = -1.0f;
        self.operationInProgress = ota_firmware_update_in_progress;
        self.ota_step = ota_step_init;
        
        if ([[NSFileManager defaultManager] fileExistsAtPath:path2FWImage]) {
            self.fw_image_path = path2FWImage;
            [self takeStep];
        } else {
            
            NSLog(@"FileNotFound Error:  %@", path2FWImage);
            self.ota_step = ota_step_error;
            return;
        }
    });
}

- (void)cancelUpdate
{
    self.ota_step = ota_step_canceled;
}

#pragma mark -

+ (NSString *)stepName:(ota_step_t)step
{
    switch(step) {
        case ota_step_no_ota:
            return @"ota_step_no_ota";
            break;
        case ota_step_init:
            return @"ota_step_init";
            break;
        case ota_step_scan:
            return @"ota_step_scan";
            break;
        case ota_step_connect:
            return @"ota_step_connect";
            break;
        case ota_step_find_services:
            return @"ota_step_find_services";
            break;
        case ota_step_find_characteristics:
            return @"ota_step_find_characteristics";
            break;
        case ota_step_upload_no_response:
            return @"ota_step_upload_no_response";
            break;
        case ota_step_upload_with_response:
            return @"ota_step_upload_with_response";
            break;
        case ota_step_upload_finish:
            return @"ota_step_upload_finish";
            break;
        case ota_step_end:
            return @"ota_step_end";
            break;
        case ota_step_error:
            return @"ota_step_error";
            break;
        case ota_max_step:
            return @"ota_max_step";
            break;
        case ota_step_password_required:
            return @"ota_step_password_required";
            break;
        case ota_step_canceled:
            return @"ota_step_canceled";
            break;
    }
    
    return @"?";
}

- (void)takeStep
{
    if (self.verbose) {
        printf("takeStep: %s\n", [[BGX_OTA_Updater stepName:self.ota_step] UTF8String]);
    }
    
    switch (self.ota_step) {
        case ota_step_no_ota:
            NSAssert(NO, @"Invalid step");
            break;
        case ota_step_init:
            [self ota_step_init];
            break;
        case ota_step_scan:
            [self ota_step_scan];
            break;
        case ota_step_connect:
            [self ota_step_connect];
            break;
        case ota_step_find_services:
            [self ota_step_find_services];
            break;
        case ota_step_find_characteristics:
            [self ota_step_find_characteristics];
            break;
        case ota_step_upload_no_response:
            [self ota_step_upload_no_response];
            break;
        case ota_step_upload_with_response:
            [self ota_step_upload_with_response];
            break;
        case ota_step_upload_finish:
            [self ota_step_upload_finish];
            break;
        case ota_step_end:
            [self ota_step_end];
            break;
        case ota_step_password_required:
            break;
        case ota_step_canceled:
        case ota_step_error:
        case ota_max_step:
            self.operationInProgress = ota_no_operation_in_progress;
            break;
    }
}

- (void)ota_step_init
{
    _upload_progress = -1.0f;
    if (CBPeripheralStateDisconnected == _peripheral.state) {
        
        self.ota_step = ota_step_scan;
        dispatch_async(self.update_dispatch_queue, ^{
            [self takeStep];
        });
        
    } else if (CBPeripheralStateConnected == _peripheral.state) {
        // check to see if DFU state.
        self.upload_progress = -1.0f;
        CBCharacteristic * data_characteristic = [self OTA_Data_Characteristic];
        if (data_characteristic) {
            self.ota_step = ota_step_upload_no_response;
            [self takeStep];
        } else {
            self.ota_step = ota_step_find_services;
            [self takeStep];
        }
    }
    
}

- (void)ota_step_scan
{
    self.cb_central_manager = [[CBCentralManager alloc] init];
    self.cb_central_manager.delegate = self;
}

- (void)ota_step_connect
{
    if (CBManagerStatePoweredOn == self.cb_central_manager.state) {
        [self.cb_central_manager connectPeripheral:_peripheral options:nil];
    }
}

- (void)ota_step_find_services
{
    _peripheral.delegate = self;
    [_peripheral discoverServices:nil];
}

- (void)ota_step_find_characteristics
{
    [_peripheral discoverCharacteristics:nil forService:[self silabs_ota_service]];
}

- (void)ota_step_upload_no_response
{
    NSUInteger offset = 0;
    NSUInteger amount2Write = 0;
    
    uint8_t cmd = 0;
    [_peripheral writeValue:[NSData dataWithBytes:&cmd length:1] forCharacteristic:[self OTA_Control_Characteristic] type:CBCharacteristicWriteWithResponse];
    if (self.verbose) {
        printf("wrote a 0 to OTA_Control_Characteristic\n");
    }
    NSData * payload = nil;
    
    self.upload_progress = 0.0f;
    
    if (ota_firmware_update_in_progress == _operationInProgress) {
        payload = [NSData dataWithContentsOfFile:self.fw_image_path];
    }
    
    while (offset < payload.length) {
        amount2Write = (payload.length - offset) < kChunkSize ? (payload.length - offset) : kChunkSize;
        
        
        [_peripheral writeValue:[payload subdataWithRange:NSMakeRange(offset, amount2Write)] forCharacteristic:[self OTA_Data_Characteristic] type:CBCharacteristicWriteWithoutResponse];
        
        
        offset += amount2Write;
        self.upload_progress = ((float)offset / (float)payload.length);
        
        [NSThread sleepUntilDate:[NSDate dateWithTimeIntervalSinceNow:0.01]];
    }
    
    self.ota_step = ota_step_upload_finish;
    [self takeStep];
}

- (void)ota_step_upload_with_response
{
    self.data_offset = 0;
    self.amount2write = 0;
    self.upload_progress = 0.0f;
    
    if (ota_firmware_update_in_progress == _operationInProgress) {
        self.payload = [NSData dataWithContentsOfFile:self.fw_image_path];
    }
    
    // write the initial zero.
    NSMutableData * md = [NSMutableData dataWithCapacity: self.password.length +2]; // cmd byte + password length + null term.
    uint8_t cmd = 0;
    
    [md appendBytes:&cmd length:1];
    if ( _password.length > 0) {
        [md appendData: [_password dataUsingEncoding:NSASCIIStringEncoding]];
        [md appendBytes:&cmd length:1];
    }

    [_peripheral writeValue:md forCharacteristic:[self OTA_Control_Characteristic] type:CBCharacteristicWriteWithResponse];
}

- (void)writeChunkOfPayload
{
    if (self.data_offset < self.payload.length) {
        // write a chunk of data.
        self.amount2write = (self.payload.length - self.data_offset) < kChunkSize ? (self.payload.length - self.data_offset) : kChunkSize;
        
        
        [_peripheral writeValue:[self.payload subdataWithRange:NSMakeRange(self.data_offset, self.amount2write)] forCharacteristic:[self OTA_Data_Characteristic] type:CBCharacteristicWriteWithResponse];
        
        
        self.data_offset += self.amount2write;
        self.upload_progress = ((float)self.data_offset / (float)self.payload.length);
        printf("Written %lu bytes %f%%\n", (unsigned long) self.data_offset, 100.0f * self.upload_progress);
        
    } else {
        self.ota_step = ota_step_upload_finish;
        dispatch_async(self.update_dispatch_queue, ^{
            [self takeStep];
        });
    }
}



- (void)ota_step_upload_finish
{
    uint8_t fin = 3;
    NSLog(@"Writing fin.");
    
    [_peripheral writeValue:[NSMutableData dataWithBytes:&fin length:1] forCharacteristic:[self OTA_Control_Characteristic] type:CBCharacteristicWriteWithResponse];
    if (self.verbose) {
        printf("wrote a 3 to OTA_Control_Characteristic.\n");
    }
}

- (void)ota_step_end
{
    if (self.installation_version) {
        [bgx_dms reportInstallationResultWithDeviceUUID: self.bgx_device_uuid version: self.installation_version];
    }
    
    self.cb_central_manager = nil;
}

- (CBService *)silabs_ota_service
{
    for ( CBService * iservice in _peripheral.services) {
        if ([iservice.UUID isEqual: [CBUUID UUIDWithString:SLAB_OTA_SERVICE_UUID]]) {
            return iservice;
        }
    }
    return nil;
}

- (CBCharacteristic *)OTA_Control_Characteristic
{
    CBService * service = [self silabs_ota_service];
    if (service) {
        for (CBCharacteristic * iChar in service.characteristics) {
            if ([iChar.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_CONTROL_CHARACTERISTIC_UUID]]) {
                return iChar;
            }
        }
    }
    return nil;
}

- (CBCharacteristic *)OTA_Data_Characteristic
{
    CBService * service = [self silabs_ota_service];
    if (service) {
        for (CBCharacteristic * iChar in service.characteristics) {
            if ([iChar.UUID isEqual:[CBUUID UUIDWithString:SLAB_OTA_DATA_CHARACTERISTIC]]) {
                return iChar;
            }
        }
    }
    return nil;
}


- (BOOL)readyForUpdate
{
    CBService * ota_service = [self silabs_ota_service];
    CBCharacteristic * ota_control_characteristic = [self OTA_Control_Characteristic];
    CBCharacteristic * ota_data_characteristic = [self OTA_Data_Characteristic];
    
    return (BOOL)(ota_service && ota_control_characteristic && ota_data_characteristic);
}


- (float)upload_progress
{
    return _upload_progress;
}

- (void)setUpload_progress:(float)upload_progress
{
    if (upload_progress != _upload_progress) {
        [self willChangeValueForKey:@"upload_progress"];
        _upload_progress = upload_progress;
        if ( [self.delegate respondsToSelector:@selector(upload_progressDidChange:)] ) {
            [self.delegate upload_progressDidChange:_upload_progress];
        }
        [self didChangeValueForKey:@"upload_progress"];
    }
}

- (ota_step_t)ota_step
{
    return _ota_step;
}

- (void)setOta_step:(ota_step_t)ota_step
{
    if (ota_step != _ota_step) {
        [self willChangeValueForKey:@"ota_step"];
        _ota_step = ota_step;
        if ([self.delegate respondsToSelector:@selector(ota_stepDidChange:)]) {
            [self.delegate ota_stepDidChange:_ota_step];
        }
        [self didChangeValueForKey:@"ota_step"];
    }
}

- (ota_operation_t)operationInProgress
{
    return _operationInProgress;
}

- (void)setOperationInProgress:(ota_operation_t)operationInProgress
{
    if (operationInProgress != _operationInProgress) {
        [self willChangeValueForKey:@"operationInProgress"];
        
        _operationInProgress = operationInProgress;
        
        if ([self.delegate respondsToSelector:@selector(operationInProgressDidChange:)]) {
            [self.delegate operationInProgressDidChange:_operationInProgress];
        }
        
        [self didChangeValueForKey: @"operationInProgress"];
    }
}

- (void)continueOTAWithPassword:(NSString *)password
{
    if (ota_step_password_required == self.ota_step) {
        self.password = password;
        self.ota_step = ota_step_upload_with_response;
        [self takeStep];
    }
}

- (void)setPassword:(NSString *)password
{
    _password = password;
}

@end




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

#import "BGXpressScanner.h"
#import "BGXUUID.h"
#import "BGXDevice.h"
#import "SafeType.h"

/**
 * Some CBCentralManager methods will need to send messages to the BGXDevice.
 */
@interface BGXDevice (ScannerAccess)

+ (instancetype)deviceWithCBPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advData rssi:(NSNumber *)rssi discoveredBy:(BGXpressScanner *)scanner;

- (void)deviceDidConnect;
- (void)deviceDidFailToConnect;
- (void)deviceDidDisconnect;

@end

@implementation BGXpressScanner
{
    CBCentralManager *manager;
}

- (id)init
{
    self = [super init];
    if (self) {
        [self willChangeValueForKey:@"scanState"];
        _scanState = CantScan;
        [self didChangeValueForKey:@"scanState"];
        manager = [[CBCentralManager alloc] initWithDelegate:self queue:nil];
        self.devicesDiscovered = [NSMutableArray arrayWithCapacity:100];
    }
    return self;
}

- (BOOL) startScan
{
    // Remove all the BGXDevices if they are disconnected
    NSMutableArray * devices2Save = [NSMutableArray arrayWithCapacity:self.devicesDiscovered.count];
    for (id mydevice in self.devicesDiscovered) {
        BGXDevice * ibgx = SafeType(mydevice, [BGXDevice class]);
        switch (ibgx.deviceState) {
            case Connected:
            case Interrogating:
            case Connecting:
                [devices2Save addObject:ibgx];
                break;
            default:
                break;
        }
    }
    [self.devicesDiscovered removeAllObjects];
    
    [self.devicesDiscovered addObjectsFromArray:devices2Save];
    
    if (Idle == self.scanState)
    {
        [manager scanForPeripheralsWithServices:@[ [CBUUID UUIDWithString:SERVICE_BGXSS_UUID] ] options:nil];
        [self willChangeValueForKey:@"scanState"];
        _scanState = Scanning;
        [self didChangeValueForKey:@"scanState"];
        if ([self.delegate respondsToSelector:@selector(scanStateChanged:)]) {
            [self.delegate scanStateChanged:_scanState];
        }
        return YES;
    }
    
    return NO;
}

- (BOOL) stopScan
{
    if (Scanning == self.scanState) {
        [manager stopScan];
        [self willChangeValueForKey:@"scanState"];
        _scanState = Idle;
        [self didChangeValueForKey:@"scanState"];
        if ([self.delegate respondsToSelector:@selector(scanStateChanged:)]) {
            [self.delegate scanStateChanged:_scanState];
        }
        return YES;
    }

    return NO;
}


- (void) centralManagerDidUpdateState:(CBCentralManager *)central
{
    if (CBManagerStatePoweredOn == central.state) {
        if (CantScan == self.scanState) {
            [self willChangeValueForKey:@"scanState"];
            _scanState = Idle;
            [self didChangeValueForKey:@"scanState"];
            if ([self.delegate respondsToSelector:@selector(scanStateChanged:)]) {
                [self.delegate scanStateChanged:_scanState];
            }
        }
    } else {
        [self willChangeValueForKey:@"scanState"];
        _scanState = CantScan;
        [self didChangeValueForKey:@"scanState"];
        if ([self.delegate respondsToSelector:@selector(scanStateChanged:)]) {
            [self.delegate scanStateChanged:_scanState];
        }
    }
    
    if ([self.delegate respondsToSelector:@selector(bluetoothStateChanged:)]) {
        [self.delegate bluetoothStateChanged:central.state];
    }
}

- (void) centralManager:(CBCentralManager *)central didDiscoverPeripheral:(CBPeripheral *)peripheral advertisementData:(NSDictionary *)advertisementData RSSI:(NSNumber *)RSSI
{
    NSUInteger deviceIndex = [self.devicesDiscovered indexOfObjectPassingTest:^BOOL(id obj, NSUInteger idx, BOOL * stop) {
        BGXDevice * device = SafeType(obj, [BGXDevice class]);
        if ([device.identifier isEqual:peripheral.identifier]) {
            NSLog(@"Scanned for %@ (previously found).", peripheral.name);
            *stop = YES;
            return YES;
        }
        return NO;
    }];
    
    if (NSNotFound == deviceIndex) {
        BGXDevice * device = [BGXDevice deviceWithCBPeripheral:peripheral advertisementData:advertisementData rssi:RSSI discoveredBy:self];
        [self.devicesDiscovered addObject:device];
        if ([self.delegate respondsToSelector:@selector(deviceDiscovered:)]) {
            [self.delegate deviceDiscovered:device];
        }
    }
}

- (void)centralManager:(CBCentralManager *)central didConnectPeripheral:(CBPeripheral *)peripheral
{
    for (id idev in self.devicesDiscovered) {
        BGXDevice * idevice = SafeType(idev, [BGXDevice class]);
        if ([idevice.identifier isEqual:peripheral.identifier]) {
            [idevice deviceDidConnect];
            return;
        }
    }
}

- (void)centralManager:(CBCentralManager *)central didFailToConnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error
{
    if (central == manager) {
        for (id idev in self.devicesDiscovered) {
            BGXDevice * idevice = SafeType(idev, [BGXDevice class]);
            if ([idevice.identifier isEqual:peripheral.identifier]) {
                [idevice deviceDidFailToConnect];
                return;
            }
        }
    }
}

- (void) centralManager:(CBCentralManager *)central didDisconnectPeripheral:(CBPeripheral *)peripheral error:(NSError *)error
{
    if (central == manager) {
        for (id idev in self.devicesDiscovered) {
            BGXDevice * idevice = SafeType(idev, [BGXDevice class]);
            if ([idevice.identifier isEqual:peripheral.identifier]) {
                [idevice deviceDidDisconnect];
                return;
            }
        }
    }
}


- (CBCentralManager *)centralManager
{
    return manager;
}

@end

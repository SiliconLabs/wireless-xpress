//
//  ViewController.m
//  throughput
//
//  Created by Brant Merryman on 4/12/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import "ViewController.h"
#import "ThroughputViewController.h"

@interface ViewController ()

@end

@implementation ViewController

- (void)viewDidLoad {
    [super viewDidLoad];
    // Do any additional setup after loading the view.
    self.scanner = [[BGXpressScanner alloc] init];
    self.scanner.delegate = self;
}

#pragma mark - Table view data source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView
{
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section
{
    return self.scanner.devicesDiscovered.count;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath
{
    UITableViewCell * cell = [tableView dequeueReusableCellWithIdentifier:@"deviceCell" forIndexPath:indexPath];
    
    BGXDevice * idevice = [self.scanner.devicesDiscovered objectAtIndex:indexPath.row];
    
    cell.textLabel.text = idevice.name;
    cell.accessoryType = UITableViewCellAccessoryDisclosureIndicator;

    return cell;
}


- (void)bluetoothStateChanged:(CBManagerState)state
{
    if (CBManagerStatePoweredOn == state) {
        [self.scanner startScan];
    }
}

- (void)deviceDiscovered:(BGXDevice *)device
{
    [self.tableView reloadData];
}

- (void)scanStateChanged:(ScanState)scanState
{
    switch (scanState) {
        case CantScan:
            NSLog(@"scanState: CantScan");
            break;
        case Idle:
            NSLog(@"scanState: Idle");
            break;
        case Scanning:
            NSLog(@"scanState: Scanning");
            break;
        default:
            NSLog(@"Unknown scanState");
            break;
    }
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
    [self performSegueWithIdentifier:@"connectToDevice" sender:self];
}

- (void)prepareForSegue:(UIStoryboardSegue *)segue sender:(id)sender
{
    ThroughputViewController * tvc = SafeType(segue.destinationViewController, [ThroughputViewController class]);

    BGXDevice * iDevice = [self.scanner.devicesDiscovered objectAtIndex:self.tableView.indexPathForSelectedRow.row];
    tvc.selectedDevice = iDevice;
}

@end

/*
 * Copyright 2018-2020 Silicon Labs
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

#import "DrawerTableViewController.h"
#import "AppDelegate.h"

@interface DrawerTableViewController ()



@property (nonatomic) BOOL firmwareUpdateEnabled;

@end

@implementation DrawerTableViewController

- (void)viewDidLoad {
    [super viewDidLoad];

    self.firmwareUpdateEnabled = NO;

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(enableFirmwareUpdate:)
                                                 name:EnableFirmwareUpdateNotificationName
                                               object:nil];

    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(disableFirmwareUpdate:)
                                                 name:DisableFirmwareUpdateNotificationName
                                               object:nil];


    self.tableView.backgroundView = [[UIView alloc] init];
    self.tableView.backgroundColor = [UIColor colorNamed:@"Silabs Red"];
    
    UIImage * logoImg = [UIImage imageNamed:@"WhiteSilabsLogo_small"];
    UIImageView *  logoImgView = [[UIImageView alloc] initWithImage:logoImg];

    CGSize sz = self.tableView.backgroundView.frame.size;

    double y = sz.height - (3 * logoImg.size.height);
    double x = 160 - (1.5 * logoImg.size.width);

    [logoImgView setFrame:CGRectMake(x, y, logoImg.size.width, logoImg.size.height)];

    [self.tableView addSubview:logoImgView];

    [self.tableView bringSubviewToFront:logoImgView];
    
    [[AppDelegate sharedAppDelegate] addObserver:self forKeyPath:@"selectedDeviceDectorator" options:NSKeyValueObservingOptionNew | NSKeyValueObservingOptionOld context:nil];
}

- (void)observeValueForKeyPath:(NSString *)keyPath ofObject:(id)object change:(NSDictionary<NSKeyValueChangeKey,id> *)change context:(void *)context
{
    if ([keyPath isEqualToString:@"selectedDeviceDectorator"]) {
        [self.tableView reloadData];
    }
}

- (void)didReceiveMemoryWarning {
    [super didReceiveMemoryWarning];
    // Dispose of any resources that can be recreated.
}

#pragma mark - Table view data source

- (NSInteger)numberOfSectionsInTableView:(UITableView *)tableView {
    return 1;
}

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {

    return self.drawerItems.count;
}


- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:@"drawer cell" forIndexPath:indexPath];
    
    // Configure the cell...
    
    cell.textLabel.text = [self.drawerItems objectAtIndex:indexPath.row];
    
    if (self.firmwareUpdateEnabled && 0 == indexPath.row) {
        
        // assign the decorator for the update item.
        
        switch ([AppDelegate sharedAppDelegate].selectedDeviceDectorator) {
            case NoDecoration:
                cell.imageView.image = nil;
                break;
            case UpdateDecoration:
                cell.imageView.image = [UIImage imageNamed:@"Update_Decoration"];
                break;
            case SecurityDecoration:
                cell.imageView.image = [UIImage imageNamed:@"Security_Decoration"];
                break;
        }
        
        
    } else {
        cell.imageView.image = nil;
    }
    
    cell.contentView.backgroundColor = [UIColor colorNamed:@"Silabs Red"];
    cell.textLabel.textColor = [UIColor whiteColor];
    cell.textLabel.textAlignment = NSTextAlignmentRight;
    cell.textLabel.font = [UIFont fontWithName:@"OpenSans-Regular" size:12.0f];
    
    cell.selectionStyle = UITableViewCellSelectionStyleBlue;
    
    return cell;
}

- (void)tableView:(UITableView *)tableView didSelectRowAtIndexPath:(NSIndexPath *)indexPath
{
  NSInteger selectedItemIndex = indexPath.row;
  if (!self.firmwareUpdateEnabled) {
    ++selectedItemIndex;
  }

  switch (selectedItemIndex) {
    case 0: // Update firmware
      {
          [[NSNotificationCenter defaultCenter] postNotificationName:UpdateFirmwareNotificationName object:nil];
      }
      break;
    case 1: // options.
          [[NSNotificationCenter defaultCenter] postNotificationName:OptionsItemNotificationName object:nil];
          break;
    case 2:
      // check if this device can support the tutorial.
      // it doesn't work on a 4" or smaller screen.
    {
      id<UICoordinateSpace> coordinateSpace = [[UIScreen mainScreen] fixedCoordinateSpace];

      if (coordinateSpace.bounds.size.width > 370.f) {
        [[NSNotificationCenter defaultCenter] postNotificationName:StartTutorialNotificationName object:nil];
      } else {
        UIAlertController * alertController = [UIAlertController alertControllerWithTitle:NSLocalizedString(@"Tutorial Unavailable", @"Error title.")
                                                                                  message:NSLocalizedString(@"The screen on this device is too small to properly run the tutorial.", @"Error message.")
                                                                           preferredStyle:UIAlertControllerStyleAlert];

        [alertController addAction: [UIAlertAction actionWithTitle:NSLocalizedString(@"Dismiss", @"button title")
                                                             style:UIAlertActionStyleCancel
                                                           handler:nil]];

        [self presentViewController:alertController animated:YES completion:nil];
      }
    }
      break;
    case 3:
          [[UIApplication sharedApplication] openURL:[NSURL URLWithString:@"https://www.silabs.com/bgx-docs"] options:@{ UIApplicationOpenURLOptionUniversalLinksOnly : @NO  } completionHandler: ^(BOOL success){
              if (!success) {
                  NSLog(@"Failed to open the link.");
              }
          } ];
          
          
          break;
    case 4: // About
      [[NSNotificationCenter defaultCenter] postNotificationName:AboutItemNotificationName object:nil];
      break;
  }

  dispatch_async(dispatch_get_main_queue(), ^{

    [tableView deselectRowAtIndexPath:indexPath animated:YES];
  });
}



- (void)enableFirmwareUpdate:(NSNotification *)n
{
  self.firmwareUpdateEnabled = YES;
  [self.tableView reloadData];
}

- (void)disableFirmwareUpdate:(NSNotification *)n
{
  self.firmwareUpdateEnabled = NO;
  [self.tableView reloadData];
}

-(NSArray *)drawerItems
{
  if (self.firmwareUpdateEnabled) {
    return @[@"Update Firmware…", @"Options", @"Tutorial", @"Help", /* @"iOS Framework", @"Command API", @"Datasheet", @"Purchase Starter Kit",*/ @"About…"];
  } else {
      return @[@"Options", @"Tutorial", @"Help", /*@"iOS Framework", @"Command API", @"Datasheet", @"Purchase Starter Kit", */@"About…"];
  }
}

@end

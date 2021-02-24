//
//  ViewController.h
//  throughput
//
//  Created by Brant Merryman on 4/12/19.
//  Copyright © 2019 Silicon Labs. All rights reserved.
//

#import <UIKit/UIKit.h>
#import <BGXpress/bgxpress.h>

@interface ViewController : UITableViewController<BGXpressScanDelegate>


@property (nonatomic, strong) BGXpressScanner * scanner;

@end


//
//  ViewController.h
//  multiconnect
//
//  Created by Brant Merryman on 10/11/18.
//  Copyright © 2018 Silicon Labs. All rights reserved.
//

#import <UIKit/UIKit.h>

#import <bgxpress/bgxpress.h>

@interface BGXDeviceListViewController : UITableViewController <BGXpressScanDelegate> {
    
    BGXpressScanner * _scanner;
}



@end


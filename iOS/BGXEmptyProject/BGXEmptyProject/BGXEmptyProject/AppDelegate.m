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


#import "AppDelegate.h"

@interface AppDelegate ()

@property (nonatomic, strong) BGXpressManager * bgxmanager;
@end

@implementation AppDelegate


- (BOOL)application:(UIApplication *)application didFinishLaunchingWithOptions:(NSDictionary *)launchOptions {
    // Override point for customization after application launch.


    /**
     * Create a BGXpressManager instance.
     * Set the app delegate as the bgxmanager delegate.
     * You do not have to do it this way. You could create this object
     * someplace else. You could have a dedicated object as the BGXpressDelegate
     * if you wish.
     */
    self.bgxmanager = [[BGXpressManager alloc] init];
    
    self.bgxmanager.delegate = self;
    return YES;
}


- (void)applicationWillResignActive:(UIApplication *)application {
    // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
    // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
}


- (void)applicationDidEnterBackground:(UIApplication *)application {
    // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
    // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
}


- (void)applicationWillEnterForeground:(UIApplication *)application {
    // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
}


- (void)applicationDidBecomeActive:(UIApplication *)application {
    // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
}


- (void)applicationWillTerminate:(UIApplication *)application {
    // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
}

- (void)connectionStateChanged:(ConnectionState)newConnectionState
{
    // Called when the ConnectionState of the BGXpressManager object changes such as when a
    // device connects, disconencts, etc.
}

- (void)busModeChanged:(BusMode)newBusMode
{
    // called when the bus mode of the connected device changes.
}

- (void)dataRead:(NSData *)newData
{
    // called when data is received by your BGX device.
}

- (void)dataWritten
{
    // called when data has been written by your BGXDevice.
}

- (void)deviceDiscovered
{
    // The devicesDiscovered array has changed.
}

@end

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

#import <XCTest/XCTest.h>
#import "Version.h"

@interface BGXCommanderTests : XCTestCase

@end

@implementation BGXCommanderTests

- (void)setUp {
    [super setUp];
    // Put setup code here. This method is called before the invocation of each test method in the class.
}

- (void)tearDown {
    // Put teardown code here. This method is called after the invocation of each test method in the class.
    [super tearDown];
}

- (void)testVersion {
    // This is an example of a functional test case.
    // Use XCTAssert and related functions to verify your tests produce the correct results.
    
    Version * v1 = [Version versionFromString:@"1.0.927.2"];
    Version * v2 = [Version versionFromString:@"1.1.1229.0"];
    Version * v3 = [Version versionFromString:@"1.0.880.1"];
    Version * v4 = [Version versionFromString:@"1.1.1229.0"];

    XCTAssertTrue(NSOrderedSame == [v4 compare:v2]);
    XCTAssertTrue(NSOrderedAscending == [v3 compare:v2]);
    XCTAssertTrue(NSOrderedAscending == [v1 compare:v2]);
    XCTAssertTrue(NSOrderedDescending == [v1 compare:v3]);
    XCTAssertTrue(NSOrderedDescending == [v2 compare:v1]);

}

- (void)testPerformanceExample {
    // This is an example of a performance test case.
    [self measureBlock:^{
        // Put the code you want to measure the time of here.
    }];
}

@end

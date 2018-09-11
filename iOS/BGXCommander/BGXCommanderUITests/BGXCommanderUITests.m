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

#import <XCTest/XCTest.h>

@interface BGXCommanderUITests : XCTestCase

@end

@implementation BGXCommanderUITests

- (void)setUp {
    [super setUp];
    
    // Put setup code here. This method is called before the invocation of each test method in the class.
    
    // In UI tests it is usually best to stop immediately when a failure occurs.
    self.continueAfterFailure = NO;
    // UI tests must launch the application that they test. Doing this in setup will make sure it happens for each test method.
    [[[XCUIApplication alloc] init] launch];
    
    // In UI tests it’s important to set the initial state - such as interface orientation - required for your tests before they run. The setUp method is a good place to do this.
}

- (void)tearDown {
    // Put teardown code here. This method is called after the invocation of each test method in the class.
    [super tearDown];
}

- (void)testAbout {


  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables.staticTexts[@"About\u2026"] tap];
  [app.buttons[@"Close"] tap];

}

- (void)testTutorialCancelStep1 {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];
  [app.buttons[@"overlay close"] tap];

}

- (void)testTutorialCancelStep2 {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];
  [app.buttons[@"Next >"] tap];
  [app.buttons[@"overlay close"] tap];

}

- (void)testTutorialCancelStep3 {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];
  [app.buttons[@"Next >"] tap];

  XCUIElementQuery * query = [app.buttons matchingIdentifier:@"ConnectToDeviceTutorialButton"];
  XCTAssert(1 == query.count, @"Invalid query.");
  XCUIElement * ConnectToDeviceTutorialButton = [query elementBoundByIndex:0];
  [ConnectToDeviceTutorialButton tap];


  [app.buttons[@"overlay close"] tap];

}

- (void)testTutorialCancelStep4 {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];

  XCUIElement *nextButton = app.buttons[@"Next >"];
  [nextButton tap];

  XCUIElementQuery * query = [app.buttons matchingIdentifier:@"ConnectToDeviceTutorialButton"];
  XCTAssert(1 == query.count, @"Invalid query.");
  XCUIElement * ConnectToDeviceTutorialButton = [query elementBoundByIndex:0];
  [ConnectToDeviceTutorialButton tap];

  [[app.otherElements containingType:XCUIElementTypeButton identifier:@"overlay close"].element tap];
  [nextButton tap];
  [nextButton tap];
  [app.buttons[@"overlay close"] tap];

}

- (void)testTutorialCancelStep5 {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];

  XCUIElement *nextButton = app.buttons[@"Next >"];
  [nextButton tap];

  XCUIElementQuery * query = [app.buttons matchingIdentifier:@"ConnectToDeviceTutorialButton"];
  XCTAssert(1 == query.count, @"Invalid query.");
  XCUIElement * ConnectToDeviceTutorialButton = [query elementBoundByIndex:0];
  [ConnectToDeviceTutorialButton tap];

  [nextButton tap];


  while (![nextButton isEnabled]) {
    NSLog(@"waiting");
    usleep(1000000);
  }

  [nextButton tap];
  [app.buttons[@"Send "] tap];
  [nextButton tap];




  [nextButton tap];
  [app.buttons[@"overlay close"] tap];

}

- (void)testTutorialComplete {

  XCUIApplication *app = [[XCUIApplication alloc] init];
  [[[app.navigationBars[@"BGX Commander"] childrenMatchingType:XCUIElementTypeButton] elementBoundByIndex:1] tap];
  [app.tables/*@START_MENU_TOKEN@*/.staticTexts[@"Tutorial"]/*[[".cells.staticTexts[@\"Tutorial\"]",".staticTexts[@\"Tutorial\"]"],[[[-1,1],[-1,0]]],[0]]@END_MENU_TOKEN@*/ tap];

  XCUIElement *nextButton = app.buttons[@"Next >"];
  [nextButton tap];
  XCUIElementQuery * query = [app.buttons matchingIdentifier:@"ConnectToDeviceTutorialButton"];
  XCTAssert(1 == query.count, @"Invalid query.");
  XCUIElement * ConnectToDeviceTutorialButton = [query elementBoundByIndex:0];
  [ConnectToDeviceTutorialButton tap];

  [nextButton tap];

  while (![nextButton isEnabled]) {
    NSLog(@"waiting");
    usleep(1000000);
  }

  [nextButton tap];
  XCUIElement * sendButton = app.buttons[@"Send "];

  while (![sendButton isEnabled]) {
    NSLog(@"waiting");
    usleep(10000);
  }

  [app.buttons[@"Send "] tap];
  while (![nextButton isEnabled]) {
    NSLog(@"waiting");
    usleep(1000000);
  }
  [nextButton tap];

  while (![nextButton isEnabled]) {
    NSLog(@"waiting");
    usleep(1000000);
  }


  [nextButton tap];
  [app.buttons[@"Disconnect "] tap];

}



@end

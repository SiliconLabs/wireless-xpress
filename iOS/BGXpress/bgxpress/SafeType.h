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


#import <Foundation/Foundation.h>

/**
 * @addtogroup safetype Safe Type
 *
 * SafeType is a convienient way to verify that an NSObject passed using id
 * is the expected type when assigning the value to a pointer of a specific
 * Objective-C type.
 *
 * For example, if you are accessing an object from an NSNotification which you
 * expect to be an NSString, you could use:
 *
 *              NSString * myString = SafeType([notification object], [NSString class]);
 *
 * myString will be nil if the object is not an NSString or it will be a valid NSString.
 *
 * @{
 */

/**
 * Checks the type of an object passed as 'id' to verify it conforms to the expected Objective-C type.
 *
 * @param obj The object to be checked
 * @param theClassType The type you expect the object to be.
 *
 * This function will check obj to see if it is a kind of theClassType. If not, it will return nil.
 *
 * @return obj or nil.
 */
id SafeType(id obj, Class theClassType);

/** @} */

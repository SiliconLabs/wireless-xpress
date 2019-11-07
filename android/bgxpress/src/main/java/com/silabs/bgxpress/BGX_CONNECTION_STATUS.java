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

package com.silabs.bgxpress;

/**
 * The BGX_CONNECTION_STATUS datatype describes the connection
 * status of a BGX Device.
 */
public enum BGX_CONNECTION_STATUS {
    DISCONNECTED       ///< The BGXpressService is not connected to a BGX.
    ,CONNECTING         ///< The BGXpressService is connecting to a BGX device.
    ,INTERROGATING      ///< The BGXpressService has connected to a BGX device and is discovering bluetooth services and characteristics.
    ,CONNECTED          ///< The BGXpressService is connected to a BGX device.
    ,DISCONNECTING      ///< The BGXpressService is in the process of disconnecting from a BGX device.
    ,CONNECTIONTIMEDOUT ///< A connection that was in progress has timed out.
}
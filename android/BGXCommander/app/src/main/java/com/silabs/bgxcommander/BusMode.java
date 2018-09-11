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

package com.silabs.bgxcommander;

/**
 * BusMode determines how the BGX interprets data it receives.
 */
public final class BusMode {

    public static final int UNKNOWN_MODE = 0; ///< An invalid bus mode value that indicates the real value has not been determined yet.
    public static final int STREAM_MODE = 1;  ///< send and receive serial data between the BGX data lines and Bluetooth.
    public static final int LOCAL_COMMAND_MODE = 2; ///< data received over the serial lines is processed as a command and results returned over the serial lines.
    public static final int REMOTE_COMMAND_MODE = 3; ///< data received over Bluetooth is processed as a command and results returned over Bluetooth.
}

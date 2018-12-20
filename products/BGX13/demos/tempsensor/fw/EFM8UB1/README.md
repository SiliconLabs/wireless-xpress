Using the EFM8UB1 temperature demo firmware
======

This project was written to run on the [EFM8UB1 STK](https://www.silabs.com/products/development-tools/mcu/8-bit/slstk2000a-efm8-universal-bee-starter-kit), using [Simplicity Studio](https://www.silabs.com/products/development-tools/software/simplicity-studio).

Testing out the firmware requires the use of the BGX Commander mobile app, 
available for [Android](https://play.google.com/store/apps/details?id=com.silabs.bgxcommander) and [iOS](https://itunes.apple.com/vn/app/bgxcommander/id1350920514?mt=8).

Note that detailed information about the EFM8SB1 variant of this 
demo is available at www.docs.silabs.com/bgx/TBD.  Although this 
content is focused on the EFM8SB1, most of the content is still 
applicable to the EFM8UB1 variant as well.

Hardware setup
-------------

1. Attach the BGX13P expansion board to the EFM8UB1 STK's expansion header.
2. Connect the USB cable to the EFM8UB1 STK to power both boards.

Software setup
----------

1. Clone this repo.
2. Open Simplicity Studio.
3. Run Simplicity IDE within Studio.
4. Right click inside the Project Explorer window and choose Import->MCU 
Project.
5. Navigate to the cloned wireless xpress repo to the project's 
./SimplicityStudio/ directory and open the project's .slsproj file.

Demo functionality
----------

1. Build, download, and run the demo to the MCU using Simplicity IDE.
2. Press PB1 on the STK to begin BLE advertising.
3. In BGX Commander, scan and connect to the BGX.
4. In BGX Commander, temperature sensor readings transmitted from the 
MCU will output on-screen.  Note that the temperature reading will be 
invalid because a temperature calibration value has not been sent to the MCU.
5. To send a single point temperature calibration to the MCU to be stored 
in non-volatile memory, type a temperature value in tenths of degrees 
Celsius in BGX Commander.  For instance, 23.0 would be sent as "230".
6. Note that after sending this calibration value, the temperature 
being output by the sensor adjusts to improve accuracy.

License
-------

This source code is licensed under the Apache-2.0 license. Please see the
[LICENSE.md](LICENSE.md) file included in this repository.

Support
-------

Please use the [Silicon Labs Support Portal](https://www.silabs.com/support/)
for all support requests.

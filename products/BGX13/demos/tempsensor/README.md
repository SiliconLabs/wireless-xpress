BLE-enabled temperature sensor
======

This demo codebase creates a Bluetooth-enabled temperature sensor, demonstrating how a smartphone app can connect to a sensor through a Bluetooth Xpress module to acquire data.

The EFM8SB1 variant of the demo firmware is described in detail at https://docs.silabs.com/gecko-os/1/bgx/latest/bgx-to-phone.  

Key demo features include:

- Embedded host interface example
- For the EFM8SB1 variant of the demo, the implementation of low power optimizations for both Bluetooth Xpress and the embedded host
- Bluetooth advertising, connection, and read/write communication

You can find more information about the BGX13 here
https://www.silabs.com/products/wireless/bluetooth/xpress

Getting started
-------
The demo requires the use of a Wireless Xpress BGX13P starter kit.  More information on this kit can be found [here](https://www.silabs.com/products/development-tools/wireless/bluetooth/bgx13p-bluetooth-xpress-starter-kit)

In addition to the starter kit, the demo requires one of the supported embedded host MCUs.  Currently supported embedded host evaluation kits are:

- [EFM8SB1 starter kit](https://www.silabs.com/products/development-tools/mcu/8-bit/slstk2010a-efm8-sleepy-bee-starter-kit)
- [EFM8UB1 starter kit](https://www.silabs.com/products/development-tools/mcu/8-bit/slstk2000a-efm8-universal-bee-starter-kit)

To build and download the example embedded host interface firmware, please see the documentation in that MCU's directory.

License
-------

This source code is licensed under the Apache-2.0 license. Please see the
[LICENSE.md](LICENSE.md) file included in this repository.

Support
-------

Please use the [Silicon Labs Support Portal](https://www.silabs.com/support/)
for all support requests.

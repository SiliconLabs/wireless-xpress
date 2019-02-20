Smart switch demo
======

This demo codebase creates a Bluetooth-enabled light switch.  The firmware
can operate in either switch mode or light mode.  Light mode can receive 
commands from either a Bluetooth Xpress module and MCU running switch mode
or a smart phone.

The EFM8SB1 variant of the demo firmware is described in detail at
https://docs.silabs.com/gecko-os/1/bgx/latest/bgx-to-bgx.  

Key demo features include:

- Embedded host interface example
- Communication using the LE Coded PHY
- Multi-set advertising on both 1M PHY and LE Coded PHY
- Pairing control features

You can find more information about the BGX13 here
https://www.silabs.com/products/wireless/bluetooth/xpress

Getting started
-------
The demo requires the use of a Wireless Xpress BGX13P starter kit.  More 
information on this kit can be found 
[here](https://www.silabs.com/products/development-tools/wireless/bluetooth/bgx13p-bluetooth-xpress-starter-kit)

In addition to the starter kit, the demo requires one of the supported embedded
host MCUs.  Currently supported embedded host evaluation kits are:

- [EFM8UB1 starter kit](https://www.silabs.com/products/development-tools/mcu/8-bit/slstk2000a-efm8-universal-bee-starter-kit)

To build and download the example embedded host interface firmware, please 
see the documentation in that MCU's directory.

License
-------

This source code is licensed under the Apache-2.0 license. Please see the
[LICENSE.md](LICENSE.md) file included in this repository.

Support
-------

Please use the [Silicon Labs Support Portal](https://www.silabs.com/support/)
for all support requests.

EFM8 + AMW007 User's Guide
==========

Hardware
-----------

This guide uses the EFM8UB1 or EFM8UB2 Starter Kit connected to an 
AMW007-E04 Starter Kit.


Before you get started
----------------------

It will be helpful to familiarize yourself with the AMW007 by 
itself, before connecting it to an EFM8.  The AMW007 user guide 
is here: 
https://www.silabs.com/documents/public/user-guides/ug370-amw007-user-guide.pdf


Quick Start
-----------

1. Ensure you have the latest firmware for the AMW007:
	> dms_activate SILABS-EFM8STK
	> ota

2. Ensure Simplicity Studio has been updated to the latest EFM8 SDK.

3. Install the WGX bootloader demo to the EFM8. This can be found by 
selecting your device in Simplicity Studio and then selecting the 
WGX UART1 Bootloader from Demos/WGX in the Launcher.

4. Connect the AMW007 board to the EFM8.

The WGX bootloader will load a default demo image from the AMW007. 
The demo shows some of the basic capabilities of the WGX library, 
including setting up a soft AP, connecting to an AP using the web 
setup mode, scanning for wifi networks, and putting the AMW007 to sleep.

The demo image can also display a listing of other EFM8 demo programs 
stored on the AMW007 and request one be loaded.  After running a demo, 
a reset or power cycle will restore main demo application and you can 
choose another demo image.


Creating bootable images of EFM8 applications
---------------------------------------------

The EFM8 bootloader uses a custom file format and cannot load .hex 
files directly. For information and tools to convert from a .hex 
file to a bootloader image, see AN945 
(https://www.silabs.com/documents/public/application-notes/an945-efm8-factory-bootloader-user-guide.pdf)

The recommended way to load the EFM8 images onto the AMW007 file 
system is with the Zentri DMS: https://docs.zentri.com/dms/latest/

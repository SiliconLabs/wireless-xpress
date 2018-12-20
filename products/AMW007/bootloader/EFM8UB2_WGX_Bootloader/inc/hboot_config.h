#ifndef __HBOOT_CONFIG_H__
#define __HBOOT_CONFIG_H__

#define EFM8_UART 					1

// board controller configuration for UART0:
// #define ENABLE_BOARD_CONTROLLER
#define DISABLE_BOARD_CONTROLLER

#define ENABLE_LEDS

// if enabled, always load default program image
// on power on or pin reset
#define LOAD_DEFAULT_ON_STARTUP

#define BOOT_FILE "EFM8/UB2/default.efm8"

#endif
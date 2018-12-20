#ifndef __EFM8_DEVICE_H__
#define __EFM8_DEVICE_H__

#ifdef __C51__
#include "SI_EFM8UB1_Register_Enums.h"
#else
#include "SI_EFM8UB1_Defs.inc"
#endif

// Select the STK device if one has not been specified by the project
#ifndef EFM8UB1_DEVICE
#define EFM8UB1_DEVICE EFM8UB10F16G_QFN28
#endif
#include "SI_EFM8UB1_Devices.h"

// Bootloader firmware revision number
#define BL_REVISION 0x90

// Device specific ID is checked by the prefix command
#define BL_DERIVATIVE_ID  (0x3200 | DEVICE_DERIVID)

// Holding the boot pin low at reset will start the bootloader
#if defined(DEVICE_PKG_QFN20)
#define BL_START_PIN P2_B0

#elif defined(DEVICE_PKG_QFN24)
#define BL_START_PIN P2_B0

#elif defined(DEVICE_PKG_QSOP24)
#define BL_START_PIN P2_B0

#elif defined(DEVICE_PKG_QFN28)
#define BL_START_PIN P3_B0

#else
#error Unknown or unsupported device package!

#endif

// Number of cycles (at reset system clock) boot pin must be held low
#define BL_PIN_LOW_CYCLES (50 * 25 / 8)

// Parameters that describe the flash memory geometry
#define BL_FLASH0_LIMIT DEVICE_FLASH_SIZE
#define BL_FLASH0_PSIZE 512
#define BL_FLASH1_START 0xF800
#define BL_FLASH1_LIMIT 0xFC00
#define BL_FLASH1_PSIZE 64

// Define the size of the bootloader in flash pages
// UART = 1, USB = 3, WiFi = 2
#define BL_PAGE_COUNT 2

// Define the starting address for the bootloader's code segments
#define BL_LIMIT_ADDRESS (BL_FLASH0_LIMIT - (BL_FLASH0_PSIZE * BL_PAGE_COUNT))
#define BL_START_ADDRESS (BL_FLASH1_LIMIT - BL_FLASH1_PSIZE)
#define BL_LOCK_ADDRESS  (BL_FLASH1_LIMIT - 1)

// Defines for managing SFR pages (used for porting between devices)
#define SET_SFRPAGE(p)  SFRPAGE = (p)

// SFR Autopaging
#define DECL_PAGE uint8_t savedPage
// enter autopage section
#define SET_PAGE(p)     do                                                    \
                        {                                                     \
                          savedPage = SFRPAGE;  /* save current SFR page */   \
                          SFRPAGE = (p);        /* set SFR page */            \
                        } while(0)
// exit autopage section
#define RESTORE_PAGE    do                                                    \
                        {                                                     \
                          SFRPAGE = savedPage;  /* restore saved SFR page */  \
                        } while(0)
// SFR page used to access UART1 registers
#define UART1_SFR_PAGE 0x20

#endif // __EFM8_DEVICE_H__

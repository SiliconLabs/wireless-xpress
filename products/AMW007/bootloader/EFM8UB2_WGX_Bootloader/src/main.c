#include "efm8_device.h"
#include "boot.h"
#include "flash.h"

#include "hboot.h"
#include "hboot_config.h"
#include "leds.h"

// Converts command byte into zero based opcode (makes code smaller)
#define OPCODE(cmd) ((cmd) - BOOT_CMD_IDENT)

// Holds the current command opcode
static uint8_t opcode;

// Holds reply to the current command
static uint8_t reply;

// ----------------------------------------------------------------------------
// Perform the bootloader erase or write commands.
// ----------------------------------------------------------------------------
void doEraseWriteCmd(void)
{
  // Get the starting address from the boot record
  uint16_t address = boot_getWord();

  // Check if bootloader is allowed to modify this address range
  if (flash_isValidRange(address, boot_hasRemaining()))
  {
    // Erase the flash page first if this was the erase command
    if (opcode == OPCODE(BOOT_CMD_ERASE))
    {
      flash_erasePage(address);
    }
    // Write data from boot record to flash one byte at a time
    while (boot_hasRemaining())
    {
      flash_writeByte(address, boot_getByte());
      address++;
    }
  }
  else
  {
    // Return an error if the address range was restricted
    reply = BOOT_ERR_RANGE;
  }
}

// ----------------------------------------------------------------------------
// Perform the bootloader verify command.
// ----------------------------------------------------------------------------
void doVerifyCmd(void)
{
  // Get the starting and ending addresses from the boot record
  uint16_t address = boot_getWord();
  uint16_t limit = boot_getWord();

  // Compute an Xmodem CRC16 over the indicated flash range
  flash_initCRC();
  while (address <= limit)
  {
    flash_updateCRC(flash_readByte(address));
    address++;
  }
  // Compare with the expected result
  if (flash_readCRC() != boot_getWord())
  {
    // Return an error if the CRC did not match
    reply = BOOT_ERR_CRC;
  }
}

// ----------------------------------------------------------------------------
// Bootloader Mainloop
// ----------------------------------------------------------------------------
void main(void)
{
  // Initialize the communication channel and clear the flash keys
  boot_initDevice();
  flash_setKeys(0, 0);
  flash_setBank(0);

  // continually poll for host connection
  hboot_pollForHost();
  
  // Send the hboot command
  hboot_requestBootload();

  // Enable watchdog timer
  // The watchdog on the UB2 is a bit limited in how it is clocked.
  // In order to get a long enough time out, we set PCA0 to be clocked
  // from a Timer0 overflow.

  // First, set up timer 0.
  // Timer 0 source is system clock
  CKCON0 |= CKCON0_T0M__SYSCLK;

  // Set timer 0 to mode 1, 16 bit counter
  TMOD |= TMOD_T0M__MODE1;

  // Start timer 0
  TCON_TR0 = 1;

  // Set PCA0 to clock from timer 0 overflows and enable watchdog.
  PCA0MD = PCA0MD_CPS__T0_OVERFLOW;
  PCA0CPL4 = 20;
  PCA0MD |= PCA0MD_WDTE__BMASK;
  PCA0CPH4 = 0x00;

  // watchdog is set for 14 seconds
  // if we've sent hboot and do not complete the load
  // within 14 seconds, we will get a reset and try again.

  // Loop until a run application command is received
  while (true)
  {
    #ifdef ENABLE_LEDS
    LED1 = 1;
    #endif

    // Wait for a valid boot record to arrive
    boot_nextRecord();

    #ifdef ENABLE_LEDS
    LED1 = 0;
    #endif

    // Receive the command byte and convert to opcode
    opcode = OPCODE(boot_getByte());
    
    // Assume success - handlers will modify if there is an error
    reply = BOOT_ACK_REPLY;

    // Interpret the command opcode
    switch (opcode)
    {
      case OPCODE(BOOT_CMD_IDENT):
        // Return an error if bootloader derivative ID does not match
        if (BL_DERIVATIVE_ID != boot_getWord())
        {
          reply = BOOT_ERR_BADID;
        }
        break;

      case OPCODE(BOOT_CMD_SETUP):
        // Save flash keys and select the requested flash bank
        flash_setKeys(boot_getByte(), boot_getByte());
        flash_setBank(boot_getByte());
        break;

      case OPCODE(BOOT_CMD_ERASE):
      case OPCODE(BOOT_CMD_WRITE):
        doEraseWriteCmd();
        break;

      case OPCODE(BOOT_CMD_VERIFY):
        doVerifyCmd();
        break;

      case OPCODE(BOOT_CMD_LOCK):
        // Write the boot signature and flash lock bytes
        flash_setBank(0);
        flash_writeByte((uint16_t)&boot_otp[0], boot_getByte());
        flash_writeByte((uint16_t)&boot_otp[1], boot_getByte());
        break;

      case OPCODE(BOOT_CMD_RUNAPP):
        // Acknowledge the command, then reset to start the user application
        boot_sendReply(BOOT_ACK_REPLY);

        // stop watchdog
        PCA0MD &= ~PCA0MD_WDTE__BMASK;

        boot_runApp();
        break;

      default:
        // Return bootloader revision for any unrecognized command
        reply = BL_REVISION;
        break;
    }

    // Reply with the results of the command
    boot_sendReply (reply);
  }
}

#include "efm8_device.h"
#include "boot.h"
#include "hboot_config.h"

#if EFM8_UART == 1

// If the device does not have XRAM, use IDATA to hold a small receive buffer;
// otherwise, use XRAM to hold a full-size receive buffer.

#if (DEVICE_XRAM_SIZE < 256)
// Sized to hold the payload for 128 byte write command.
// Erase/Write = 1 byte command + 2 bytes address + 128 bytes data
#define BOOT_RXBUF_SIZE 132

// Buffer holds data received from the host
uint8_t SI_SEG_IDATA boot_rxBuf[BOOT_RXBUF_SIZE];

// Used to cloak buffer access
#define BOOT_RXBUF(i) boot_rxBuf[i]

#else
// Sized to hold the maximum boot record payload
#define BOOT_RXBUF_SIZE 256

// Buffer holds data received from the host (must be located at 0)
uint8_t SI_SEG_XDATA boot_rxBuf[BOOT_RXBUF_SIZE] _at_ 0x00;

// Cloaks XDATA buffer access to reduce code size.
// CAUTION: For this to work properly, the buffer must be located at address 0x0.
#define BOOT_RXBUF(i) *((uint8_t SI_SEG_XDATA *)(i))

#endif // DEVICE_XRAM_SIZE

// Counts the number of bytes remaining in the receive buffer. Also acts as 
// the index for the next byte to get from the receive buffer.
uint8_t boot_rxNext;

// ----------------------------------------------------------------------------
// Read one byte from UART
// ----------------------------------------------------------------------------
static uint8_t readByte(void)
{
  DECL_PAGE;
  uint8_t c;
  SET_PAGE (UART1_SFR_PAGE);

  // check for overrun
  if (SCON1 & SCON1_OVR__SET)
  {
    // flush a byte from the fifo
    c = SBUF1;  

    // clear overrun and receive flags
    SCON1 &= ~(SCON1_OVR__SET | SCON1_RI__SET);
  }

  // Wait indefinitely for a byte to arrive
  while (!SCON1_RI)
    ;
  SCON1_RI = 0;
  c = SBUF1;
  RESTORE_PAGE;
  return c;
}

// ----------------------------------------------------------------------------
// Wait for the next boot record to arrive.
// ----------------------------------------------------------------------------
void boot_nextRecord(void)
{
  uint8_t count;

  // Wait indefinitely to receive a frame start character
  while (BOOT_FRAME_START != readByte())
    ;

  // Receive and save the frame length
  count = readByte();
  boot_rxNext = count;

  // Receive and buffer frame data. Data is stored in reverse order (from 
  // the end of the buffer to the beginning) to save code space.
  for (; count; count--)
  {
    BOOT_RXBUF(count) = readByte();
  }
}

// ----------------------------------------------------------------------------
// Get the next byte in the boot record.
// ----------------------------------------------------------------------------
uint8_t boot_getByte(void)
{
  uint8_t next = BOOT_RXBUF(boot_rxNext);
  boot_rxNext--;
  return next;
}

// ----------------------------------------------------------------------------
// Get the next word in the boot record.
// ----------------------------------------------------------------------------
uint16_t boot_getWord(void)
{
  SI_UU16_t word;

  // 16-bit words are received in big-endian order
  word.u8[0] = boot_getByte();
  word.u8[1] = boot_getByte();
  return word.u16;
}

// ----------------------------------------------------------------------------
// Send a one byte reply to the host.
// ----------------------------------------------------------------------------
void boot_sendReply(uint8_t reply)
{
  DECL_PAGE;
  SET_PAGE(UART1_SFR_PAGE);

  /* Wait for the FIFO to have space */
  while(!(UART1FCN1 & UART1FCN1_TXNF__NOT_FULL));
  SCON1_TI = 0;
  SBUF1 = reply;
  while (UART1FCT & UART1FCT_TXCNT__FMASK);
  while (!SCON1_TI);
  RESTORE_PAGE;
}
#endif
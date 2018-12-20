#include "efm8_device.h"
#include "flash.h"

// Holds the flash keys received in the prefix command
uint8_t flash_key1;
uint8_t flash_key2;

// ----------------------------------------------------------------------------
// Writes one byte to flash memory.
// ----------------------------------------------------------------------------
void writeByte(uint16_t addr, uint8_t byte)
{
  uint8_t SI_SEG_XDATA * pwrite = (uint8_t SI_SEG_XDATA *) addr;

  // Unlock flash by writing the key sequence
  FLKEY = flash_key1;
  FLKEY = flash_key2;

  // Enable flash writes, then do the write
  PSCTL |= PSCTL_PSWE__WRITE_ENABLED;
  *pwrite = byte;
  PSCTL &= ~(PSCTL_PSEE__ERASE_ENABLED|PSCTL_PSWE__WRITE_ENABLED);
}

// ----------------------------------------------------------------------------
// Erases one page of flash memory.
// ----------------------------------------------------------------------------
void flash_erasePage(uint16_t addr)
{
  // Enable flash erasing, then start a write cycle on the selected page
  PSCTL |= PSCTL_PSEE__ERASE_ENABLED;
  writeByte(addr, 0);
}

// ----------------------------------------------------------------------------
// Writes one byte to flash memory.
// ----------------------------------------------------------------------------
void flash_writeByte(uint16_t addr, uint8_t byte)
{
  // Don't bother writing the erased value to flash
  if (byte != 0xFF)
  {
    writeByte(addr, byte);
  }
}

// ----------------------------------------------------------------------------
// Check if flash address range may be erased or written.
// ----------------------------------------------------------------------------
bool flash_isValidRange(uint16_t addr, uint8_t size)
{
  uint16_t limit = BL_LIMIT_ADDRESS;

  // Adjust upper limit if start address is in data flash
  if (addr >= BL_FLASH1_START)
  {
    limit = BL_START_ADDRESS;
  }
  // Test address range against the upper limit for this region
  if ((addr < limit) && (addr + size <= limit))
  {
    return true;
  }
  else
  {
    return false;
  }
}

// ----------------------------------------------------------------------------
// Return the CRC register value.
// ----------------------------------------------------------------------------
uint16_t flash_readCRC(void)
{
  uint16_t crc16;

  // Read the result starting with the MSB
  CRC0CN0 |= CRC0CN0_CRCPNT__ACCESS_UPPER;
  crc16  = CRC0DAT << 8;
  crc16 |= CRC0DAT;
  return crc16;
}

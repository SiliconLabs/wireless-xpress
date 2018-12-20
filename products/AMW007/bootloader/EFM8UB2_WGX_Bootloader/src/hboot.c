#include "efm8_device.h"
#include "boot.h"

#include "hboot_config.h"
#include "hboot.h"

extern uint8_t reset_source;

SI_SEGMENT_VARIABLE (hboot_cmd [], char, SI_SEG_CODE) = HBOOT_CMD_START;
SI_SEGMENT_VARIABLE (default_app [], char, SI_SEG_CODE) = BOOT_FILE;

/**************************************************************************//**
 * Check UART for received data
 *****************************************************************************/
static uint8_t checkUART(void)
{
  uint8_t c = 0;

#if EFM8_UART == 0
  if (SCON0_RI)
  {
    c = SBUF0; 
    SCON0_RI = 0; // clear flag
  }
#else  
  // check for overrun
  if (SCON1 & SCON1_OVR__SET)
  {
    // flush a byte from the fifo
    c = SBUF1;  

    // clear overrun and receive flags
    SCON1 &= ~(SCON1_OVR__SET | SCON1_RI__SET);
  }

  if (SCON1 & SCON1_RI__SET)
  {
    c = SBUF1; 
  }
#endif

  return c;
}

/**************************************************************************//**
 * Send string on UART
 *****************************************************************************/
static void sendString(char *s)
{
  while (*s != 0)
  {
    boot_sendReply(*s);
    s++;
  } 
}

/**************************************************************************//**
 * Delay
 *****************************************************************************/
static void delay(uint32_t count)
{
  while (count > 0)
  {
    count--;
  }
}

/**************************************************************************//**
 * Wait for host connection
 *****************************************************************************/
void hboot_pollForHost(void)
{
  uint16_t count;
  uint8_t tries = 8;

  // repeat check multiple times to make sure we have a connection
  while (tries > 0)
  {
    // busy wait so we don't flood with characters
    delay(10000);

    boot_sendReply('\r');
    boot_sendReply('\n');

    // check UART for a while for newline
    count = 10000;
    while (count > 0)
    {     
      if (checkUART() == '\n')
      {
        tries--;
        break;
      }

      count--;
    }

    // check for timeout
    if (count == 0)
    {
      // reset tries
      tries = 8;
    }
  }

  delay(1000);
}

/**************************************************************************//**
 * Request bootload file from host
 *****************************************************************************/
void hboot_requestBootload(void)
{

  // get pointer to signature region
  uint8_t SI_SEG_XDATA *sig = (char SI_SEG_XDATA *)0x2;

  // send the first part of the hboot command
  sendString(hboot_cmd);

  // check if application requesting a specific file.
  // also check that we did not just come up from power on reset.
  if ((sig[0] == '@') && (sig[1] == '#') && ((reset_source & RSTSRC_PORSF__SET) == 0))
  {
    // erase bootloader command from signature
    sig[0] = 0;
    sig[1] = 0;
    
    // Send the name of the file
    sig += 2;
    sendString(sig);
  }
  else
  {
    sendString(default_app);
  }
  
  // finish command 
  boot_sendReply('\r'); 
  boot_sendReply('\n');
}


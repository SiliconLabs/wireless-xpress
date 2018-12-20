/*
 * BGX_uart.c
 *
 *  Created on: Jul 14, 2018
 *      Author: padorris
 */

#include <STRING.h>
#include <uart_1.h>
#include <stdio.h>
#include <STRING.h>
#include "efm8_config.h"
#include "SI_EFM8UB1_Defs.h"
#include "BGX_uart.h"
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
bit listenerEnabled = false;
bit listenerLinEndFound = false;
uint16_t listenerLineLen;
SI_SEGMENT_VARIABLE(listenerBuffer[2], uint8_t, SI_SEG_XDATA);
SI_SEGMENT_VARIABLE(BGX_transmitBuffer[30], uint8_t, SI_SEG_XDATA);
SI_SEGMENT_VARIABLE(BGX_receiveBuffer[90], uint8_t, SI_SEG_XDATA);

bit receivedResponse = false;

/***************************************************************************//**
 * Callback for transmission of a data.
 *
 * This function is called when all bytes in the buffer have been transferred.
 * Left blank because it has no need in the demo.
 *
 * @warning
 * This function is called from an ISR and should be as short as possible.
 ******************************************************************************/
void UART1_transmitCompleteCb()
{
	UART1FCN0 &= ~UART1FCN0_TFRQE__ENABLED;
}
void UART1_receiveCompleteCb()
{
  receivedResponse = true;
  if(listenerEnabled)
  {
    BGX_receiveBuffer[listenerLineLen] = listenerBuffer[0];
    listenerLineLen++;
    if(listenerBuffer[0] == '\n')
    {
      listenerLinEndFound = true;
    }
    UART1_readBuffer(listenerBuffer,1);
  }
}

uint8_t BGX_Write(const char *buff)
{
  DECL_PAGE;
  SET_PAGE(UART1_SFR_PAGE);
  strcpy(BGX_transmitBuffer, buff);
  UART1_writeBuffer(BGX_transmitBuffer, strlen(BGX_transmitBuffer));
  if(MODE_PIN == COMMAND_MODE)
  {
    RESTORE_PAGE;
    return BGX_getResponse();
  }
  else
  {
	RESTORE_PAGE;
    // if we're in streaming mode, don't expect a response
    // and assume success
    return '0';
  }
}


uint8_t BGX_getResponse(void)
{
  uint8_t index, last_character, this_character, return_value, start_of_line = 0;
  bit new_line_started = false;
  bit response_received = false;
  last_character = 255;
  this_character = 255;
  index = 0;
  do
  {
    // Receive 1 character
    UART1_readBuffer(listenerBuffer,1);
    while(!receivedResponse);
    // Flag is set in UART peripheral driver callback ISR
    receivedResponse = false;

    last_character = this_character;
    this_character = listenerBuffer[0];
    BGX_receiveBuffer[index] = this_character;
    index++;

    // Make decisions after BGX completes transmission of line
    if(this_character=='\n')
    {
    	if(BGX_receiveBuffer[start_of_line] == 'R')
    	{
          // Response headers always start with 'R' so this was the header
    	  // The second character on this line is the response value
          response_received = true;
          return_value = BGX_receiveBuffer[start_of_line+1];
    	}
    	else if(response_received)
    	{
          // Response header was last line, this is data log line
          // We could parse the log here if needed
          // The data log is the last BGX will transmit, so now we can exit
          break;
    	}
    	// Next character will be the start of the next line
    	start_of_line = index;
    }
  }while(1);

  return return_value;
}

uint8_t BGX_didReceiveLine(const char *buff)
{
  uint8_t index, last_character, this_character, return_value, start_of_line = 0;
  bit new_line_started = false;
  bit response_received = false;
  last_character = 255;
  this_character = 255;
  index = 0;
  do
  {
    // Receive 1 character
    UART1_readBuffer(listenerBuffer,1);
    while(!receivedResponse);
    // Flag is set in UART peripheral driver callback ISR
    receivedResponse = false;

    last_character = this_character;
    this_character = listenerBuffer[0];
    BGX_receiveBuffer[index] = this_character;
    index++;

    // Make decisions after BGX completes transmission of line
    if(this_character=='\n')
    {
       if(strstr(BGX_receiveBuffer, buff))
       {
         return_value = 1;
         break;
       }
       else
       {
         return_value = 0;
         break;
       }
    }
  }while(1);

  return return_value;
}

void listenerOn(void)
{
  listenerEnabled = true;
  listenerReset();
  UART1_readBuffer(listenerBuffer,1);
}
void listenerOff(void)
{
  listenerEnabled = false;
}
void listenerReset(void)
{
  listenerLineLen = 0;
  listenerLinEndFound = false;
}
bool listenerFoundLineEnd(void)
{
  return listenerLinEndFound;
}

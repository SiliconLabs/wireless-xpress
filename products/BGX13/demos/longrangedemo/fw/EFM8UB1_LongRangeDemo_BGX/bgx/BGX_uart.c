/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

#include <STRING.h>
#include <STDLIB.h>
#include <uart_1.h>
#include <stdio.h>
#include "efm8_config.h"
#include "SI_EFM8UB1_Defs.h"
#include "BGX_uart.h"

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/

#define DECL_PAGE uint8_t savedPage

// enter autopage section
#define SET_PAGE(p)     do                                                    \
                        {                                                     \
                          savedPage = SFRPAGE;  /* save current SFR page */   \
                          SFRPAGE = (p);        /* set SFR page */            \
                        } while (0)

// exit autopage section
#define RESTORE_PAGE    do                                                    \
                        {                                                     \
                          SFRPAGE = savedPage;  /* restore saved SFR page */  \
                        } while (0)

// SFR page used to access UART1 registers
#define UART1_SFR_PAGE   0x20

// The receive buffer is particularly big because the BGX device
// sends hundreds of bytes when scanning for other BGX devices
// TODO: try with smaller size (e.g. 90) and see if stuff breaks
#define MAX_RX_BUFFER_SIZE    256

// The command response header has the following format: RXYYYYY\r\n
#define MAX_RESPONSE_LENGTH   9

// Max command length that will be sent to the BGX device
#define MAX_TX_BUFFER_SIZE    30

/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

// Set this to true to allow the MCU to listen for packets from the BGX device.
// The main() sets this to true after the BGX device has been initialized.
SI_BIT(listenerEnabled) = false;

// This flag indicates whether a line of data ending in '\n' has been received from the BGX device
// (e.g. a command response header ending in "\r\n", response data ending in "\r\n", etc).
// The UART1_receiveCompleteCb() function sets this to true after receiving a linefeed '\n'.
// Note: the user program must set this back to false after processing the received data.
// Note: the command response could have multiple linefeeds.
SI_BIT(listenerReceivedLineEndFlag) = false;

// This flag indicates whether ANY data has been received from the BGX device.
// Upon entering the UART1_receiveCompleteCb() function, this flag is immediately set to true.
// Notice the similarity, but difference to the listenerReceivedLineEndFlag flag.
SI_BIT(listenerReceivedDataFlag) = false;

// This flag indicates whether the entire command response data has been
// received. Whereas listenerReceivedLineEndFlag gets set for every '\n'
// received, this flag only gets set once all of the data specified in the
// response header <RXYYYYY><response data> has been received. This gets set in BGX_getResponse().
SI_BIT(listenerReceivedEntireResponseFlag) = false;

// Number of bytes that have been received from the BGX device. This is also
// used as the index for filling the BGX_receiveBuffer[]
uint16_t listenerNumBytesReceived;

// This buffer is used by the uart_1.c peripheral driver's ISR to receive data
// TODO: this should work with size 1 --> try this and see if stuff breaks, no need for this to be 2,
//       if anything, it's just confusing
SI_SEGMENT_VARIABLE(listenerBuffer[2], uint8_t, SI_SEG_XDATA);

// This buffer is used by the UART1_receiveCompleteCb() function. Data is copied from
// the listener buffer into this buffer. It holds the actual response data
SI_SEGMENT_VARIABLE(BGX_receiveBuffer[MAX_RX_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);

// This buffer is used to hold the command sent to the BGX device
SI_SEGMENT_VARIABLE(BGX_transmitBuffer[MAX_TX_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);

/***************************************************************************//**
 * @brief
 *    Callback function for transmission of data.
 *
 * @details
 *    This function gets called when all bytes in the transmit buffer have been
 *    transferred. Left blank because this demo has no need for this callback
 *
 * @warning
 *    This function is called from an ISR and should be as short as possible
 ******************************************************************************/
void UART1_transmitCompleteCb(void)
{
  UART1FCN0 &= ~UART1FCN0_TFRQE__ENABLED;
}

/***************************************************************************//**
 * @brief
 *    Callback function for receiving data.
 *
 * @details
 *    This function gets called when all bytes in the receive buffer have been
 *    received. It copies one byte from the listener buffer to the
 *    BGX_receiveBuffer[].
 *
 * @warning
 *    This function is called from an ISR and should be as short as possible
 ******************************************************************************/
void UART1_receiveCompleteCb(void)
{
  listenerReceivedDataFlag = true;
  if (listenerEnabled)
  {
    BGX_receiveBuffer[listenerNumBytesReceived] = listenerBuffer[0];
    listenerNumBytesReceived++;
    if (listenerBuffer[0] == '\n')
    {
      listenerReceivedLineEndFlag = true;
    }
    UART1_readBuffer(listenerBuffer, 1);
  }
}

// TODO: have BGX_Write take a second parameter (pointer to a buffer to store the response results)

/***************************************************************************//**
 * @brief
 *    Send the command over UART to the BGX device
 *
 * @details
 *    This function calls BGX_getResponse to copy the command response into the
 *    BGX_receiveBuffer[] if we're in COMMAND_MODE.
 ******************************************************************************/
uint8_t BGX_Write(const char *buff)
{
  DECL_PAGE;
  SET_PAGE(UART1_SFR_PAGE);

  // Send the command over UART to the BGX device
  strcpy(BGX_transmitBuffer, buff);
  IE_EA = 0;
  UART1_writeBuffer(BGX_transmitBuffer, strlen(BGX_transmitBuffer));
  IE_EA = 1;

  RESTORE_PAGE;

  // If the board is in command mode, get the command response from
  // the BGX device and put it in the BGX_receiveBuffer[]
  if (MODE_PIN == COMMAND_MODE) {
    return BGX_getResponse();
  }
  // Else we're in streaming mode so don't expect a response and assume success
  else {
    return 0;
  }
}

/***************************************************************************//**
 * @brief
 *    Get the command response data
 *
 * @details
 *    The command response is in the format RXYYYYY<response data>. The while
 *    loop copies the response data one byte at a time into the receive buffer
 *    and then checks if the byte was a linefeed '\n'. If it is, then start
 *    processing the received data. The code first checks to see if the data
 *    line is a response header. If so, record how many bytes the response data
 *    will be and then continue receiving data. Once the code has received the
 *    response header and all of the data bytes have been received, exit.
 ******************************************************************************/
uint8_t BGX_getResponse(void)
{
  uint8_t index = 0;
  uint8_t thisCharacter = 0;
  uint8_t errorCode = 255;
  uint8_t startOfLineIndex = 0;
  SI_BIT(receivedResponseHeader) = false;
  uint32_t responseLength = 0;
  uint32_t numDataBytesReceived = 0;

  listenerReceivedDataFlag = false;
  listenerReceivedEntireResponseFlag = false;

  while (1)
  {
    // Receive 1 character
    UART1_readBuffer(listenerBuffer, 1);
    while (!listenerReceivedDataFlag);
    listenerReceivedDataFlag = false; // Clear the flag. It will get set again in UART1_receiveCompleteCb()

    // Copy one byte from the listener buffer into the receive buffer
    thisCharacter = listenerBuffer[0];
    BGX_receiveBuffer[index] = thisCharacter;
    index++;

    // If we've received the response header, start keeping track of how many actual data bytes have been received
    if (receivedResponseHeader) {
      numDataBytesReceived += 1;
    }

    // Only process the data after receiving a full line of data
    if (thisCharacter == '\n')
    {
      if (BGX_receiveBuffer[startOfLineIndex] == 'R')
      {
        // This if-statement should be entered the first time '\n' is received.
        // Response headers always start with 'R' so this was the header
        // The second character on this line is the error code
        // The 3rd to 7th characters are the response length
        receivedResponseHeader = true;
        errorCode = BGX_receiveBuffer[startOfLineIndex + 1] - '0'; // Use ascii math to convert the character digit to an integer digit
        responseLength = (BGX_receiveBuffer[startOfLineIndex + 2] - '0') * 10000 // Use math instead of atoi() to get the integer version of the response length
                          + (BGX_receiveBuffer[startOfLineIndex + 3] - '0') * 1000
                          + (BGX_receiveBuffer[startOfLineIndex + 4] - '0') * 100
                          + (BGX_receiveBuffer[startOfLineIndex + 5] - '0') * 10
                          + (BGX_receiveBuffer[startOfLineIndex + 6] - '0');
      }
      else if (receivedResponseHeader && (responseLength - numDataBytesReceived) == 0)
      {
        // This if-statement should be entered the last time '\n' is received.
        // Most of the time that means the 2nd time '\n' is received, but if
        // there are multiple lines of received data, like with the scan()
        // command, then this if-statement will only be entered the last '\n'.
        // Now that we've received the response header as well as all of the data bytes, we can exit
        listenerReceivedEntireResponseFlag = true;
        break;
      }
      // The next character will be the start of the next line.
      // This gets updated every time we receive a '\n'
      startOfLineIndex = index;
    }
  }

  return errorCode;
}

uint8_t BGX_didReceiveLine(const char *buff)
{
  uint8_t index = 0;
  uint8_t lastCharacter = 0;
  uint8_t thisCharacter = 0;
  uint8_t returnValue = 255;
  uint8_t startOfLineIndex = 0;
  SI_BIT(receivedResponseHeader) = false;

  while (1)
  {
    // Receive 1 character
    UART1_readBuffer(listenerBuffer, 1);
    while (!listenerReceivedDataFlag);
    // Flag is set in UART peripheral driver callback ISR
    listenerReceivedDataFlag = false;

    lastCharacter = thisCharacter;
    thisCharacter = listenerBuffer[0];
    BGX_receiveBuffer[index] = thisCharacter;
    index++;

    // Make decisions after BGX completes transmission of line
    if (thisCharacter == '\n')
    {
      if (strstr(BGX_receiveBuffer, buff))
      {
        returnValue = 1;
        break;
      }
      else
      {
        returnValue = 0;
        break;
      }
    }
  }

  return returnValue;
}

void listenerOn(void)
{
  listenerEnabled = true;
  listenerReset();
  UART1_readBuffer(listenerBuffer, 1);
}

void listenerOff(void)
{
  listenerEnabled = false;
}

void listenerReset(void)
{
  listenerNumBytesReceived = 0;
  listenerReceivedLineEndFlag = false;
}

bool listenerReceivedLineEnd(void)
{
  return listenerReceivedLineEndFlag;
}

bool listenerReceivedEntireResponse(void)
{
  return listenerReceivedEntireResponseFlag;
}

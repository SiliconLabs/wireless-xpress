/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

#include <SI_EFM8UB1_Register_Enums.h>
#include <STDIO.h> // Used for sprintf()
#include <string.h>
#include "bsp.h"
#include "draw.h"
#include "light.h"
#include "bgx.h"
#include "tick.h"
#include "BGX_uart.h"

// Image file includes for creating sprite structs

#include "led_control.h"

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/
// State set of PB1, which controls pairing state
enum
{
  PAIRING_DISABLED,
  PAIRING_ENABLED
};

// Set of possible PHYs during connection
enum
{
  PHY_UNKNOWN,
  PHY_125K,
  PHY_1M
};
/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

SI_SEGMENT_VARIABLE( last_command_received[10], static uint8_t, SI_SEG_XDATA);
SI_SEGMENT_VARIABLE(connection_status, static uint8_t, SI_SEG_XDATA);
SI_SEGMENT_VARIABLE(pairing_status, static uint8_t, SI_SEG_XDATA);
SI_SEGMENT_VARIABLE(light_connectedPHY, uint8_t, SI_SEG_XDATA);

// Flag for telling us when push button 0 is idle (i.e. not pushed).
// This is useful for non-blocking polling
static SI_BIT(PB0_idle) = false;
static SI_BIT(PB1_idle) = true;

/*****************************************************************************/
/* BGX light mode-specific functions                                         */
/*****************************************************************************/

/***************************************************************************//**
 * @brief
 *    Function disables pairing if connected
 ******************************************************************************/
void Light_BGXdisablePairing(void)
{
  if (!BGX_isConnected())
  {
    BGX_Write("set bl e p off\r\n");
    pairing_status = PAIRING_DISABLED;
  }
}

/***************************************************************************//**
 * @brief
 *    Enables pairing if connected
 ******************************************************************************/
void Light_BGXenablePairing(void)
{
  if (!BGX_isConnected())
  {
    BGX_Write("set bl e p any\r\n");
    pairing_status = PAIRING_ENABLED;
  }
}
/***************************************************************************//**
 * @brief
 *    Configures BGX to multiset advertising, LR preferred
 ******************************************************************************/
void Light_configureBGX(void)
{

  BGX_Write("set bl p m 1\r\n");      // Enable multiset advertising
  BGX_Write("set bl p p 125k\r\n");   // Set preferred 125K LR phy
  Light_BGXdisablePairing();          // Disable pairing
  BGX_Write("adv high\r\n");          // Turn on advertising
}

/***************************************************************************//**
 * @brief
 *    Draws selection screen immediately after startup.
 ******************************************************************************/
void Light_drawTitleScreen(void)
{

  SI_SEGMENT_VARIABLE(textHeader[], uint8_t, SI_SEG_XDATA) =
  " Long range PHY demo ";

  SI_SEGMENT_VARIABLE(textBody[], uint8_t, SI_SEG_XDATA) =
  "\r\n Switch       Light \n   PB1         PB0";

  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBody(textBody);
}

/***************************************************************************//**
 * @brief
 *    Draw the light mode screen
 ******************************************************************************/
void Light_drawScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[MAX_TEXT_HEADER_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(textFooter[MAX_TEXT_FOOTER_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(textBody[MAX_TEXT_BODY_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);
  uint16_t char_count;

  sprintf(textHeader, "Light Mode");

  // Text body renders as:
  // Connection: <none/LR PHY/1M/2M PHY>
  // Pairing: <Disabled/Enabled>
  //
  char_count = sprintf(textBody, "Connection:");

  if (connection_status == BGX_DISCONNECTED)
  {
    char_count += sprintf(textBody + char_count, "%s", " none");
    sprintf(textFooter, "PB0: clear bond\nPB1: hold to pair");
  }
  else
  {
    switch (light_connectedPHY)
    {
      case (PHY_UNKNOWN):
        char_count += sprintf(textBody + char_count, "%s", " ");
        break;
      case (PHY_125K):
        char_count += sprintf(textBody + char_count, "%s", " LR PHY");
        break;
      case (PHY_1M):
        char_count += sprintf(textBody + char_count, "%s", " 1M/2M PHY");
        break;
    }
    // Disallow clearing bond/pair controls when connected
    sprintf(textFooter, " \n ");
  }

  char_count += sprintf(textBody + char_count, "\nPairing: ");
  if (pairing_status == PAIRING_DISABLED)
  {
    char_count += sprintf(textBody + char_count, "Disabled");
  }
  else
  {
    char_count += sprintf(textBody + char_count, "Enabled");
  }

  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBody(textBody);
  drawTextFooter(textFooter);
}

/***************************************************************************//**
 * @brief
 *    Resets screen
 ******************************************************************************/
void Light_reset(void)
{
  eraseScreen();
  connection_status = BGX_isConnected();
  pairing_status = PAIRING_DISABLED;
  LED_turnOff();
}

/***************************************************************************//**
 * @brief
 *    The main() for light mode
 ******************************************************************************/
void Light_main(void)
{
  uint8_t receivedCommand;
  uint8_t new_connection_status;
  uint16_t tick_count;

  // Make sure the user has released the PB0 button
  while (BSP_PB0 == BSP_PB_PRESSED)
    ;
  Light_configureBGX();
  // Erase the screen and draw the  machine
  Light_reset();

  Light_drawScreen();

  // Used for a screen update indicating PHY connected
  tick_count = GetTickCount();
  light_connectedPHY = PHY_UNKNOWN;
  while (1)
  {

    // Button monitoring for light mode
    // Note: Button polling is done this way so that there are no
    // blocking waits in this while (1) loop. This allows the BGX slave device
    // to always be able to check if it received any commands from the host
    // device.

    // PB0 clears bonds when pressed
    if ((BSP_PB0 == BSP_PB_PRESSED) && (PB0_idle))
    {
      PB0_idle = false;
      BGX_clearBonds();
    }
    if (BSP_PB0 == BSP_PB_UNPRESSED)
    {
      PB0_idle = true;
    }

    // PB1 enables pairing when held, disables otherwise
    if ((BSP_PB1 == BSP_PB_PRESSED) && (PB1_idle))
    {
      PB1_idle = false;
      Light_BGXenablePairing();
      Light_drawScreen();
    }
    if ((BSP_PB1 == BSP_PB_UNPRESSED) && (!PB1_idle))
    {
      Light_BGXdisablePairing();
      PB1_idle = true;
      Light_drawScreen();
    }

    // BGX listening for light mode
    if (listenerReceivedLineEnd())
    {
      // Note: this assumes commands are a single character
      sscanf(BGX_receiveBuffer, "%c", &receivedCommand);

      // Process received command
      switch (receivedCommand)
      {

        // BGX slave device received a command from
        // the host telling it to reset its inventory
        case ('0'):
          LED_turnOff();
          break;
          // BGX slave device received a command from the host
          // telling it to send its inventory amount back
        case ('1'):
          LED_turnOn();
          break;
        case ('2'):
          LED_changeColor(LED_GREEN);
          break;
        case ('3'):
          LED_changeColor(LED_RED);
          break;
        case ('B'):
          light_connectedPHY = PHY_125K;
          // Do nothing if the BGX slave device received an
          // unrecognized command
        default:
          break;
      }

      // Note: at the end of processing, always reset the listener
      listenerReset();

      // Update text on-screen in case anything has changed
      Light_drawScreen();
    }

    // Check state of connection pin and take action if state has changed
    new_connection_status = BGX_isConnected();
    if (connection_status != new_connection_status)
    {
      // If true, BGX_CONNECTED->BGX_DISCONNECTED has just happened
      if ((connection_status == BGX_CONNECTED))
      {
        Light_BGXdisablePairing();
      }
      else
      {
        // Used for a screen update indicating PHY connected
        tick_count = GetTickCount();
        // Indicate pairing is effectively disabled because the module
        // can only connect to one central at a time
        pairing_status = PAIRING_DISABLED;
        light_connectedPHY = PHY_UNKNOWN;
      }
      connection_status = new_connection_status;
      Light_drawScreen();
    }
    if ((light_connectedPHY == PHY_UNKNOWN)
        && (GetTickCount() == (tick_count + 300)))
    {
      light_connectedPHY = PHY_1M;
      Light_drawScreen();
    }
  }
}


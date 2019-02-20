/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

#include <SI_EFM8UB1_Register_Enums.h>
#include <STRING.h>
#include <STDIO.h>
#include <STDLIB.h>
#include "bsp.h"
#include "efm8_config.h"

#include "tick.h"
#include "scanning.h"
#include "draw.h"
#include "joystick.h"
#include "BGX_uart.h"
#include "bgx.h"

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/

// This is the column where text should be displayed after the '--> ' arrow
#define END_OF_ARROW_BUFFER_COL   4

// Change this to change how long the BGX host scans for other devices
#define SCAN_TIME_S    2
#define SCAN_TIME_MS   (SCAN_TIME_S * 1000)

/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

// The device index of device we are currently connected to
// This gets set in Scanning_selectDevice()
uint8_t Scanning_connectedDeviceIndex;

/*****************************************************************************/
/* Function Prototypes                                                       */
/*****************************************************************************/

static uint8_t getJoystickDirection(void);
static void eraseOldArrow(uint8_t oldArrowPosition);
static void drawCurArrow(uint8_t curArrowPosition);
static uint8_t updateArrowPosition(uint8_t curArrowPosition,
                                   uint8_t joystickDirection);

static void waitForButton1PressAndRelease(void);



void Scanning_configureBGX(void)
{
  // Set preferred 125K long range phy
  BGX_Write("set bl p p 125k\r\n");
}


/***************************************************************************//**
 * @brief
 *    Draw the initial scanning screen
 ******************************************************************************/
void Scanning_drawScanningScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[], uint8_t, SI_SEG_XDATA) = "Scanning Mode";
  SI_SEGMENT_VARIABLE(textBody[], uint8_t, SI_SEG_XDATA) = "Scanning ...";

  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBody(textBody);
}

/***************************************************************************//**
 * @brief
 *    Draw the screen showing that no devices were found
 ******************************************************************************/
void Scanning_drawNoScanResultsScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[], uint8_t, SI_SEG_XDATA) = "Scan Results";
  SI_SEGMENT_VARIABLE(textBody[MAX_TEXT_BODY_BUFFER_SIZE], uint8_t, SI_SEG_XDATA)
  = "No devices found\nPB1: scan again";

  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBody(textBody);
}

/***************************************************************************//**
 * @brief
 *    Draw the screen showing the scan results
 ******************************************************************************/
void Scanning_drawScanResultsScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[], uint8_t, SI_SEG_XDATA) = "Scan Results";
  SI_SEGMENT_VARIABLE(textBody[MAX_TEXT_BODY_BUFFER_SIZE], uint8_t,
                      SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(textFooter[], uint8_t, SI_SEG_XDATA) =
      "Push joystick to sel\nPB0 clear bond";
  uint8_t i = 0;

  // Clear textBody so that the calls to strcat() below function properly
  for (i = 0; i < MAX_TEXT_BODY_BUFFER_SIZE; i++)
  {
    textBody[i] = 0;
  }

  // Get the scan result names and append a linefeed "\n" to each one
  for (i = 0; i < BGX_numDevicesFound; i++)
  {
    strcat(textBody, &BGX_scanResultsParsed[i]);
    strcat(textBody, "\n");
  }

  // Display the text header, scan results, and text footer
  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBodyAtCol(textBody, END_OF_ARROW_BUFFER_COL);
  drawTextFooter(textFooter);
}

/***************************************************************************//**
 * @brief
 *    Draw the screen showing the scan results
 ******************************************************************************/
void Scanning_drawScanFailureScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[], uint8_t, SI_SEG_XDATA) = "Connection Error";
  SI_SEGMENT_VARIABLE(textBody[MAX_TEXT_BODY_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(textFooter[], uint8_t, SI_SEG_XDATA) =
      "PB0: clear bond\ninfo and try again";
  uint8_t i = 0;

  sprintf(textBody, "Security mismatch\nDid you hold PB1 on\nnew light to pair?\n  \n");

  // Display the text header, scan results, and text footer
  drawTextBeforeHeader(BGX_deviceName);
  drawTextHeader(textHeader);
  drawTextBody(textBody);
  drawTextFooter(textFooter);
}

/***************************************************************************//**
 * @brief
 *    Draw the screen showing that the BGX host device has been connected to a
 *    BGX slave device
 ******************************************************************************/
void Scanning_drawConnectedScreen(void)
{
  SI_SEGMENT_VARIABLE(textHeader[MAX_TEXT_HEADER_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);

  SI_SEGMENT_VARIABLE(textBody[MAX_TEXT_BODY_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(textFooter[MAX_TEXT_FOOTER_BUFFER_SIZE],
                      uint8_t,
                      SI_SEG_XDATA);
  SI_SEGMENT_VARIABLE(char_count, uint8_t, SI_SEG_XDATA);
  // Draw the device name the line above the text header
  drawTextBeforeHeader(BGX_deviceName);

  // Draw the two header lines
  char_count = sprintf(textHeader, "Connected: ");
  sprintf(textHeader + char_count,
          BGX_scanResultsParsed[Scanning_connectedDeviceIndex - 1]);
  drawTextHeader(textHeader);

  char_count = sprintf(textBody, "LED Joystick control\n");
  char_count += sprintf(textBody + char_count, "\n\n         ON\n");
  char_count += sprintf(textBody + char_count, "    GREEN + RED\n");
  char_count += sprintf(textBody + char_count, "         OFF\n");

  drawTextBody(textBody);

  // Draw the text footer
  strcpy(textFooter, "PB1: disconnect");
  drawTextFooter(textFooter);
}

/***************************************************************************//**
 * @brief
 *    Draw the current arrow
 *
 * @param curArrowPosition [in]
 *    The current arrow position (i.e. the position after moving the joystick)
 ******************************************************************************/
static void drawCurArrow(uint8_t curArrowPosition)
{
  // This buffer will include an arrow symbol as well as
  // a device name in the following format: "--> DeviceName"
  SI_SEGMENT_VARIABLE(arrowWithDeviceNameBuffer[MAX_LCD_COLS], uint8_t, SI_SEG_XDATA) =
  {
    "--> "
  };

  // Attach the device name at the end of the arrow buffer using pointer arithmetic
  strcpy(&arrowWithDeviceNameBuffer[END_OF_ARROW_BUFFER_COL],
         &BGX_scanResultsParsed[curArrowPosition]);

  // Redraw the line at the current arrow position to include an arrow symbol
  // '-->' with the device name attached
  drawText(arrowWithDeviceNameBuffer,
      SCREEN_BODY_START_ROW + (curArrowPosition * LINE_SPACING),
      0);
}

/***************************************************************************//**
 * @brief
 *    Erase the old arrow
 *
 * @param oldArrowPosition [in]
 *    The old arrow position before the joystick was moved
 ******************************************************************************/
static void eraseOldArrow(uint8_t oldArrowPosition)
{
  // Redraw the line at the old arrow position to only show the device name
  // (i.e. no arrow)
  drawText(&BGX_scanResultsParsed[oldArrowPosition],
  SCREEN_BODY_START_ROW + (LINE_SPACING * oldArrowPosition),
           END_OF_ARROW_BUFFER_COL);
}

/***************************************************************************//**
 * @brief
 *    Update the arrow position on the LCD
 *
 * @param curArrowPosition [in]
 *    The current arrow position
 *
 * @param joystickDirection [in]
 *    The joystick input
 *
 * @return
 *     The new arrow position
 *
 * @note
 *    The arrow position uses 0-based indexing where as the device index received
 *    from the scan command uses 1-based indexing
 ******************************************************************************/
static uint8_t updateArrowPosition(uint8_t curArrowPosition,
                                   uint8_t joystickDirection)
{
  // Move the arrow position up if the joystick was pressed upwards
  if (joystickDirection == JOYSTICK_N)
  {
    if (curArrowPosition == 0)
    {
      // If the arrow is at the top position and the joystick was
      // move upwards, go to the bottom
      curArrowPosition = BGX_numDevicesFound - 1;
    }
    else
    {
      curArrowPosition -= 1;
    }
    // Add a delay so that the joystick input doesn't get updated too quickly
    Wait(200);
  }

  // Move the arrow position down if the joystick was pressed downwards
  else if (joystickDirection == JOYSTICK_S)
  {
    if (curArrowPosition == BGX_numDevicesFound - 1)
    {
      // If the arrow is currently at the bottom position and the
      // joystick was moved downwards, go back to the top
      curArrowPosition = 0;
    }
    else
    {
      curArrowPosition += 1;
    }
    // Add a delay so that the joystick input doesn't get updated too quickly
    Wait(200);
  }

  return curArrowPosition;
}

/***************************************************************************//**
 * @brief
 *    Wait for the user to press button 1 and then release it
 *
 * @details
 *    This function uses a blocking wait, which is why it is not used more
 *    often for button polling purposes
 ******************************************************************************/
static void waitForButton1PressAndRelease(void)
{
  while (BSP_PB1 != BSP_PB_PRESSED)
    ;
  while (BSP_PB1 != BSP_PB_UNPRESSED)
    ;
}

/***************************************************************************//**
 * @brief
 *    Execute the logic for button 1
 *
 * @details
 *    Switch back to COMMAND_MODE and disconnect from the BGX slave device
 ******************************************************************************/
static void executeButton1Logic(void)
{
  // Write the breakout sequence to switch us from STREAM_MODE to COMMAND_MODE
  // The breakout sequence is ('$$$') by default. See the "bu s s" command
  // for how to change it.  We want to switch back to command mode so that we
  // can issue commands like "scan" again to the BGX device
  BGX_Write("$$$");

  // Wait for the switch to COMMAND_MODE to be entered
  while (MODE_PIN != COMMAND_MODE)
    ;

  // We can now disconnect from the BGX slave device
  BGX_disconnect();
}

/***************************************************************************//**
 * @brief
 *    Get the direction of the joystick input
 *
 * @details
 *    Converts the joystick input to a digital value using the ADC
 *
 * @return
 *    A joystick direction:
 *      JOYSTICK_NONE --> 0 --> not pressed
 *      JOYSTICK_C    --> 1 --> center
 *      JOYSTICK_N    --> 2 --> north
 *      JOYSTICK_E    --> 3 --> east
 *      JOYSTICK_S    --> 4 --> south
 *      JOYSTICK_W    --> 5 --> west
 ******************************************************************************/
static uint8_t getJoystickDirection(void)
{
  uint32_t mV;

  ADC0CN0_ADBUSY = 1;
  while (!ADC0CN0_ADINT)
    ;
  ADC0CN0_ADINT = 0;

  mV = (ADC0 * (uint32_t) 3300) / 1023;
  return JOYSTICK_convert_mv_to_direction(mV);
}

/***************************************************************************//**
 * @brief
 *    Non-blocking joystick update function
 *
 * @details
 *    Compares previous state of joystick with new position, and
 *    debounces that new position.  New positions reported after
 *    5 consecutive reads of the same new position.
 *
 * @returns
 *    -1 if no new position is reported, or JOYSTICK_... if new position
 *    passes debounce qualification.
 ******************************************************************************/
uint8_t Scanning_didJoystickUpdate(void)
{
  xdata uint8_t xdata joystick_direction;
  static xdata uint8_t old_joystick_direction = JOYSTICK_NONE;
  static xdata uint8_t joystick_terminalCount = 0;
  static xdata uint8_t joystick_candidate_change = JOYSTICK_NONE;

  // Update the joystick direction and compare to previous qualified
  // position
  joystick_direction = getJoystickDirection();
  if ((joystick_direction != old_joystick_direction))
  {
    // If the new position matches the candidate new position
    // being qualified, increment debounce count
    if (joystick_direction == joystick_candidate_change)
    {

      joystick_terminalCount++;
      // If terminal value reached, report new position
      // and save candidate
      if (joystick_terminalCount >= 5)
      {
        joystick_terminalCount = 0;
        old_joystick_direction = joystick_direction;
        return joystick_direction;
      }
    }
    else
    {
      // New position doesn't match candidate,
      // reset count and save position as new candidate
      joystick_terminalCount = 0;
      joystick_candidate_change = joystick_direction;
    }
  }
  else
  {
    joystick_terminalCount = 0;
  }

  return -1;
}
/***************************************************************************//**
 * @brief
 *    The main() for scanning mode
 ******************************************************************************/
void Scanning_main(void)
{

  xdata uint8_t selectedDeviceIndex;
  xdata uint8_t receivedCommand;
  xdata uint8_t connect_error_code;
  xdata uint8_t desiredTokenIndices[] =
  {SCAN_RESULTS_DEVICE_NAME_INDEX};
  uint8_t curArrowPosition = 0;
  uint8_t oldArrowPosition = 0;
  uint8_t joystick_direction;
  uint8_t numDesiredTokens = sizeof(desiredTokenIndices) / sizeof(uint8_t);

  // Make sure the user has released the PB1 button
  while (BSP_PB1 == BSP_PB_PRESSED)
    ;

  Scanning_configureBGX();

  while (1)
  {
    connect_error_code = ERROR_CODE_FAIL;
    // Connection loop will not terminate until
    // scanning BGX establishes a connection with
    // a peripheral BGX
    while (connect_error_code == ERROR_CODE_FAIL)
    {
      // Loop will only break when at least one BGX
      // has been found in a scan.  Otherwise loop
      //repeats and attempts another scan
      while (1)
      {

        // Erase the screen and draw the scanning screen
        eraseScreen();

        Scanning_drawScanningScreen();

        // Scan for bluetooth devices for SCAN_TIME_MS
        // milliseconds and get the results
        BGX_scan(SCAN_TIME_MS);
        BGX_scanOff();

        // Parse scan results
        BGX_getScanResults(desiredTokenIndices, numDesiredTokens);

        // Erase the screen and draw the appropriate scan results screen
        eraseScreen();

        // Re-enter the while loop and scan again for other devices
        if (BGX_numDevicesFound == 0)
        {
          Scanning_drawNoScanResultsScreen();
          waitForButton1PressAndRelease();
        }
        else
        {
          // Exit the while loop and wait for the user to select a
          // BGX device to connect to
          Scanning_drawScanResultsScreen();
          break;
        }
      }

      selectedDeviceIndex = -1;
      oldArrowPosition = 0;
      curArrowPosition = 0;
      drawCurArrow(curArrowPosition);
      // Poll joystick until the user has selected a BGX device to connect to
      // Also monitor PB0 in case user chooses to clear bond information
      while (selectedDeviceIndex == -1)
      {
        // Check for joystick position updates
        joystick_direction = Scanning_didJoystickUpdate();
        switch (joystick_direction)
        {
          // Update cursor position among scanned BGX devices
          // if user moves joystick north or south
          case (JOYSTICK_N):
          case (JOYSTICK_S):
          {
            eraseOldArrow(oldArrowPosition);
            curArrowPosition = updateArrowPosition(curArrowPosition,
                                                   joystick_direction);
            drawCurArrow(curArrowPosition);
            oldArrowPosition = curArrowPosition;
          }
          break;
          // Set selectedDeviceIndex to choice user has made,
          // which causes the loop to terminate
          case (JOYSTICK_C):
            {
            Scanning_connectedDeviceIndex = curArrowPosition + 1;
            selectedDeviceIndex = Scanning_connectedDeviceIndex;
          }
            break;
        }

        // If user presses PB0, clear bonds and continue
        // waiting for BGX selection to be made
        if (BSP_PB0 == BSP_PB_PRESSED)
        {
          while (BSP_PB0 != BSP_PB_UNPRESSED)
            ; // Wait until button 0 is released
          BGX_clearBonds();
        }
      }

      // Attempt to connect to the selected device
      connect_error_code = BGX_connect(selectedDeviceIndex);

      // If connection was not successful because of
      // security mismatch, clear bond info and repeat the
      // connection loop
      if (connect_error_code == ERROR_CODE_FAIL)
      {
        Scanning_drawScanFailureScreen();
        while (BSP_PB0 != BSP_PB_PRESSED)
          ;
        while (BSP_PB0 != BSP_PB_UNPRESSED)
          ; // Wait until button 0 is released
        BGX_clearBonds();

      }
    }

    // Wait for the device to connect as signaled by the CONNECTION_PIN
    while (CONNECTION_PIN != CONNECTED)
      ;

    // Erase the screen and display the screen showing
    // that the two devices are connected now
    eraseScreen();
    Scanning_drawConnectedScreen();

    // Send this upon connection to indicate at the app communication level
    // that a BGX and not a phone is connected as central
    BGX_Write("B\r\n");

    // Communication loop will exit when user clicks PB1
    // to force a disconnect
    while (1)
    {

      if (listenerReceivedLineEnd())
      {
        sscanf(BGX_receiveBuffer, "%c", &receivedCommand);
      }

      if (BSP_PB0 == BSP_PB_PRESSED)
      {
        while (BSP_PB0 != BSP_PB_UNPRESSED)
          ; // Wait until button 0 is released
      }

      // If button 1 was pressed, host disconnects from the slave BGX device
      if (BSP_PB1 == BSP_PB_PRESSED)
      {
        executeButton1Logic();
        // Wait until button 1 is released to force a disconnect
        while (BSP_PB1 != BSP_PB_UNPRESSED)
          ;
          // Go back to the connection loop and scan again for other devices
        break;
      }
      // Poll joystick input until the user selects a device to connect to

      // If joystick movement is detected, send command to
      // connected BGX to adjust light state
      switch (Scanning_didJoystickUpdate())
      {
        {
          case (JOYSTICK_NONE):
          // Do nothing
          break;
          case (JOYSTICK_N):
          BGX_Write("1\r\n");
          break;
          case (JOYSTICK_E):
          BGX_Write("3\r\n");
          break;
          case (JOYSTICK_S):
          BGX_Write("0\r\n");
          break;
          case (JOYSTICK_W):
          BGX_Write("2\r\n");
          break;
        }
      }

    }
  }
}


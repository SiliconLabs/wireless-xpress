/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

#include <SI_EFM8UB1_Register_Enums.h>
#include <STRING.h>
#include <STDLIB.h>
#include <STDIO.h>

#include "efm8_config.h"
#include "BGX_uart.h"
#include "tick.h"
#include "bgx.h"

// TODO: figure out why DATA is so big

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/

// Index defines for the command response header
// Format: RXYYYYY\r\n<response data>
#define RESPONSE_R_INDEX             0
#define RESPONSE_ERROR_CODE_INDEX    1
#define RESPONSE_DATA_LENGTH_INDEX   2
#define RESPONSE_DATA_INDEX          9

// Format length defines for the command response header
// (e.g. the data length section in the response header is 5 characters)
#define RESPONSE_ERROR_CODE_FORMAT_LENGTH    1
#define RESPONSE_DATA_LENGTH_FORMAT_LENGTH   5

/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

// Temporary buffer for holding intermediate data and other unnecessary results
// (e.g. the result of a sprintf() call, unimportant response data, etc.)
static SI_SEGMENT_VARIABLE(tempBuffer[MAX_TEMP_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);

// An array to store all of the scan results in the following format:
// ! # RSSI BD_ADDR           Device Name
// # 1  -46 4C:55:CC:1a:3d:df Device1
// # 2  -46 4C:55:CC:1a:30:1f Device2
static SI_SEGMENT_VARIABLE(BGX_scanResultsAll[MAX_SCAN_RESULTS_ALL_SIZE], uint8_t, SI_SEG_XDATA);

// The parsed array of scan results using the parseScanResults() function
SI_SEGMENT_VARIABLE(BGX_scanResultsParsed[MAX_NUM_SCAN_DEVICES][MAX_SCAN_RESULT_LINE_SIZE], uint8_t, SI_SEG_XDATA);

// The number of devices found when performing a scan
SI_SEGMENT_VARIABLE(BGX_numDevicesFound, uint8_t, SI_SEG_XDATA);

// Buffer used to store the device name
SI_SEGMENT_VARIABLE(BGX_deviceName[MAX_DEVICE_NAME_SIZE], uint8_t, SI_SEG_XDATA);

/*****************************************************************************/
/* Function Prototypes                                                       */
/*****************************************************************************/

static void parseScanResults(uint8_t *desiredTokenIndices, uint8_t numDesiredTokens);

/***************************************************************************//**
 * @brief
 *    Initialize the BGX device
 *
 * @details
 *    Sets the device to machine mode, starts advertising, sets a GPIO pin for
 *    stream/command mode detection, sets a GPIO pin for connectivity to another
 *    device detection, saves this configuration to non-volatile flash.
 ******************************************************************************/
void BGX_init(void)
{
  BGX_RESET_PIN = BGX_RESET_PIN&0;
  Wait(20);
  BGX_RESET_PIN = BGX_RESET_PIN|1;

  if(BGX_didReceiveLine("COMMAND_MODE"))
  {
    // Indicates BGX has exited reset and can receive commands
  }
  else
  {
    // Error condition.  Should not happen in this example.  Simply fall through.
  }

  // Set BGX to machine mode to enable response headers for easier parsing
  BGX_Write("set sy c m machine\r\n");

  // Set advertising to always connect quickly
  BGX_Write("set bl v h d 30\r\n");
  BGX_Write("set bl v h i 32\r\n");
  BGX_Write("set bl v l d 0\r\n");
  BGX_Write("set bl v l i 32\r\n");

  // Route mode status indicator to GPIO 6
  // Now, MODE_PIN (see inc/config/efm8_config.h) will tell us whether we are in STREAM_MODE or COMMAND_MODE
  BGX_Write("gfu 6 none\r\n");
  BGX_Write("gfu 6 str_active_n\r\n"); // Stream mode is active low

  // Route connection status indicator to GPIO 4
  // Now, CONNECTION_PIN (see inc/config/efm8_config.h) will tell us whether we are CONNECTED or DISCONNECTED to/from a BGX host device
  BGX_Write("gfu 4 none\r\n");
  BGX_Write("gfu 4 con_active\r\n"); // Connection is active high

  // Save user configuration
  BGX_Write("save\r\n");

  BGX_Write("adv off\r\n"); // Turn off advertising at init
}

/***************************************************************************//**
 * @brief
 *    Gets the device name from the BGX device and stores it in the global
 *    variable `BGX_deviceName`.
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_getDeviceName(void)
{
  uint8_t newLineIndex;
  uint8_t errorCode;

  // Send the command to receive the device name
  BGX_Write("get sy d n\r\n");

  // Wait for data to come back
  while (!listenerReceivedEntireResponse());

  // Parse the command response
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, BGX_deviceName);

  // Remove the carriage return '\r' and linefeed '\n' from the device name
  newLineIndex = strstr(BGX_deviceName, "\r\n") - BGX_deviceName; // Find the index of the "\r\n" by using pointer arithmetic
  BGX_deviceName[newLineIndex] = 0;     // Replace the carriage return
  BGX_deviceName[newLineIndex + 1] = 0; // Replace the linefeed

  // Note: at the end of processing, always reset the listener
  listenerReset();

  return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Parse the command response.
 *
 * @details
 *    Command response format: RXYYYYY\r\n<response data>
 *    R denotes a response header
 *    X is the error code
 *    YYYYY is the length of the response data (including a trailing "\r\n" at the end of the response data)
 *    See the Command Protocol section in the BGX documentation for more details
 *
 * @note
 *    The command response is in characters, which is why we must use the atoi()
 *    function to convert the response header characters to integers. Also, the
 *    atoi() function only takes strings, so we must copy the response header
 *    characters into null-terminated string buffers.
 *
 * @note
 *    The BGX variable "sy c h" must be set to 1 to enable response headers.
 *    This is automatically done when setting the device to machine mode
 *    "set sy c m machine"
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_parseCmdResponse(uint8_t *responseBuffer, uint8_t *responseData)
{
  uint8_t responseErrorCodeStr[RESPONSE_ERROR_CODE_FORMAT_LENGTH + 1]; // Plus 1 for the null terminator
  uint8_t responseErrorCodeNum;

  uint8_t responseLengthStr[RESPONSE_DATA_LENGTH_FORMAT_LENGTH + 1]; // Plus 1 for the null terminator
  uint32_t responseLengthNum = 0;

  uint16_t i = 0;

  // Check if we received a valid response header (R)
  if (responseBuffer[RESPONSE_R_INDEX] != 'R') {
    return ERROR_CODE_FAIL;
  }

  // Get the error code (X)
  strncpy(responseErrorCodeStr, &responseBuffer[RESPONSE_ERROR_CODE_INDEX], RESPONSE_ERROR_CODE_FORMAT_LENGTH);
  responseErrorCodeNum = atoi(responseErrorCodeStr);
  sscanf(BGX_receiveBuffer, "%u", &responseErrorCodeNum);

  // Get the response length (YYYYY)
  strncpy(responseLengthStr, &responseBuffer[RESPONSE_DATA_LENGTH_INDEX], RESPONSE_DATA_LENGTH_FORMAT_LENGTH);
  responseLengthNum = atoi(responseLengthStr);

  // Use the response length to get the actual data from the response
  strncpy(responseData, &responseBuffer[RESPONSE_DATA_INDEX], responseLengthNum);
  responseData[responseLengthNum] = 0; // Null terminate the string

  return responseErrorCodeNum;


}

/***************************************************************************//**
 * @brief
 *    Enables bonding when pairing if connected
 ******************************************************************************/
void BGX_clearBonds(void)
{
   BGX_Write("clrb\r\n");
}

/***************************************************************************//**
 * @brief
 *    Start scanning for other BGX devices
 *
 * @param waitTimeMs [in]
 *    How long to scan in units of milliseconds
 *
 * @note
 *    This command simply initiates the scan. It doesn't stop the scan or give
 *    any scan results. See BGX_scanOff() and BGX_getScanResults()
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_scan(uint16_t waitTimeMs)
{
  uint8_t errorCode;

  // Send the 'scan' command
  BGX_Write("scan\r\n");

  // Allow the scan to take place for waitTimeMs milliseconds. Note: this is a blocking wait.
  Wait(waitTimeMs);

  // Process the data and command response
  while (!listenerReceivedEntireResponse()); // Wait for data to come back from the slave device
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, tempBuffer); // Put response data in temp buffer because we don't need it
  listenerReset(); // Note: at the end of processing, always reset the listener

  return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Stop scanning for other BGX devices
 *
 * @note
 *    This command simply stops the scan. It doesn't give any scan results.
 *    See BGX_getScanResults().
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_scanOff(void)
{
  uint8_t errorCode;

  // Tell the BGX device to stop scanning
  BGX_Write("scan off\r\n");

  // Process the data and command response
  while (!listenerReceivedEntireResponse()); // Wait for data to come back from the slave device
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, tempBuffer); // Put response data in temp buffer because we don't need it
  listenerReset(); // Note: at the end of processing, always reset the listener

  return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Parse the scan results received from BGX_getScanResults()
 *
 * @details
 *    Here is the expected scan results format of BGX_scanResultsAll[]:
 *
 *    ! # RSSI BD_ADDR           Device Name
 *    # 1  -46 4C:55:CC:1a:3d:df Device1
 *    # 2  -46 4C:55:CC:1a:30:1f Device2
 *
 * @details
 *    The global BGX_scanResultsAll[] buffer holds all of the scan information.
 *    However, the user might not want all of this info so this function
 *    provides a flexible means of getting parsing the scan information. The
 *    flexibility comes from allowing the user to choose which indices of the
 *    scan results they want to keep.
 *
 *    Index 0 corresponds to the '!'           column and will always be '#'
 *    Index 1 corresponds to the '#'           column and represents the device index (e.g. 1, 2, 3, etc.)
 *    Index 2 corresponds to the 'RSSI'        column and represents the received signal strength indicator
 *    Index 3 corresponds to the 'BD_ADDR'     column and represents the bluetooth device address
 *    Index 4 corresponds to the 'Device Name' column and represents the device name
 *
 *    So, if you only wanted the device name column, you would call the
 *    BGX_getScanResults() function with a pointer to an array with the contents [4]
 *
 *    This function orders the parsed information based on the order of indices
 *    provided. So, if you wanted the device name and then the bluetooth
 *    address, you would pass in a pointer to an array with the contents [4, 3]
 *
 *    Each element of the BGX_scanResultsParsed[] array corresponds to one
 *    device information and is a string. The contents of the string are
 *    separated by a single space for each given index.
 *
 *    Shown below are a few examples of how to call this function and the
 *    expected output.
 *
 *    Input1: uint8_t desiredTokenIndices[] = { 1, 4 };
 *            uint8_t numDesiredTokens = sizeof(desiredTokenIndices) / sizeof(uint8_t);
 *            BGX_getScanResults(desiredTokenIndices, numDesiredTokens);
 *
 *    Output1: ['1 DeviceName1',
 *              '2 DeviceName2']
 *
 *    Input2: uint8_t desiredTokenIndices[] = { 4, 0, 3 };
 *            uint8_t numDesiredTokens = sizeof(desiredTokenIndices) / sizeof(uint8_t);
 *            BGX_getScanResults(desiredTokenIndices, numDesiredTokens);
 *
 *    Output2: ['DeviceName1 # 4C:55:CC:1a:3d:df',
 *              'DeviceName2 # 4C:55:CC:1a:30:1f']
 *
 * @param desiredTokenIndices [in]
 *    A pointer to an array of desired token indices
 *
 * @param numDesiredTokens [in]
 *    The number of desired tokens. This should be the number of elements in the
 *    desiredTokenIndices[] array
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
static void parseScanResults(uint8_t *desiredTokenIndices, uint8_t numDesiredTokens)
{
  SI_SEGMENT_VARIABLE(scanResultsAllCopy[MAX_SCAN_RESULT_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);

  uint8_t *fullLine;
  uint8_t fullLineIndex;
  uint8_t* arrayOfFullLines[MAX_NUM_SCAN_DEVICES];

  uint8_t *token;
  uint8_t tokenIndex;
  uint8_t* arrayOfTokens[MAX_TOKENS_PER_LINE];

  uint8_t i = 0;
  uint8_t j = 0;

  uint8_t numDevicesFound = 0;

  uint8_t curDesiredTokenIndex;

  // Clear the array of scan results so the strcat() call below will function properly
  for (i = 0; i < MAX_NUM_SCAN_DEVICES; i++) {
    for (j = 0; j < MAX_SCAN_RESULT_LINE_SIZE; j++) {
      BGX_scanResultsParsed[i][j] = 0;
    }
  }

  // Split the original scan results into lines and store them in an array
  strcpy(scanResultsAllCopy, BGX_scanResultsAll); // Make a copy of the original scan results because strtok() destroys its input
  fullLine = strtok(scanResultsAllCopy, "\r\n"); // Don't use the first line because it's just the scan results header
  fullLineIndex = 0;
  while (1) {
    fullLine = strtok(0, "\r\n");
    if (fullLine == 0 || numDevicesFound >= MAX_NUM_SCAN_DEVICES) {
      break;
    }
    arrayOfFullLines[fullLineIndex++] = fullLine;
    numDevicesFound += 1;
  }

  // Record the number
  BGX_numDevicesFound = numDevicesFound;

  // Go through each line in the scan results
  for (fullLineIndex = 0; fullLineIndex < numDevicesFound; fullLineIndex++) {

    // Split the line into tokens separated by whitespace " "
    tokenIndex = 0;
    for (token = strtok(arrayOfFullLines[fullLineIndex], " "); token != 0; token = strtok(0, " ")) {
      arrayOfTokens[tokenIndex++] = token;
    }

    // Grab all of tokens at the desired token indices from the arrayOfTokens
    for (i = 0; i < numDesiredTokens; i++) {
      curDesiredTokenIndex = desiredTokenIndices[i];
      strcat(BGX_scanResultsParsed[fullLineIndex], arrayOfTokens[curDesiredTokenIndex]);
      strcat(BGX_scanResultsParsed[fullLineIndex], " ");
    }
  }
}

/***************************************************************************//**
 * @brief
 *    Get the scan results
 *
 * @details
 *    See the parseScanResults() function for details.
 *
 * @param desiredTokenIndices [in]
 *    See the parseScanResults() function for details.
 *
 * @param numDesiredTokens [in]
 *    See the parseScanResults() function for details.
 *
 * @note
 *    This function will only work if called after BGX_scan().
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_getScanResults(uint8_t *desiredTokenIndices, uint8_t numDesiredTokens)
{
  uint8_t errorCode;

  // Get the scan results from the BGX device
  BGX_Write("scan results\r\n");
  while (!listenerReceivedEntireResponse()); // Wait for data to come back from the slave device
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, BGX_scanResultsAll); // Put response data in BGX_scanResultsAll buffer
  listenerReset(); // Note: at the end of processing, always reset the listener
  if (errorCode != ERROR_CODE_SUCCESS) {
    return errorCode;
  }

  // Parse the scan results for the desired token indices
  parseScanResults(desiredTokenIndices, numDesiredTokens);

  return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Connect to another BGX device
 *
 * @param index [in]
 *    The index of the BGX device to connect to. This index is obtained from
 *    BGX_getScanResults().
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_connect(uint8_t index)
{
  uint8_t errorCode;

  // Connect to the BGX device using the given device index
  sprintf(tempBuffer, "con %u\r\n", (uint16_t)index); // Need to cast to uint16_t for "%u" format in sprintf() to work
  BGX_Write(tempBuffer);

  // Process the data and command response
  while (!listenerReceivedEntireResponse()); // Wait for data to come back from the slave device
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, tempBuffer); // Put response data in temp buffer because we don't need it
  listenerReset(); // Note: at the end of processing, always reset the listener
  if(strncmp(tempBuffer, "Success", 7) == 0)
    {
      return ERROR_CODE_SUCCESS;
    }
    else
    {
  	return ERROR_CODE_FAIL;
    }
  //return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Disconnect from the slave BGX device
 *
 * @note
 *    This command must be issued from COMMAND_MODE. If the BGX host device
 *    connects to another device, it will automatically go into STREAM_MODE.
 *    Therefore, the BGX host device must issue a breakout sequence (by default
 *    '$$$') to return to COMMAND_MODE before calling this function.
 *
 * @return
 *    A response error code (see Command Protocol under BGX documentation)
 ******************************************************************************/
uint8_t BGX_disconnect(void)
{
  uint8_t errorCode;

  // Only try to disconnect if already connected to another BGX device
  if (CONNECTION_PIN == DISCONNECTED) {
    return ERROR_CODE_FAIL;
  }

  // Send the 'disconnect' command
  BGX_Write("dct\r\n");

  // Process the data and command response
  while (!listenerReceivedEntireResponse()); //  // Wait for data to come back from the slave device
  errorCode = BGX_parseCmdResponse(BGX_receiveBuffer, tempBuffer); // Put response data in temp buffer because we don't need it
  listenerReset(); // Note: at the end of processing, always reset the listener

  return errorCode;
}

/***************************************************************************//**
 * @brief
 *    Return connection state
 *
 * @note
 *    This API returns the status of the CONNECTION_PIN, which has
 *    been configured for logic high when BGX has an active BLE link
 *    and logic low when BGX has no active BLE link.
 * @return
 *    Returns enum value BGX_CONNECTED or BGX_DISCONNECTED
 ******************************************************************************/
uint8_t BGX_isConnected(void)
{
   if(CONNECTION_PIN == DISCONNECTED)
      return BGX_DISCONNECTED;
   else
      return BGX_CONNECTED;
}

/***************************************************************************//**
 * @brief
 *    Get the inventory value from the BGX slave device
 *
 * @note
 *    The BGX host device must be connected to the slave device before calling
 *    this function
 ******************************************************************************/
uint8_t BGX_getInventoryValue(void)
{
  uint8_t receivedCommand;
  uint8_t inventoryValueBuffer[3];

  // Only send request for data if connected to another BGX device
  if (CONNECTION_PIN == DISCONNECTED) {
    return ERROR_CODE_FAIL;
  }

  // Tell the BGX slave device to send back its inventory count
  BGX_Write("g\r\n");

  // Wait for data to come back from the slave device and reset the listener
  while (!listenerReceivedLineEnd());
  listenerReset();

  // Parse the received data
  sscanf(BGX_receiveBuffer, "%c %s", &receivedCommand, &inventoryValueBuffer[0]);

  // See if the data received has the expected command header
  if (receivedCommand == 'i') {
    return atoi(inventoryValueBuffer);
  } else {
    return ERROR_CODE_FAIL;
  }
}

/***************************************************************************//**
 * @brief
 *    Send the inventory value from the BGX slave device to the BGX host
 *
 * @note
 *    The BGX host device must be connected to the slave device before calling
 *    this function
 ******************************************************************************/
uint8_t BGX_sendInventoryValue(uint8_t value)
{
  // Only send data if connected to another BGX device
  if (CONNECTION_PIN == DISCONNECTED) {
    return ERROR_CODE_FAIL;
  }

  sprintf(tempBuffer, "i %u\r\n", (uint16_t)value); // Need to cast to uint16_t for "%u" format in sprintf() to work
  BGX_Write(tempBuffer);
  // Note: Don't poll for listenerReceivedLineEnd because we don't expect a response back from the BGX host for this command.
  //       This command is a one-way data packet from the slave device to master device
  return ERROR_CODE_SUCCESS;
}

/***************************************************************************//**
 * @brief
 *    Tell the BGX slave device to reset its inventory
 *
 * @note
 *    The BGX host device must be connected to the slave device before calling
 *    this function
 ******************************************************************************/
uint8_t BGX_resetInventory(void)
{
  // Only send reset command if connected to another BGX device
  if (CONNECTION_PIN == DISCONNECTED) {
    return ERROR_CODE_FAIL;
  }

  BGX_Write("r\r\n");
  // Note: Don't poll for listenerReceivedLineEnd because we don't expect a response back from the BGX host for this command.
  //       This command is a one-way data packet from the master device to slave device

  return ERROR_CODE_SUCCESS;
}

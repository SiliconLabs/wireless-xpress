#ifndef BGX_H_
#define BGX_H_

/*****************************************************************************/
/* Defines                                                                   */
/*****************************************************************************/

// Note: these are somewhat arbitrary error code return values
#define ERROR_CODE_SUCCESS   0x00
#define ERROR_CODE_FAIL      0xFF

// TODO
#define MAX_DEVICE_NAME_SIZE        16

// TODO
#define SCAN_RESULTS_DEVICE_NUM_INDEX    1
#define SCAN_RESULTS_RSSI_INDEX          2
#define SCAN_RESULTS_BD_ADDR_INDEX       3
#define SCAN_RESULTS_DEVICE_NAME_INDEX   4

// TODO: do something with these macros
#define MAX_DEVICE_NAME_SIZE        16
#define MAX_TEMP_BUFFER_SIZE        20

#define MAX_NUM_SCAN_DEVICES        4
#define MAX_SCAN_RESULTS_ALL_SIZE   255

#define MAX_SCAN_RESULT_LINE_SIZE        40
#define MAX_SCAN_RESULT_BUFFER_SIZE      (MAX_SCAN_RESULT_LINE_SIZE * MAX_NUM_SCAN_DEVICES)
#define MAX_TOKENS_PER_LINE              5

#define SCAN_RESULTS_DEVICE_NUM_INDEX    1
#define SCAN_RESULTS_RSSI_INDEX          2
#define SCAN_RESULTS_BD_ADDR_INDEX       3
#define SCAN_RESULTS_DEVICE_NAME_INDEX   4

#define MAX_SCAN_RESULTS_POUND_SYMBOL_LENGTH   1
#define MAX_SCAN_RESULTS_DEVICE_NUM_LENGTH     2
#define MAX_SCAN_RESULTS_RSSI_LENGTH           4
#define MAX_SCAN_RESULTS_BD_ADDR_LENGTH        17
#define MAX_SCAN_RESULTS_DEVICE_NAME_LENGTH    MAX_DEVICE_NAME_SIZE

enum {
  BGX_DISCONNECTED = 0,
  BGX_CONNECTED = 1,
};

/*****************************************************************************/
/* Globals                                                                   */
/*****************************************************************************/

// Buffer used to store the device name
extern SI_SEGMENT_VARIABLE(BGX_deviceName[MAX_DEVICE_NAME_SIZE], uint8_t, SI_SEG_XDATA);

// The number of devices found when performing a scan
extern SI_SEGMENT_VARIABLE(BGX_numDevicesFound, uint8_t, SI_SEG_XDATA);

// The parsed array of scan results using the parseScanResults() function
extern SI_SEGMENT_VARIABLE(BGX_scanResultsParsed[MAX_NUM_SCAN_DEVICES][MAX_SCAN_RESULT_LINE_SIZE], uint8_t, SI_SEG_XDATA);

/*****************************************************************************/
/* Function Prototypes                                                       */
/*****************************************************************************/

// Initialize the BGX settings
void BGX_init(void);

// Get device name
uint8_t BGX_getDeviceName(void);

// Parse command response
uint8_t BGX_parseCmdResponse(uint8_t *responseBuffer, uint8_t *responseData);

// Scan functions
uint8_t BGX_scan(uint16_t waitTime);
uint8_t BGX_scanOff(void);

// Scan results functions
uint8_t BGX_getScanResults(uint8_t *desiredTokenIndices, uint8_t numDesiredTokens);

// Connect functions
uint8_t BGX_connect(uint8_t index);
uint8_t BGX_disconnect(void);

uint8_t BGX_isConnected(void);

void BGX_clearBonds(void);

#endif /* BGX_H_ */

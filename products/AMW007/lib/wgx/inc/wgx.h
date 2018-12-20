#ifndef __WGX_H__
#define __WGX_H__

/**************************************************************************//**
 * @addtogroup wgx_api WGX API
 * @{
 *****************************************************************************/

//Option macro documentation
/**************************************************************************//**
 * @addtogroup wgx_config WGX API Configuration
 * @{
 *
 * @brief
 * WGX configuration constants from wgx_config.h
 * 
 * This peripheral driver will look for configuration constants in 
 * **wgx_config.h**. This file is provided/written by the user and should be 
 * located in a directory that is part of the include path. 
 *  
 *****************************************************************************/

/**************************************************************************//**
 * @def WGX_BUFFER_SIZE
 * @brief Sets the size of the WGX UART communications buffer
 *
 * This option sets the size of the buffer used for communications with the
 * WGX module. This is the largest amount of data that can be sent and 
 * received in a single command.
 *
 * Default setting is '64' and may be overridden by defining in 'wgx_config.h'.
 *
 *****************************************************************************/

/**************************************************************************//**
 * @def WGX_LINE_BUFFER_SIZE
 * @brief Sets the size of the WGX line processing buffer
 *
 * This option sets the size of the buffer used for processing the
 * WGX_StreamReadLine function.
 *
 * Default setting is '64' and may be overridden by defining in 'wgx_config.h'.
 *
 *****************************************************************************/

/**************************************************************************//**
 * @def WGX_SYS_CLK
 * @brief Sets the system clock speed to calculate the UART baud rate
 *
 * The UART requires the system clock speed in order to calculate the
 * timing settings to generate the correct baud rate for the WGX interface.
 * This option must be configured correctly for the interface to function.
 *
 * Default setting is '24.5 MHz' and may be overridden by defining in 'wgx_config.h'.
 *
 *****************************************************************************/

/**************************************************************************//**
 * @def WGX_MAX_STREAMS
 * @brief Sets the maximum number of streams that can be used
 *
 * This option sets the number of simultaneous streams the application can 
 * use. Most read or write operations require the use of a stream. Each stream
 * consumes part of the XDATA memory.
 *
 * Default setting is '4' and may be overridden by defining in 'wgx_config.h'.
 *
 *****************************************************************************/

/**************************************************************************//**
 * @def WGX_CMD_TIMEOUT
 * @brief Sets the default timeout used for WGX commands
 *
 * This option sets the command timeout, in milliseconds. 
 * It is not recommended to change the default setting.
 *
 * Default setting is '1000' and may be overridden by defining in 'wgx_config.h'.
 *
 *****************************************************************************/

/**  @} (end addtogroup wgx_config) */

/////////////////////////////////////////////////////////////////////////////
// Includes
/////////////////////////////////////////////////////////////////////////////
#include "wgx_config.h"
#include "wgx_uart.h"

/////////////////////////////////////////////////////////////////////////////
// Default Configuration Options
/////////////////////////////////////////////////////////////////////////////
#define WGX_MAX_FILENAME_LEN        40
#define WGX_SSID_STR_LEN            33
#define WGX_MAC_STR_LEN             18
#define WGX_IP_STR_LEN              16
#define WGX_NETWORK_TIMEOUT     20000

/////////////////////////////////////////////////////////////////////////////
// Structs
/////////////////////////////////////////////////////////////////////////////

typedef enum
{
  WGX_STATUS_CLOSE_ASYNC      = 5,
  WGX_STATUS_CLOSED           = 4,
  WGX_STATUS_CLOSING          = 3,
  WGX_STATUS_CONNECTED        = 2,
  WGX_STATUS_CONNECTING       = 1,
  WGX_STATUS_OK               = 0,
  WGX_STATUS_ERR              = -1,
  WGX_STATUS_NO_DATA          = -2,
  WGX_STATUS_BUSY             = -3,
  WGX_STATUS_ALREADY_OPEN     = -4,
  WGX_STATUS_CONNECT_FAILED   = -5,
  WGX_STATUS_NOT_INITIALIZED  = -6,
  WGX_STATUS_TIMED_OUT        = -7,
  WGX_STATUS_NO_CONNECTION    = -8,
} wgx_status_t;

typedef enum
{
  WGX_STREAM_TYPE_NONE,
  WGX_STREAM_TYPE_CMD,
  WGX_STREAM_TYPE_FILE,
  WGX_STREAM_TYPE_HTTP,
  WGX_STREAM_TYPE_TCPC,
  WGX_STREAM_TYPE_TCPS,
  WGX_STREAM_TYPE_TLSC,
  WGX_STREAM_TYPE_TLSS,
  WGX_STREAM_TYPE_UDPC,
  WGX_STREAM_TYPE_UDPS,
  WGX_STREAM_TYPE_WEBS,
  WGX_STREAM_TYPE_WEBC,
} wgx_stream_type_t;


typedef enum
{
  WGX_PROTOCOL_UDP,
  WGX_PROTOCOL_TCP,
  WGX_PROTOCOL_TLS,
} wgx_protocol_t;

typedef int8_t wgx_stream_t;

/////////////////////////////////////////////////////////////////////////////
// Prototypes
/////////////////////////////////////////////////////////////////////////////

/**************************************************************************//**
 * @addtogroup wgx_general General Functions
 * @{
 *****************************************************************************/

/***************************************************************************//**
 * @brief
 * Initialize the WGX interface.
 *
 * Initializes the WGX interface library and the UART.
 *
 * @note The WGX module will be rebooted. This function will wait for the
 * reboot to complete, which may take up to 3 seconds. If the watchdog is 
 * enabled, ensure it has a sufficient timeout to prevent a system reboot
 * while the WGX module is being initialized.
 *
 * @warning Do not call any WGX functions before calling WGX_Init.
 *
 ******************************************************************************/
void WGX_Init(void);

/***************************************************************************//**
 * @brief
 * WGX communications background task
 *
 * Performs management and maintenance functions within the WGX library,
 * including processing of asynchronous commands such as network connections
 * and sleep mode.
 *
 * @note This function must be called within the application's main loop at
 * least once per second.
 *
 ******************************************************************************/
void WGX_Poll(void);

/***************************************************************************//**
 * @brief
 * Checks if WGX module is busy
 *
 * @return
 * True if module is busy processing a command. False if module is ready.
 * 
 ******************************************************************************/
bool WGX_Busy(void);

/***************************************************************************//**
 * @brief
 * Reboot WGX module
 *
 * Reboots WGX module.
 *
 * @note This function may take several seconds to complete. If the watchdog
 * is enabled, it must have an adequate timeout period to prevent a system reset.
 *
 * @note This function will close and reset all streams.
 *
 ******************************************************************************/
void WGX_Reboot(void);

/***************************************************************************//**
 * @brief
 * Sends a command with parameters
 *
 * Format a command, send it to the module, and wait for a response.
 * This function is a generic command handler that can be used to send
 * any command to the WGX module that does not already have a function
 * in this library.
 *
 * @param cmd0
 * First part of command to send. Passing null will skip this parameter.
 *
 * @param cmd1
 * Second part of command to send. Passing null will skip this parameter.
 *
 * @param param0
 * First part of command parameter to send. Passing null will skip this parameter.
 *
 * @param param1
 * Second part of command parameter to send. Passing null will skip this parameter.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Cmd(char* cmd0,
                     char* cmd1, 
                     char* param0, 
                     char* param1);

/**  @} (end addtogroup wgx_general) */

/**************************************************************************//**
 * @addtogroup wgx_sleep Sleep Functions
 * @{
 *****************************************************************************/

/***************************************************************************//**
 * @brief
 * Put the WGX module in sleep mode.
 *
 * @param seconds
 * Number of seconds the module will sleep.
 *
 * @note
 * The module will report busy while it is asleep and no further commands can be
 * sent until the module wakes up.
 *
 * @note
 * This function only acts on the WGX module. The EFM8 MCU can continue
 * to operate while the module is asleep.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Sleep(uint16_t seconds);

/***************************************************************************//**
 * @brief
 * Checks if WGX module is sleeping
 *
 * @note
 * If the module is asleep, this function will also call WGX_Poll. This
 * allows the application to busy wait on this function. 
 *
 * @return
 * True if module is asleep. False if module is awake.
 * 
 ******************************************************************************/
bool WGX_IsSleeping(void);

/**  @} (end addtogroup wgx_sleep) */

/**************************************************************************//**
 * @addtogroup wgx_config_var Configuration Variable Functions
 * @{
 *****************************************************************************/

/**************************************************************************//**
 * @brief
 * Set a variable on the module.
 *
 * @param variable
 * Name of variable to set. This must be a null terminated string.
 *
 * @param param
 * Parameter to set. This must be a null terminated string.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Set(char* variable, char* param);

/**************************************************************************//**
 * @brief
 * Get a variable from the module.
 *
 * Gets a variable from the module and stores the result in the given pointer.
 * If max_len is set to 1, this function will store a single character result.
 * If max_len is greater than 1, this function will store a string result and
 * will ensure the string is null terminated.
 *
 * @param variable
 * Name of variable to get. This must be a null terminated string.
 *
 * @param result
 * Pointer to string buffer to store the result.
 *
 * @param max_len
 * Maximum length of result. This should be the size of the result buffer.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Get(char* variable, char* result, uint8_t max_len);

/**  @} (end addtogroup wgx_config_var) */


/**************************************************************************//**
 * @addtogroup wgx_client_server Network Client/Server Functions
 * @{
 *****************************************************************************/

/**************************************************************************//**
 * @brief
 * Open a client network connection
 *
 * Opens a client network connection for selected protocol. This is an
 * asynchronous command, as some network connections may take several seconds
 * to establish (or fail). If this function returns WGX_STATUS_OK, the connection
 * attempt is in progress and the module will report busy until it is complete.
 * The stream will report its status as WGX_STATUS_CONNECTING until the connection
 * attempt is completed. The background processing for the connection is performed
 * by WGX_Poll.
 *
 * @note
 * See WGX_Poll for information on how to properly maintain the internal 
 * mechanisms for asynchronous commands.
 *
 * @param protocol
 * Network protocol to use.
 *
 * @param stream
 * Stream number to use for this connection.
 *
 * @param ip
 * IP address of remote host to connect to.
 *
 * @param port
 * Port of remote host to connect to.
 *
 * @param local_port
 * Local port to use for connection. If 0, the module will randomly select
 * a port number.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_ALREADY_OPEN if the stream has already been opened.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy or is not connected to a network.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Client(wgx_protocol_t protocol,
                        wgx_stream_t stream,
                        char* ip, 
                        uint16_t port, 
                        uint16_t local_port);

/**************************************************************************//**
 * @brief
 * Start a server
 *
 * Starts a server for the selected protocol.
 *
 * @note
 * Only one server can be running per protocol. Each client connection will
 * a separate stream.
 *
 * @param protocol
 * Network protocol to use.
 *
 * @param local_port
 * Local port to use for server.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_NO_CONNECTION if the module is not connected to a network.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_StartServer(wgx_protocol_t protocol, uint16_t local_port);

/**************************************************************************//**
 * @brief
 * Stop a server
 *
 * Stops a server for the selected protocol.
 *
 * @note
 * This function will close all open client streams that were connected to
 * the server.
 *
 * @param protocol
 * Network protocol to use.
 *
 * @param local_port
 * Local port of server to shut down.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_StopServer(wgx_protocol_t protocol, uint16_t local_port);


/**************************************************************************//**
 * @brief
 * Accept a client connection on a server
 *
 * Accepts a client connection on a server running with the selected protocol.
 * This function will poll the module for new client connections on the 
 * selected protocol. Only one client connection can be accepted at a time. If
 * it is necessary to handle multiple simultaneous connections, this function 
 * must be called again with a different stream for each desired connection.
 *
 * @note
 * This function will not check if the server has been started. If the server
 * is not running, this function will indicate there is no client connection
 * available.
 *
 * @param protocol
 * Network protocol to use.
 *
 * @param stream
 * Stream to use for client connection.
 *
 * @return
 * WGX_STATUS_OK if a client connection was accepted.
 * 
 * @return
 * WGX_STATUS_NO_CONNECTION if there was no client connection.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy or not connected to a network.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_AcceptConnection(wgx_protocol_t protocol,
                                  wgx_stream_t stream);

/**  @} (end addtogroup wgx_client_server) */

/**************************************************************************//**
 * @addtogroup wgx_stream Stream Functions
 * @{
 *****************************************************************************/

/**************************************************************************//**
 * @brief
 * Read data from a stream
 *
 * Reads data from stream and returns the number of bytes read.
 *
 * @note
 * Some streams will be closed automatically by the module, such as a TCP
 * connection being closed by the remote host. In that case, this will function
 * will allow reading of all remaining data on the stream and will return -1
 * when no more data is available and the stream is closed.
 *
 * @note
 * The maximum amount of data read cannot exceed WGX_BUFFER_SIZE. If a larger
 * read length is requested, it will be limited to WGX_BUFFER_SIZE.
 *
 * @param stream
 * Stream to read.
 *
 * @param dest
 * Pointer to buffer to store stream data.
 *
 * @param len
 * Amount of data to read from stream.
 *
 * @return
 * Number of bytes read from stream. Returns 0 if the module is busy or no data
 * was available to read.  Returns a negative result if the stream was closed or
 * if an invalid stream was given.
 * 
 ******************************************************************************/
int16_t WGX_StreamRead(wgx_stream_t stream, 
                       uint8_t* dest, 
                       uint8_t len);

/**************************************************************************//**
 * @brief
 * Write data to a stream
 *
 * Writes data to a stream and returns the number of bytes written.
 *
 * @note
 * The maximum amount of data written cannot exceed WGX_BUFFER_SIZE. If a larger
 * write length is requested, it will be limited to WGX_BUFFER_SIZE.
 *
 * @param stream
 * Stream to write.
 *
 * @param src
 * Pointer to buffer to write to stream.
 *
 * @param len
 * Amount of data to write to stream.
 *
 * @return
 * Number of bytes written to stream. Returns 0 if the module is busy or no data
 * could be written. Returns a negative result an invalid stream was given.
 * 
 ******************************************************************************/
int16_t WGX_StreamWrite(wgx_stream_t stream, 
                        uint8_t* src, 
                        uint8_t len);

/**************************************************************************//**
 * @brief
 * Write a string to a stream
 *
 * Writes a string to a stream and returns the number of bytes written. The
 * string must be null terminated.
 *
 * @note
 * The maximum amount of data written cannot exceed WGX_BUFFER_SIZE. If a larger
 * write length is requested, it will be limited to WGX_BUFFER_SIZE.
 *
 * @param stream
 * Stream to write.
 *
 * @param src
 * Pointer to string to write to stream.
 *
 * @return
 * Number of bytes written to stream. Returns 0 if the module is busy or no data
 * could be written. Returns a negative result an invalid stream was given.
 * 
 ******************************************************************************/
int16_t WGX_StreamWriteStr(wgx_stream_t stream, 
                           char* src);


/**************************************************************************//**
 * @brief
 * Check if a stream is busy.
 *
 * Checks if a stream is busy. Streams can be busy if they are in the process
 * of connecting or disconnecting to a remote host over the network.
 *
 * @param stream
 * Stream to check.
 *
 * @return
 * True if stream is busy.
 * 
 ******************************************************************************/
bool WGX_StreamBusy(wgx_stream_t stream);

/**************************************************************************//**
 * @brief
 * Wait while stream is busy.
 *
 * Waits while a stream is busy. WGX_Poll will be called until the stream is
 * no longer busy. This is useful for waiting until a stream has established
 * a network connection. 
 *
 * @param stream
 * Stream to wait on.
 *
 * @return
 * WGX_STATUS_OK if stream is no longer busy.
 *
 * @return
 * WGX_STATUS_ERR if an invalid stream was given.
 *
 * @return
 * WGX_STATUS_TIMED_OUT if the stream's operation timed out.
 * 
 ******************************************************************************/
wgx_status_t WGX_StreamWait(wgx_stream_t stream);

/**************************************************************************//**
 * @brief
 * Close a stream
 *
 * Closes a stream. Network streams will be closed asynchronously by WGX_Poll.
 *
 * @note
 * Some network streams may take several seconds to properly close. While this
 * function will return immediately, background processing in WGX_Poll may be 
 * necessary to complete the close process. It is recommended to check the stream
 * with WGX_StreamStatus to verify it is closed (WGX_STATUS_CLOSED) before
 * reusing the stream for another purpose.
 *
 * @param stream
 * Stream to close.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_StreamClose(wgx_stream_t stream);


/**************************************************************************//**
 * @brief
 * Get current status of a stream
 *
 * @param stream
 * Stream to check.
 *
 * @return
 * WGX_STATUS_OK if there is no error and no other status is more applicable.
 *
 * @return
 * WGX_STATUS_CLOSE_ASYNC if the stream is waiting for be closed asynchronously
 * by the polling loop.
 *
 * @return
 * WGX_STATUS_CLOSING if the stream is currently closing and waiting on a
 * network exchange to complete.
 *
 * @return
 * WGX_STATUS_CLOSED if the stream is closed.
 *
 * @return
 * WGX_STATUS_CONNECTING if the stream is connecting to a remote host.
 *
 * @return
 * WGX_STATUS_CONNECTED if the stream is connecting to a remote host.
 *
 * @return
 * WGX_STATUS_CONNECT_FAILED if the connection attempt failed.
 *
 * @return
 * WGX_STATUS_NO_CONNECTION if no connection was available.
 *
 * @return
 * WGX_STATUS_ERR if an error condition has occurred.
 *
 ******************************************************************************/
wgx_status_t WGX_StreamStatus(wgx_stream_t stream);

/**  @} (end addtogroup wgx_stream) */

/**************************************************************************//**
 * @addtogroup wgx_file File Functions
 * @{
 *****************************************************************************/


/**************************************************************************//**
 * @brief
 * Open a file
 *
 * Open a file for reading or writing. The file must already exist on the module's
 * file system.
 *
 * @param stream
 * Stream to use for reading/writing.
 *
 * @param filename
 * Name of file to open.
 *
 * @return
 * WGX_STATUS_OK if the file opened successfully.
 *
 * @return
 * WGX_STATUS_ERR if the command fails or the file does not exist.
 * 
 ******************************************************************************/
wgx_status_t WGX_OpenFile(wgx_stream_t stream, char* filename);

/**************************************************************************//**
 * @brief
 * Create a file
 *
 * Create a file. This will create a new file of size bytes on the module file
 * system. It will also open a stream to read or write the file.
 *
 * @param stream
 * Stream to use for reading/writing.
 *
 * @param filename
 * Name of file to create.
 *
 * @param size
 * Size of file in bytes.
 * 
 * @return
 * WGX_STATUS_OK if the file opened successfully.
 *
 * @return
 * WGX_STATUS_ERR if the command fails or the file does not exist.
 * 
 ******************************************************************************/
wgx_status_t WGX_CreateFile(wgx_stream_t stream, char* filename, uint16_t size);

/**************************************************************************//**
 * @brief
 * List files on module file system.
 *
 * Start a file listing from the module's file system. This prepares the stream,
 * but does not return any data. The application must call WGX_ParseFileList to
 * receive the file list data.
 *
 * @note
 * This function requires the use of an internal buffer for processing the
 * responses. As is only one buffer available, to prevent buffer corruption it 
 * is recommended to read the entire file list before sending any other 
 * commands.
 *
 * @param stream
 * Stream to use for reading the file list.
 *
 * @return
 * WGX_STATUS_OK if the file opened successfully.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_ListFiles(wgx_stream_t stream);

/**************************************************************************//**
 * @brief
 * Parse file listing
 *
 * Reads a single file entry from the module. The entry consists of the file
 * name and length.
 *
 * This function returns WGX_STATUS_NO_DATA when the last entry has been read.
 *
 * @note
 * This function requires the use of an internal buffer for processing the
 * responses. As is only one buffer available, to prevent buffer corruption it 
 * is recommended to read the entire file list before sending any other 
 * commands.
 *
 * @param stream
 * Stream to use for reading the file list.
 *
 * @param filename
 * Destination buffer for filename. The buffer must be at least 
 * WGX_MAX_FILENAME_LEN bytes long. This function will ensure the filename is
 * null terminated.
 * If set to 0, reading the filename will be skipped.
 *
 * @param filename_len
 * Size of filename buffer.
 *
 * @param file_length
 * Pointer to destination variable to store file length.
 * If set to 0, reading the file length will be skipped.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 *
 * @return
 * WGX_STATUS_NO_DATA if there are no more entries in the file list.
 * 
 ******************************************************************************/
wgx_status_t WGX_ReadFileList(wgx_stream_t stream, 
                              char* filename, 
                              int8_t filename_len,
                              uint32_t *file_length);

/**  @} (end addtogroup wgx_file) */


/**************************************************************************//**
 * @addtogroup wgx_wifi Wi-Fi Functions
 * @{
 *****************************************************************************/  

/**************************************************************************//**
 * @brief
 * Check if module is connected to a network
 *
 * Returns true if module is connected to a network and has a valid IP address.
 * This function will also return true when in Soft AP mode.
 *
 * @return
 * True if connected
 *
 ******************************************************************************/
bool WGX_IsConnected(void);

/**************************************************************************//**
 * @brief
 * Connect to an access point
 *
 * Connects to an access point using the given SSID and password. This function 
 * will return immediately, however, the actual connection process may take
 * several seconds. The application will need to check WGX_IsConnected before
 * using any network functions.
 *
 * If the module is already connected to a network or in Soft AP mode, it will
 * disconnect and attempt the new connection requested by this function.
 *
 * @param ssid
 * SSID of access point to connect to
 *
 * @param pass
 * Password of access point to connect to
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Connect(char* ssid, char* pass);

/**************************************************************************//**
 * @brief
 * Start a soft access point
 *
 * Starts a soft access point using the given SSID and password.
 *
 * If the module is already connected to a network or in Soft AP mode, it will
 * disconnect and set up the new Soft AP requested by this function.
 *
 * @param ssid
 * SSID of soft access point
 *
 * @param pass
 * Password of soft access point
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_SoftAP(char* ssid, char* pass);

/**************************************************************************//**
 * @brief
 * Disconnect from network
 *
 * If the module is connected to an access point, this function will disconnect
 * it. If the module is running a Soft AP, it will be shut down.
 * 
 * This function will also close all open network streams.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Disconnect(void);

/**************************************************************************//**
 * @brief
 * Get number of clients connected to Soft AP.
 *
 * Returns number of clients connected to Soft AP.  If not in Soft AP mode,
 * this function will return 0.
 *
 * @return
 * Number of clients connected
 *
 ******************************************************************************/
uint8_t WGX_SoftAPClientCount(void);

/**************************************************************************//**
 * @brief
 * Get IP address
 *
 * Gets IP address of module. This function will ensure the the string is 
 * null terminated.
 *
 * @param ip
 * Pointer to destination buffer for IP address. The buffer must be at least
 * WGX_IP_STR_LEN bytes long to contain the IP address.
 *
 * @param len
 * Length of destination buffer.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_IP(char* ip, uint8_t len);

/**************************************************************************//**
 * @brief
 * Get SSID of current network
 *
 * Gets SSID of current network. If in soft AP mode, returns name of soft AP.
 * This function will ensure the the string is null terminated.
 *
 * @param ssid
 * Pointer to destination buffer for SSID. The buffer must be at least
 * WGX_SSID_STR_LEN bytes long to contain the IP address.
 *
 * @param len
 * Length of destination buffer.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_SSID(char* ssid, uint8_t len);

/**************************************************************************//**
 * @brief
 * Get RSSI
 *
 * Gets received signal strength. This value only has meaning if the module is
 * connected to an access point.
 *
 * @param rssi
 * Pointer to destination variable for RSSI.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_RSSI(int8_t *rssi);

/**************************************************************************//**
 * @brief
 * Scan for access points
 *
 * Scan for available access points. This command blocks while the scan is in
 * progress and takes several seconds to complete. This function does not
 * return the scan list itself. The application must call WGX_ParseWifiList
 * to read the scan list.
 *
 * @note
 * This function requires the use of an internal buffer for processing the
 * responses. As is only one buffer available, to prevent buffer corruption it 
 * is recommended to read the entire scan list before sending any other 
 * commands.
 *
 * @param stream
 * Stream to use for reading the scan list.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_ScanWifi(wgx_stream_t stream);

/**************************************************************************//**
 * @brief
 * Parse scan list
 *
 * Scan for available access points. This command blocks while the scan is in
 * progress and takes several seconds to complete. This function does not
 * return the scan list itself. The application must call WGX_ParseWifiList
 * to read the scan list.
 *
 * This function returns WGX_STATUS_NO_DATA when the last entry has been read.
 *
 * @note
 * This function requires the use of an internal buffer for processing the
 * responses. As is only one buffer available, to prevent buffer corruption it 
 * is recommended to read the entire file list before sending any other 
 * commands.
 *
 * @param stream
 * Stream to use for reading the scan list.
 *
 * @param ssid
 * Buffer to store SSID. Must be at least WGX_SSID_STR_LEN bytes in size.
 * If set to 0, SSID will not be returned.
 *
 * @param channel
 * Pointer to store channel number.
 * If set to 0, channel will not be returned.
 *
 * @param mac
 * Buffer to store MAC address. Must be at least WGX_MAC_STR_LEN bytes in size.
 * If set to 0, MAC will not be returned.
 *
 * @param rssi
 * Pointer to store signal strength.
 * If set to 0, rssi will not be returned.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 *
 * @return
 * WGX_STATUS_NO_DATA if there are no more entries in the scan list.
 * 
 ******************************************************************************/
wgx_status_t WGX_ParseWifiList(wgx_stream_t stream, 
                               char ssid[WGX_SSID_STR_LEN], 
                               uint8_t *channel, 
                               char mac[WGX_MAC_STR_LEN], 
                               int8_t *rssi);


/**  @} (end addtogroup wgx_wifi) */

/**************************************************************************//**
 * @addtogroup wgx_http HTTP Client Functions
 * @{
 *****************************************************************************/

/**************************************************************************//**
 * @brief
 * Perform HTTP Get
 *
 * Connect to an HTTP server and retrieve HTTP content.
 *
 * @note
 * Only one HTTP stream can be open at a time.
 * 
 * @param stream
 * Stream to use for reading the http data
 *
 * @param url
 * URL to retrieve.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_NO_CONNECTION if the module is not connected to a network.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Http_Get(wgx_stream_t stream, char *url);

/**************************************************************************//**
 * @brief
 * Perform HTTP Post
 *
 * Connect to an HTTP server and post data.
 *
 * @note
 * Only one HTTP stream can be open at a time.
 * 
 * @param stream
 * Stream to use for writing the data
 *
 * @param url
 * URL to connect to.
 *
 * @param content_type
 * Indicates content type being sent. This is typically a MIME type, such as
 * "application/octet-stream" for binary data, "text/plain" for plain text, or
 * "text/html" for text formatted as a web page.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_NO_CONNECTION if the module is not connected to a network.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 * 
 ******************************************************************************/
wgx_status_t WGX_Http_Post(wgx_stream_t stream, char *url, char *content_type);

/**  @} (end addtogroup wgx_http) */

/**************************************************************************//**
 * @brief
 * Request bootloader
 *
 * Reboots to bootloader and requests given filename to be loaded from the
 * module. If no filename is given (0), the bootloader will request a default
 * file.
 *
 * This function will disable global interrupts, stop the watchdig (if
 * configured), and reset the CPU.
 *  
 * @note
 * This function does not return.
 *
 * @param filename
 * Name of file to load from module.
 * 
 ******************************************************************************/
void WGX_Bootload(char *filename);

/**  @} (end addtogroup wgx_fw) */

/**************************************************************************//**
 * @addtogroup wgx_web_setup Web Setup Functions
 * @{
 *****************************************************************************/

/**************************************************************************//**
 * @brief
 * Initiate web setup
 *
 * Initiates the web setup flow to connect to a Wi-Fi AP. This will enable the
 * soft AP with the given SSID and password and enable the HTTP server to
 * server the configuration web page. After configuring a Wi-Fi AP via the web
 * page, the set up flow will attempt to connect to the AP. If the connection
 * fails, the web setup flow will be automatically restarted.
 *
 * If no SSID or password are given, the configured settings from the WGX
 * module will be used.
 * 
 * @param ssid
 * Name of soft AP. If null, uses SSID from configuration.
 * 
 * @param pass
 * Password of soft AP. If null, uses password from configuration.
 *
 * @return
 * WGX_STATUS_OK if the command completed successfully.
 *
 * @return
 * WGX_STATUS_BUSY if the module is currently busy.
 *
 * @return
 * WGX_STATUS_ERR if the command fails.
 *
 ******************************************************************************/
wgx_status_t WGX_WebSetup(char* ssid, char* pass);

/**************************************************************************//**
 * @brief
 * Check if web setup flow is active
 * 
 * @return
 * True if the web setup flow is currently active.
 * 
 ******************************************************************************/
bool WGX_IsWebSetup(void);

/**************************************************************************//**
 * @brief
 * Check if web setup flow is connecting to AP
 *
 * @return
 * True if the module is attempting to connect to the user provided AP
 * 
 ******************************************************************************/
bool WGX_IsWebSetupConnecting(void);

/**  @} (end addtogroup wgx_web_setup) */

#endif

/**  @} (end addtogroup wgx_api) */

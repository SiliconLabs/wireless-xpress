#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include "bsp.h"
#include "wgx.h"
#include "wgx_uart.h"
#include "tick.h"
#include "wgx_config.h"
#include "wgx_commands.h"
#include "sfr_page.h"

#if WGX_USE_WATCHDOG == 1
#include "wdt_0.h"
#define WDT_kick() WDT0_feed()
#define WDT_stop() WDT0_stop()
#else
#define WDT_kick()
#define WDT_stop()
#endif

/////////////////////////////////////////////////////////////////////////////
// Variables
/////////////////////////////////////////////////////////////////////////////
#define BL_SIGNATURE    0xA5

#define RX_HEADER_LEN     9
#define RX_HEADER_START   'R'

#define RESPONSE_SUCCESS              0
#define RESPONSE_COMMAND_FAILED       1
#define RESPONSE_PARSE_ERROR          2
#define RESPONSE_UNKNOWN_COMMAND      3
#define RESPONSE_TOO_FEW_ARGS         4
#define RESPONSE_TOO_MANY_ARGS        5
#define RESPONSE_UNKNOWN_VAR          6
#define RESPONSE_INVALID_ARGUMENT     7
#define RESPONSE_OVERFLOW             8
#define RESPONSE_BOUNDS_ERROR         9
#define RESPONSE_NONE                 255

// note the 2 additional bytes, this is to account for the /r/n that most responses
// will also send back.
static SI_SEGMENT_VARIABLE (buffer[RX_HEADER_LEN + WGX_BUFFER_SIZE + 2], uint8_t, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE (tx_busy, volatile uint8_t, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE (line_buffer[WGX_LINE_BUFFER_SIZE], uint8_t, SI_SEG_XDATA);

#define STATE_IDLE      0
#define STATE_HEADER    1
#define STATE_DATA      2
#define STATE_SLEEP     3
#define STATE_UNINIT    4
static SI_SEGMENT_VARIABLE (comm_state, uint8_t, SI_SEG_XDATA) = STATE_UNINIT;

#define SETUP_IDLE      0
#define SETUP_WEB       1
#define SETUP_CONNECT   2
static SI_SEGMENT_VARIABLE (setup_state, uint8_t, SI_SEG_XDATA) = SETUP_IDLE;

#define DISCONNECTED      0
#define CONNECTED_AP      1
#define CONNECTED_SOFTAP  2
static SI_SEGMENT_VARIABLE (connection_state, uint8_t, SI_SEG_XDATA) = DISCONNECTED;

static SI_SEGMENT_VARIABLE (process_stream, wgx_stream_t, SI_SEG_XDATA) = -1;

static SI_SEGMENT_VARIABLE (response_err, uint8_t, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE (response_len, uint8_t, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE (line_buffer_total_len, uint8_t, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE (line_buffer_line_len, uint8_t, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE (timeout, uint16_t, SI_SEG_XDATA) = WGX_CMD_TIMEOUT;
static SI_SEGMENT_VARIABLE (network_timeout, uint16_t, SI_SEG_XDATA);

#define U16TOA_BUF_SIZE 8
static SI_SEGMENT_VARIABLE (temp_buf[U16TOA_BUF_SIZE], uint8_t, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE (temp_buf2[U16TOA_BUF_SIZE], uint8_t, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE (failed_cmds, uint8_t, SI_SEG_XDATA);

typedef struct{
  wgx_stream_type_t type;
  int8_t handle;
  wgx_status_t status;
} wgx_stream_state_t;
static SI_SEGMENT_VARIABLE (streams[WGX_MAX_STREAMS], wgx_stream_state_t, SI_SEG_XDATA);

/////////////////////////////////////////////////////////////////////////////
// Static Function Prototypes
/////////////////////////////////////////////////////////////////////////////
static bool is_handle_open(uint8_t handle)
{
  uint8_t i;

  for (i = 0; i < WGX_MAX_STREAMS; i++)
  {
    if (streams[i].handle == handle)
    {
      return true;
    }
  }

  return false;
}

static void write_uart_buffer(SI_VARIABLE_SEGMENT_POINTER(buffer, uint8_t, EFM8PDL_UART1_RX_BUFTYPE),
                              uint8_t length)
{

  DECL_PAGE;
  SET_PAGE(UART1_SFR_PAGE);
  
  UART_writeBuffer(buffer, length);

  RESTORE_PAGE;
}

static wgx_status_t send_cmd(char *cmd0, 
                             char* cmd1, 
                       char* param0, 
                       char* param1)
{
  uint16_t len;
  uint8_t next_index = 0;

  // Copy cmd data to buffer.
  // We are using generic pointers in this function so that 
  // command and parameter strings can come from any memory segment.

  len = strlen(cmd0);
  if (len >= (WGX_BUFFER_SIZE - 2))
  {
    goto len_error;
  }

  strcpy(&buffer[next_index], cmd0);
  next_index = len;
  
  if (cmd1)
  {
    len += strlen(cmd1);
    if (len >= (WGX_BUFFER_SIZE - 2))
    {
      goto len_error;
    }

    strcpy(&buffer[next_index], cmd1);
    next_index = len;
  }

  if (param0)
  {
    len += strlen(param0);
    if (len >= (WGX_BUFFER_SIZE - 2))
    {
      goto len_error;
    }

    strcpy(&buffer[next_index], param0);
    next_index = len;
  }

  if (param1)
  {
    len += strlen(param1);
    if (len >= (WGX_BUFFER_SIZE - 2))
    {
      goto len_error;
    }

    strcpy(&buffer[next_index], param1);
  }

  // append end of line characters
  buffer[len] = '\r';
  len++;
  buffer[len] = '\n';
  len++;
    
  // begin transmission
  tx_busy = true;
  write_uart_buffer(buffer, len);

  // wait while buffered command is sent
  while (tx_busy);
  
  return WGX_STATUS_OK;

len_error:
  return WGX_STATUS_ERR;
}

static bool timed_out(uint16_t ticks)
{
  return (GetTickCount() - ticks) >= timeout;
}

static int8_t recv_response(void)
{
  uint16_t ticks = GetTickCount();

  // clear old response
  memset(buffer, 0, RX_HEADER_LEN);

  // enable RX interrupt
  UART_enableRxFifoInt(true);

  // set buffer read for entire buffer
  UART_readBuffer(buffer, sizeof(buffer));

  response_len = 0;
  response_err = RESPONSE_NONE;

  // wait for reception
  while (((sizeof(buffer) - UART_rxBytesRemaining()) < RX_HEADER_LEN) &&
         !timed_out(ticks));

  // check response header for errors or timeout
  if ((buffer[0] != RX_HEADER_START) || timed_out(ticks))
  {
    // disable RX interrupt
    UART_enableRxFifoInt(false);

    failed_cmds++;

    return -1;
  }

  // header ok

  // convert error code from character to integer
  response_err = buffer[1] - '0';

  // convert ascii response len to integer and
  // account for header
  response_len = atoi(&buffer[2]) + RX_HEADER_LEN;
  buffer[response_len] = 0; // ensure null termination

  // wait for reception
  while (((sizeof(buffer) - UART_rxBytesRemaining()) < response_len) && 
         !timed_out(ticks));

  // disable RX interrupt
  UART_enableRxFifoInt(false);
  
  if (timed_out(ticks))
  {
    failed_cmds++;

    return -1;
  }

  // check response
  if (response_err != RESPONSE_SUCCESS)
  {
    failed_cmds++;

    return -2;
  }

  // reset failed command counter
  failed_cmds = 0;

  return 0;
}

static void async_recv(void)
{
  comm_state = STATE_HEADER;

  // enable RX interrupt
  UART_enableRxFifoInt(true);

  // set buffer read for entire buffer
  UART_readBuffer(buffer, WGX_BUFFER_SIZE);
}

static int8_t parse_stream_handle(int8_t* handle, int16_t* length_remaining)
{
  uint8_t len = response_len - RX_HEADER_LEN;
  uint8_t *ptr = &buffer[RX_HEADER_LEN];

  // minimum response is something like "0,0\r\n", 5 bytes
  // sometimes we just get a handle: "0\r\n", 3 bytes
  if (len < 3)
  {
    return -1;
  }

  *handle = (int8_t)atoi(ptr);

  while ((*ptr != ',') && (len > 0))
  {
    len--;
    ptr++;
  }

  if (len == 0)
  {
    return -2;
  }

  if (length_remaining != 0)
  {
    ptr++; // increment to get past the comma
    
    *length_remaining = atoi(ptr);
  }

  return 0;
}

static bool busy(void)
{
  return comm_state != STATE_IDLE;
}

static bool not_connected(void)
{
  return connection_state == DISCONNECTED;
}

static void reset_line_buffer(void)
{
  line_buffer_total_len = 0;
  line_buffer_line_len = 0;
}

void UART_receiveCompleteCb(void)
{
  
}

void UART_transmitCompleteCb(void)
{ 
  // disable TX interrupt when transmission is complete
  UART_enableTxFifoInt(false);

  tx_busy = false;
}

static void u16toa(uint16_t i, char a[U16TOA_BUF_SIZE])
{
  char *ptr = &a[U16TOA_BUF_SIZE - 2];
  a[U16TOA_BUF_SIZE - 1] = 0; // null terminate

  do
  {
    *ptr = (i % 10) + '0';
    ptr--;
    i /= 10;
  } while (i > 0);

  // fill remainder with spaces
  while (ptr >= a)
  {
    *ptr = ' ';
    ptr--;
  }
}

static void send_char(uint8_t b)
{
  while (UART_getTxFifoCount() > 0);  
  UART_write(b);
}

static wgx_status_t stream_read(int8_t handle, uint8_t len)
{
  wgx_status_t status;

  u16toa(handle, temp_buf);
  u16toa(len, temp_buf2);

  status = WGX_Cmd(cmd_stream_read, 0, temp_buf, temp_buf2);

  if (status < 0)
  {
    return status;
  }

  // adjust data length for header
  response_len -= RX_HEADER_LEN;

  // in addition to compensating for the response header,
  // also subtract 2 additional bytes, which are the /r/n 
  // that the UART interface sends but is not part of the
  // actual stream.
  // we only need to do this if there was any response data
  // sent.  a 0 length response will not include these
  // extra characters.
  if (response_len > 2)
  {
    response_len -= 2;
  }  

  return status;
}

static uint16_t stream_read_line(wgx_stream_state_t *stream_state)
{
  int16_t read_len;
  SI_VARIABLE_SEGMENT_POINTER (ptr, uint8_t, SI_SEG_XDATA);
  SI_VARIABLE_SEGMENT_POINTER (ptr2, uint8_t, SI_SEG_XDATA);
  
  if (line_buffer_line_len > 0)
  {
    // move line buffer contents forward
    ptr = line_buffer;
    ptr2 = &line_buffer[line_buffer_line_len];
    read_len = line_buffer_total_len - line_buffer_line_len;

    while (read_len > 0)
    {
      *ptr = *ptr2;
      ptr++;
      ptr2++;
      read_len--;
    }

    line_buffer_total_len -= line_buffer_line_len;
    line_buffer_line_len = 0;
  }

  read_len = sizeof(line_buffer) - line_buffer_total_len;
  if (stream_read(stream_state->handle, read_len) == WGX_STATUS_OK)
  {
    read_len = response_len;
    memcpy(&line_buffer[line_buffer_total_len], &buffer[RX_HEADER_LEN], read_len);  
  }
  else
  {
    read_len = 0;
  }

  line_buffer_total_len += read_len;

  // check if the line buffer is empty, if so, this is the end of the stream
  if (line_buffer_total_len == 0)
  {
    // no more data in buffer, we can skip the remainder of processing
    return 0;
  }

  // scan buffer to new line
  ptr = line_buffer;
  line_buffer_line_len = 0;
  read_len = line_buffer_total_len; // reuse read length to contain entire buffer length for the line scan

  while ((*ptr != '\n') && (read_len > 0))
  {
    ptr++;
    read_len--;  
    line_buffer_line_len++;
  }

  // account for newline so on the next read we skip past it
  line_buffer_line_len++;

  ptr--; // move pointer back by 1
  *ptr = 0; // replace carriage return with null terminator

  // length includes null terminator.  subtract 1 to account for newline character we're stripping.
  return line_buffer_line_len - 1;
}

static wgx_status_t stream_list(wgx_stream_state_t *stream_state)
{
  stream_state->status = WGX_STATUS_ERR;

  if (WGX_Cmd(cmd_stream_list, 0, 0, 0) < 0)
  {
    return WGX_STATUS_ERR;  
  }

  stream_state->type = WGX_STREAM_TYPE_CMD;

  if (parse_stream_handle(&stream_state->handle, 0) < 0)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  reset_line_buffer();

  // read first line, which is a throwaway for this command.
  // application's first read will be useful data.
  stream_read_line(stream_state);

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;
}

static wgx_status_t parse_stream_list(wgx_stream_state_t *stream_state, 
                                     int8_t *handle, 
                                     wgx_stream_type_t* type)
{
  char *line = line_buffer;
  int16_t len;
  
  /*
  Stream list data format example:

  ! # Type  Info
  # 0 TCPS  10.1.54.31:12345 10.1.54.11:59584
  */

  len = stream_read_line(stream_state);

  // check if stream is empty
  if (len <= 0)
  {
    return WGX_STATUS_NO_DATA;
  }

  // sanity check for # at beginning of line
  if (line[0] != '#')
  {
    return WGX_STATUS_ERR;
  }

  line += 2;
  *handle = atoi(line);
  
  line += 2;
  *type = WGX_STREAM_TYPE_NONE;

  // rather than doing string compares, we will match against specific
  // characters.
  if (line[0] == 'C')
  {
    *type = WGX_STREAM_TYPE_CMD;
  }
  else if (line[0] == 'F')
  {
    *type = WGX_STREAM_TYPE_FILE; 
  }
  else if (line[0] == 'H')
  {
    *type = WGX_STREAM_TYPE_HTTP;   
  }
  else if (line[0] == 'T')
  {
    if (line[1] == 'C')
    {
      if (line[3] == 'C')
      {
        *type = WGX_STREAM_TYPE_TCPC;        
      }
      else
      {
        *type = WGX_STREAM_TYPE_TCPS;         
      }
    }
    else
    {
      if (line[3] == 'C')
      {
        *type = WGX_STREAM_TYPE_TLSC;        
      }
      else
      {
        *type = WGX_STREAM_TYPE_TLSS;         
      }
    }
  }
  else if (line[0] == 'U')
  {
    if (line[3] == 'C')
    { 
      *type = WGX_STREAM_TYPE_UDPC;         
    }
    else
    {
      *type = WGX_STREAM_TYPE_UDPS;
    }
  }
  else if (line[0] == 'W')
  {
    if (line[3] == 'C')
    { 
      *type = WGX_STREAM_TYPE_WEBC;         
    }
    else
    {
      *type = WGX_STREAM_TYPE_WEBS;
    }
  }

  return WGX_STATUS_OK;
}

static wgx_status_t close_stream(wgx_stream_state_t *stream_state)
{
  // check if stream is already closing or closed
  if ((stream_state->status == WGX_STATUS_CLOSE_ASYNC) ||
      (stream_state->status == WGX_STATUS_CLOSED) ||
      (stream_state->status == WGX_STATUS_CLOSING))
  {
    return WGX_STATUS_OK;  
  }

  u16toa(stream_state->handle, temp_buf);

  if ((stream_state->type != WGX_STREAM_TYPE_CMD) &&
      (stream_state->type != WGX_STREAM_TYPE_FILE))
  {
    stream_state->status = WGX_STATUS_CLOSE_ASYNC;
  }
  else
  {
    if (send_cmd(cmd_stream_close, 0, temp_buf, 0) < 0)
    {
      return WGX_STATUS_ERR;
    }

    stream_state->status = WGX_STATUS_CLOSED;
    stream_state->handle = -1;
    recv_response();
  }

  return WGX_STATUS_OK;
}

static uint8_t ping(void)
{
  uint16_t ticks = GetTickCount();

  // enable RX interrupt
  UART_enableRxFifoInt(true);

  // send <CR><LF>
  send_char('\r');
  send_char('\n');

  // clear buffer
  buffer[0] = 0;
  buffer[1] = 0;
  buffer[2] = 0;
  buffer[3] = 0;

  // set buffer read
  UART_readBuffer(buffer, 4);

  while ((UART_rxBytesRemaining() > 0) && ((GetTickCount() - ticks) < 20));
    
  // check for timeout
  if (UART_rxBytesRemaining() > 0)
  {
    return 0;
  }

  // short delay to wait for rest of module response to be sent
  // (even though we are ignoring it)
  // this is so we don't start sending a new command until
  // after the module finishes responding to our ping.
  Wait(2);

  // check buffer
  if ((buffer[0] == '\r') && (buffer[1] == '\n') && (buffer[2] == 'R') && (buffer[3] == 'e'))
  {
    return 1; // module is in human mode
  }
  else if ((buffer[0] == 'R') && (buffer[1] == '0') && (buffer[2] == '0') && (buffer[3] == '0'))
  {
    return 2; // module is in machine mode
  }

  // delay so we can't flood the module with pings
  Wait(20);

  // no response, or invalid response
  return 0;
}

static uint8_t wait_for_serial(void)
{
  uint8_t response = ping();

  while (response == 0)
  {
    response = ping();
  }

  return response;
}

static void set_machine_mode(void)
{
  send_cmd(cmd_set, cmd_machine_mode, 0, 0);
  Wait(10); // delay to skip human mode response

  if (wait_for_serial() != 2)
  {
    // not in machine mode
    // try one more time
    send_cmd(cmd_set, cmd_machine_mode, 0, 0);
    Wait(10);
    wait_for_serial();
  } 
}

static void init_comms(void)
{
  comm_state = STATE_IDLE;

  failed_cmds = 0;

  set_machine_mode();

  WGX_Set(cmd_stream_buffered, cmd_true);
  WGX_Set(cmd_stream_auto_close, cmd_false);
  WGX_Set(cmd_enable_dns, cmd_false);
  WGX_Cmd(cmd_stream_close_all, 0, 0, 0);
  WGX_Cmd(cmd_network_down, cmd_network_wlan, 0, 0);
  WGX_Cmd(cmd_network_down, cmd_network_softap, 0, 0);
  // WGX_Cmd(cmd_save, 0, 0, 0);

  process_stream = -1;
}

static void reboot(void)
{
  send_cmd(cmd_reboot, 0, 0, 0);

  connection_state = DISCONNECTED;
  setup_state = SETUP_IDLE;

  wait_for_serial();
  
  // re-initialize device comms
  init_comms(); 
}


/////////////////////////////////////////////////////////////////////////////
// Function
/////////////////////////////////////////////////////////////////////////////

void WGX_Init(void)
{
  wgx_stream_state_t *stream_state;
  uint8_t i;

  // initialize UART
  UART_init(
    WGX_SYS_CLK, 
    115200, 
    UART_DATALEN_8,
    UART_STOPLEN_SHORT,
    UART_FEATURE_DISABLE,
    0,
    UART_RX_ENABLE,
    UART_MULTIPROC_DISABLE
    );

  UART_initTxFifo(UART_TXTHRSH_ZERO, UART_TXFIFOINT_DISABLE);
  UART_initRxFifo(UART_RXTHRSH_ZERO, UART_RXTIMEOUT_DISABLE, UART_RXFIFOINT_DISABLE);

  for (i = 0; i < WGX_MAX_STREAMS; i++)
  {
    stream_state = &streams[i];

    stream_state->handle = -1;
    stream_state->type = WGX_STREAM_TYPE_NONE;
    stream_state->status = WGX_STATUS_OK;
  }

  comm_state = STATE_IDLE;
  
  wait_for_serial();

  set_machine_mode();

  WGX_Reboot();
}

void WGX_Poll(void)
{
  uint8_t i;
  wgx_stream_state_t *stream_state;

  if (comm_state == STATE_IDLE)
  {
    // check if any streams need to be closed
    if (process_stream < 0)
    {
      for (i = 0; i < WGX_MAX_STREAMS; i++)
      {
        stream_state = &streams[i];

        if (stream_state->status == WGX_STATUS_CLOSE_ASYNC)
        {
          // try to close
          u16toa(stream_state->handle, temp_buf);
          if (send_cmd(cmd_stream_close, 0, temp_buf, 0) >= 0)
          {
            process_stream = i;  
            stream_state->status = WGX_STATUS_CLOSING;

            async_recv();
          }          

          break;
        }
      }
    }
  }
  else if (comm_state == STATE_HEADER)
  {
    // check receiver status
    if ((WGX_BUFFER_SIZE - UART_rxBytesRemaining()) < RX_HEADER_LEN)
    {
      return;
    }

    // parse response

    // convert error code from character to integer
    response_err = buffer[1] - '0';

    // convert ascii response len to integer and
    // account for header (ISR will count all bytes receiveds
    // including the header).
    response_len = atoi(&buffer[2]) + RX_HEADER_LEN;
    buffer[response_len] = 0; // ensure null termination

    comm_state = STATE_DATA;
  }
  else if (comm_state == STATE_DATA)
  {
    // check receiver status
    if ((WGX_BUFFER_SIZE - UART_rxBytesRemaining()) < response_len)
    {
      return;
    }

    // disable RX interrupt
    UART_enableRxFifoInt(false);

    // check if a stream needs to be serviced
    if (process_stream >= 0)
    {
      stream_state = &streams[process_stream];

      if (stream_state->status == WGX_STATUS_CONNECTING)
      {
        if (response_err == 0)
        {
          // connected
          stream_state->status = WGX_STATUS_CONNECTED;

          // set stream handle (if not already set, which shouldn't really happen)
          if (stream_state->handle < 0)
          {
            stream_state->handle = atoi(&buffer[RX_HEADER_LEN]);
          }
        }
        else
        {
          // connection fail
          stream_state->status = WGX_STATUS_CONNECT_FAILED;
        }
      }
      else if (stream_state->status == WGX_STATUS_CLOSING)
      {
        // clear handle
        stream_state->handle = -1;

        if (response_err == 0)
        {
          stream_state->status = WGX_STATUS_CLOSED;
        }
        else
        {
          stream_state->status = WGX_STATUS_ERR;
        }
      }
    }

    process_stream = -1;
    comm_state = STATE_IDLE;
  }
  else if (comm_state == STATE_SLEEP)
  {
    if (ping() != 0)
    {
      // module is back up
      init_comms();
    }
  }
  
  if (setup_state == SETUP_WEB)
  {  
    WGX_Cmd(cmd_setup_status, 0, 0, 0);

    // check response
    if (response_err != RESPONSE_SUCCESS)
    {
      // got an error, possibly came back up from reboot.
      Wait(50);
      init_comms();
    } 
    else if ((response_err == RESPONSE_SUCCESS) && (buffer[RX_HEADER_LEN] == '0'))
    {
      // exit setup mode, since the connect api won't run in setup mode
      setup_state = SETUP_IDLE;

      // connect to wifi
      if (WGX_Connect(0, 0) == WGX_STATUS_OK)
      {
        // now go to connecting state and set timeout
        setup_state = SETUP_CONNECT;
        network_timeout = GetTickCount();
      }
      else
      {
        // connection command failed, return to setup mode
        WGX_WebSetup(0, 0); 
      }
    }
    else
    {
      // prevent rapid polling from overloading the module CPU
      Wait(20);
    }
  }
  else if (setup_state == SETUP_CONNECT)
  {
    // pre-emptively set to idle state, otherwise WGX_IsConnected will
    // always return false.
    setup_state = STATE_IDLE;

    // check if connected
    if (WGX_IsConnected())
    {
      // setup is complete
      
    }
    else if ((GetTickCount() - network_timeout) >= WGX_NETWORK_TIMEOUT)
    {
      // connection failed, return to setup mode
      WGX_WebSetup(0, 0); 
    }
    else
    {
      // fallthrough case is that we are not connected but have not 
      // timed out. set state back to connect, and we'll check again
      // on the next pass.
      setup_state = SETUP_CONNECT;
    }
  }
}

bool WGX_Busy(void)
{
  if (busy())
  {
    WGX_Poll();
  }

  return busy();
}

void WGX_Reboot(void)
{
  // busy wait for async processing to complete
  while (WGX_Busy())
  {
    WDT_kick();
  }

  reboot();
}

wgx_status_t WGX_Sleep(uint16_t seconds)
{
  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  u16toa(seconds, temp_buf);

  if (WGX_Set(cmd_wakeup_timeout, temp_buf) < 0)
  {
    return WGX_STATUS_ERR;
  }

  // disconnect from network interfaces (also closes open streams)
  WGX_Disconnect();

  send_cmd(cmd_sleep, 0, 0, 0);

  // wait for transmission to complete
  while (UART_getTxFifoCount() > 0);

  comm_state = STATE_SLEEP;

  return WGX_STATUS_OK;
}

bool WGX_IsSleeping(void)
{
  if (comm_state == STATE_SLEEP)
  {
    WGX_Poll();
  }

  return comm_state == STATE_SLEEP;
}

wgx_status_t WGX_Cmd(char* cmd0, 
                     char* cmd1, 
                     char* param0, 
                     char* param1)
{
  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  if (send_cmd(cmd0, cmd1, param0, param1) < 0)
  {
    return WGX_STATUS_ERR;
  }

  if (recv_response() < 0)
  {
    return WGX_STATUS_ERR; 
  }

  return WGX_STATUS_OK;
}

wgx_status_t WGX_Set(char* variable, char* param)
{
  return WGX_Cmd(cmd_set, variable, param, 0);
}

wgx_status_t WGX_Get(char* variable, char* result, uint8_t max_len)
{
  wgx_status_t status = WGX_Cmd(cmd_get, 0, variable, 0);

  if (status < 0)
  {
    return status;
  }

  if (result != 0)
  {
    strncpy(result, &buffer[RX_HEADER_LEN], max_len);

    // ensure null termination on buffers longer than 1 byte.
    // this allows single character responses, while still
    // protecting strings.
    if (max_len > 1)
    {
      result[max_len - 1] = 0;
    }
  }

  return WGX_STATUS_OK;
}

wgx_status_t WGX_Client(wgx_protocol_t protocol,
                        wgx_stream_t stream,
                        char* ip, 
                        uint16_t port, 
                        uint16_t local_port)
{
  SI_VARIABLE_SEGMENT_POINTER(cmd, char, SI_SEG_CODE);
  wgx_stream_type_t stream_type;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (stream_state->handle >= 0)
  {
    return WGX_STATUS_ALREADY_OPEN;
  }

  if (busy() || not_connected() || (setup_state != SETUP_IDLE) || (process_stream >= 0))
  {
    return WGX_STATUS_BUSY;
  }

  if (protocol == WGX_PROTOCOL_UDP)
  {
    cmd = cmd_udpc;
    stream_type = WGX_STREAM_TYPE_UDPC;
  }
  else if (protocol == WGX_PROTOCOL_TCP)
  {
    stream_type = WGX_STREAM_TYPE_TCPC;

    if (local_port == 0)
    {
      cmd = cmd_tcpc;
    }
    else
    {
      cmd = cmd_tcpcl;  
    }
  }
  else if (protocol == WGX_PROTOCOL_TLS)
  {
    stream_type = WGX_STREAM_TYPE_TLSC;

    if (local_port == 0)
    {
      cmd = cmd_tlsc;
    }
    else
    {
      cmd = cmd_tlscl;  
    }
  }
  else
  {
    return WGX_STATUS_ERR;
  }

  u16toa(port, temp_buf);

  // set to error status. this will be changed later if this function completes
  // without error.
  stream_state->status = WGX_STATUS_ERR;

  if (local_port == 0)
  {
    if (send_cmd(cmd, ip, temp_buf, 0) < 0)
    {
      return WGX_STATUS_ERR;
    }  
  }
  else
  {
    u16toa(local_port, temp_buf2);

    if (send_cmd(cmd, temp_buf2, ip, temp_buf) < 0)
    {
      return WGX_STATUS_ERR;
    }  
  }

  async_recv();

  process_stream = stream;

  stream_state->type = stream_type;
  stream_state->status = WGX_STATUS_CONNECTING;

  reset_line_buffer();

  return WGX_STATUS_OK;
}

wgx_status_t WGX_StartServer(wgx_protocol_t protocol, uint16_t local_port)
{
  SI_VARIABLE_SEGMENT_POINTER(iface, char, SI_SEG_CODE);

  if (setup_state != SETUP_IDLE)
  {
    return WGX_STATUS_BUSY;
  }

  if (connection_state == CONNECTED_AP)
  {
    iface = cmd_network_wlan;
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    iface = cmd_network_softap;
  }
  else
  {
    return WGX_STATUS_NO_CONNECTION;  
  }

  u16toa(local_port, temp_buf);

  if (protocol == WGX_PROTOCOL_UDP)
  {
    return WGX_Cmd(cmd_udps, 0, cmd_start, temp_buf);
  }
  else if (protocol == WGX_PROTOCOL_TCP)
  {
    return WGX_Cmd(cmd_tcps, iface, cmd_start, temp_buf);
  }
  
  return WGX_STATUS_ERR;  
}

wgx_status_t WGX_StopServer(wgx_protocol_t protocol, uint16_t local_port)
{
  SI_VARIABLE_SEGMENT_POINTER(cmd, char, SI_SEG_CODE);
  wgx_stream_type_t stream_type;
  wgx_stream_state_t *stream_state;
  uint8_t i;

  if (protocol == WGX_PROTOCOL_UDP)
  {
    cmd = cmd_udps;
    stream_type = WGX_STREAM_TYPE_UDPS;
  }
  else if (protocol == WGX_PROTOCOL_TCP)
  {
    cmd = cmd_tcps;
    stream_type = WGX_STREAM_TYPE_TCPS;
  }
  else
  {
    return WGX_STATUS_ERR;
  }

  // close all open streams.
  // the WGX module will automatically close streams on its side,
  // so we just need to update our internal state
  for (i = 0; i < WGX_MAX_STREAMS; i++)
  {
    stream_state = &streams[i];

    if (stream_state->type == stream_type)
    {
      stream_state->status = WGX_STATUS_CLOSED;
      stream_state->handle = -1;
    }
  }

  u16toa(local_port, temp_buf);

  return WGX_Cmd(cmd, cmd_stop, temp_buf, 0); 
}

wgx_status_t WGX_AcceptConnection(wgx_protocol_t protocol,
                                  wgx_stream_t stream)
{
  int8_t handle;
  wgx_stream_type_t stream_type;
  wgx_stream_type_t filter_type;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (stream_state->handle >= 0)
  {
    return WGX_STATUS_ALREADY_OPEN;
  } 

  if (busy() || not_connected())
  {
    return WGX_STATUS_BUSY;
  }

  if (protocol == WGX_PROTOCOL_UDP)
  {
    filter_type = WGX_STREAM_TYPE_UDPS;
  }
  else if (protocol == WGX_PROTOCOL_TCP)
  {
    filter_type = WGX_STREAM_TYPE_TCPS;
  }
  else if (protocol == WGX_PROTOCOL_TLS)
  {
    filter_type = WGX_STREAM_TYPE_TLSS;
  }
  else
  {
    return WGX_STATUS_ERR;
  }

  // list streams and find any that are new.
  if (stream_list(stream_state) < 0)
  {
    stream_state->status = WGX_STATUS_ERR;
    return WGX_STATUS_ERR;
  }

  while (parse_stream_list(stream_state, &handle, &stream_type) == WGX_STATUS_OK)
  {
    // is this handle already open?
    if (is_handle_open(handle))
    {
      continue;
    }  

    // does this stream match
    if (stream_type == filter_type)
    {
      // match! 

      // close the list stream
      close_stream(stream_state);

      // open new stream
      stream_state->status = WGX_STATUS_CONNECTED;
      stream_state->handle = handle;
      stream_state->type = stream_type;

      return WGX_STATUS_OK;
    }
  }

  // close the list stream
  close_stream(stream_state);

  stream_state->status = WGX_STATUS_NO_CONNECTION;

  return WGX_STATUS_NO_CONNECTION;  
}

int16_t WGX_StreamRead(wgx_stream_t stream, 
                       uint8_t* dest, 
                       uint8_t len)
{
  uint8_t read_len;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  // limit read length to buffer size
  if (len > WGX_BUFFER_SIZE)
  {
    len = WGX_BUFFER_SIZE;
  }

  if (busy() || (stream_state->handle < 0))
  {
    return 0;
  }

  if (stream_read(stream_state->handle, len) < 0)
  {
    return 0;
  }

  // save copy of response length
  read_len = response_len;

  // check for 0 length response
  if (response_len == 0)
  {
    // poll for status
    if (WGX_Cmd(cmd_stream_poll, 0, temp_buf, 0) == WGX_STATUS_OK)
    {
      // poll status of 2 indicates that the remote side has closed the connection.
      if (((response_len - RX_HEADER_LEN) > 0) && (buffer[RX_HEADER_LEN] == '2'))
      {
        // since we've read all of the data left on the stream, we can go ahead and close the connection.

        // change stream type to cmd.
        // if this was a network stream, since the remote closed, our side
        // can close synchronously.
        // changing type to cmd allows the close function to run the synchronous command.
        stream_state->type = WGX_STREAM_TYPE_CMD;

        close_stream(stream_state);
        
        return -1;
      }
    }

    // read 0 bytes, we can return here
    return 0;
  } 

  if (read_len > len)
  {
    read_len = len;
  }

  memcpy(dest, &buffer[RX_HEADER_LEN], read_len);

  return read_len;
}

int16_t WGX_StreamWrite(wgx_stream_t stream, 
                        uint8_t* src, 
                        uint8_t len)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (stream_state->handle < 0)
  {
    return 0;
  }

  if (busy())
  {
    return 0;
  }

  u16toa(stream_state->handle, temp_buf);
  u16toa(len, temp_buf2);

  if (send_cmd(cmd_stream_write, 0, temp_buf, temp_buf2) < 0)
  {
    return 0;
  }

  // copy data into XRAM buffer
  // the UART routines only work with XRAM
  memcpy(buffer, src, len);

  // write stream data
  tx_busy = true;
  write_uart_buffer(buffer, len);
  
  // wait while buffer is sent
  while (tx_busy);

  send_char('\r');
  send_char('\n');

  // stream write sends two responses!
  recv_response();
  recv_response();

  return len;
}

int16_t WGX_StreamWriteStr(wgx_stream_t stream, 
                           char* src)
{
  return WGX_StreamWrite(stream, src, strlen(src));
}

bool WGX_StreamBusy(wgx_stream_t stream)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return false;
  }

  return (stream_state->status == WGX_STATUS_CONNECTING) ||
         (stream_state->status == WGX_STATUS_CLOSING);
}

wgx_status_t WGX_StreamWait(wgx_stream_t stream)
{
  uint16_t ticks = GetTickCount();

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  // delay loop while stream is busy
  while (WGX_StreamBusy(stream) && !timed_out(ticks))
  {
    WDT_kick();

    // poll so the internal state machine continues to run
    WGX_Poll();
  }

  // check if stream is still busy, if so, we have a timeout
  if (WGX_StreamBusy(stream))
  {
    return WGX_STATUS_TIMED_OUT;
  }

  return WGX_STATUS_OK;
}

wgx_status_t WGX_StreamClose(wgx_stream_t stream)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  // check if stream is initialized
  if (stream_state->handle < 0)
  {
    return WGX_STATUS_NOT_INITIALIZED;    
  }

  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  return close_stream(stream_state);
}

wgx_status_t WGX_StreamStatus(wgx_stream_t stream)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  return stream_state->status;
}

wgx_status_t WGX_OpenFile(wgx_stream_t stream, char* filename)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (WGX_Cmd(cmd_file_open, 0, filename, 0) < 0)
  {
    return WGX_STATUS_ERR;  
  }

  stream_state->status = WGX_STATUS_ERR;
  stream_state->type = WGX_STREAM_TYPE_FILE;

  // check if open succeeded
  if (response_err != RESPONSE_SUCCESS)
  {
    return WGX_STATUS_ERR;
  }

  if (parse_stream_handle(&stream_state->handle, 0) < 0)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;   
} 

wgx_status_t WGX_CreateFile(wgx_stream_t stream, char* filename, uint16_t size)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  u16toa(size, temp_buf);

  if (WGX_Cmd(cmd_file_create, 0, filename, temp_buf) < 0)
  {
    return WGX_STATUS_ERR;  
  }

  stream_state->status = WGX_STATUS_ERR;
  stream_state->type = WGX_STREAM_TYPE_FILE;

  // check if open succeeded
  if (response_err != RESPONSE_SUCCESS)
  {
    return WGX_STATUS_ERR;
  }

  if (parse_stream_handle(&stream_state->handle, 0) < 0)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;   
}

wgx_status_t WGX_ListFiles(wgx_stream_t stream)
{
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (WGX_Cmd(cmd_ls, 0, 0, 0) < 0)
  {
    return WGX_STATUS_ERR;  
  }

  stream_state->status = WGX_STATUS_ERR;
  stream_state->type = WGX_STREAM_TYPE_CMD;

  if (parse_stream_handle(&stream_state->handle, 0) < 0)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  reset_line_buffer();

  // read first line, which is a throwaway for this command.
  // application's first read will be useful data.
  stream_read_line(stream_state);

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;
}

wgx_status_t WGX_ReadFileList(wgx_stream_t stream, 
                              char* filename, 
                              int8_t filename_len,
                              uint32_t *file_length)
{
  int16_t len;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }
  
  // check if stream is initialized
  if (stream_state->handle < 0)
  {
    return WGX_STATUS_NOT_INITIALIZED;    
  }

  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  /*
  File list data format example:

  //  !  #   Size   Version  Filename
  //  #  0   1995     1.5.0  webapp/index.html
  //  #  1  22670     1.5.0  webapp/zentrios.css.gz
  //  #  2   7599     1.5.0  .recovery.html
  //  #  3   9530     1.5.0  webapp/unauthorized.html

  */

  len = stream_read_line(stream_state);

  if (len == 0)
  {
    return WGX_STATUS_NO_DATA;
  }

  // sanity check for # at beginning of line.
  if (line_buffer[0] != '#')
  {
    return WGX_STATUS_ERR;
  } 

  // parse file size
  // this starts at character 5
  if (file_length != 0)
  {
    *file_length = atoi(&line_buffer[4]);
  }

  strncpy(filename, &line_buffer[23], filename_len);
  filename[filename_len - 1] = 0; // ensure null termination

  return WGX_STATUS_OK;
}

wgx_status_t WGX_ScanWifi(wgx_stream_t stream)
{
  uint16_t save_timeout = timeout;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  stream_state->status = WGX_STATUS_ERR;

  timeout = WGX_NETWORK_TIMEOUT;

  if (WGX_Cmd(cmd_scan, 0, 0, 0) < 0)
  {
    timeout = save_timeout;
    return WGX_STATUS_ERR;  
  }
  timeout = save_timeout;

  stream_state->status = WGX_STATUS_OK;
  stream_state->type = WGX_STREAM_TYPE_CMD;

  if (parse_stream_handle(&stream_state->handle, 0) < 0)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  reset_line_buffer();

  // read first line, which is a throwaway for this command.
  // application's first read will be useful data.
  stream_read_line(stream_state);

  // for wifi scan, also throw away second line
  stream_read_line(stream_state);

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;
}

wgx_status_t WGX_ParseWifiList(wgx_stream_t stream, 
                               char ssid[WGX_SSID_STR_LEN], 
                               uint8_t *channel, 
                               char mac[WGX_MAC_STR_LEN], 
                               int8_t *rssi)
{
  int16_t len;
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  // check if stream is initialized
  if (stream_state->handle < 0)
  {
    return WGX_STATUS_NOT_INITIALIZED;    
  }

  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  /*
  Wifi scan list data format example:

  //  ! 1 networks found
  //  !  # Ch RSSI MAC (BSSID)       Network (SSID)
  //  #  0  1  -72 FE:EC:DA:81:30:32 SiliconLabsGuest

  */

  stream_state->status = WGX_STATUS_ERR;

  len = stream_read_line(stream_state);

  if (len < 32)
  {
    return WGX_STATUS_NO_DATA;
  }

  // sanity check for # at beginning of line.
  if (line_buffer[0] != '#')
  {
    return WGX_STATUS_ERR;
  } 

  // parse channel
  if (channel != 0)
  {
    *channel = atoi(&line_buffer[5]);
  }

  // parse rssi
  if (rssi != 0)
  {
    *rssi = atoi(&line_buffer[8]);
  }

  // parse MAC
  if (mac != 0)
  {
    // set null terminator
    line_buffer[30] = 0;
    memset(mac, 0, WGX_MAC_STR_LEN);

    strncpy(mac, &line_buffer[13], WGX_MAC_STR_LEN);    
  }

  // parse ssid
  if (ssid != 0)
  {
    memset(ssid, 0, WGX_SSID_STR_LEN);
    strncpy(ssid, &line_buffer[31], WGX_SSID_STR_LEN);
  }

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;
}

bool WGX_IsConnected(void)
{
  char result;

  // don't count setup mode as connected
  if (setup_state != SETUP_IDLE)
  {
    return false;
  }
  
  if (connection_state == CONNECTED_AP)
  {
    if (WGX_Get(cmd_wlan_status, &result, sizeof(result)) < 0)
    {
      return false;
    }
    // WLAN status:
    // 0 = not connected
    // 1 = connected to AP but no IP address
    // 2 = connected to AP and IP address configured

    return result == '2';
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    return true;
  }

  return false;
}

wgx_status_t WGX_Connect(char* ssid, char* pass)
{
  if (setup_state != SETUP_IDLE)
  {
    return WGX_STATUS_BUSY;
  }

  // disconnect first
  WGX_Disconnect();

  // set ssid and password, if given
  if (ssid != 0)
  {
    if (WGX_Set(cmd_wlan_ssid, ssid) < 0)
    {
      return WGX_STATUS_ERR;
    }
  }

  if (pass != 0)
  {
    if (WGX_Set(cmd_wlan_pass, pass) < 0)
    {
      return WGX_STATUS_ERR;
    }
  }

  connection_state = CONNECTED_AP;

  return WGX_Cmd(cmd_network_up, cmd_network_wlan, 0, 0);
}

wgx_status_t WGX_SoftAP(char* ssid, char* pass)
{
  wgx_status_t status;

  if (setup_state != SETUP_IDLE)
  {
    return WGX_STATUS_BUSY;
  }

  // disconnect first
  WGX_Disconnect();

  // set ssid and password
  if (WGX_Set(cmd_softap_ssid, ssid) < 0)
  {
    return WGX_STATUS_ERR;
  }

  if (WGX_Set(cmd_softap_pass, pass) < 0)
  {
    return WGX_STATUS_ERR;
  }
  
  connection_state = CONNECTED_SOFTAP;

  status = WGX_Cmd(cmd_network_up, cmd_network_softap, 0, 0);

  // work around for bug in soft AP on W variant:
  // there is no way to poll to check when the soft AP is up,
  // but sending a start server command before the soft AP is
  // ready causes it to fail.
  // Best we can do for now is delay.
  // Wait(2000);

  return status;
}

wgx_status_t WGX_Disconnect(void)
{
  uint8_t i;
  wgx_status_t status = WGX_STATUS_OK;

  if (setup_state != SETUP_IDLE)
  {
    return WGX_STATUS_BUSY;
  }

  if (connection_state == CONNECTED_AP)
  {
    status = WGX_Cmd(cmd_network_down, cmd_network_wlan, 0, 0);  
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    status = WGX_Cmd(cmd_network_down, cmd_network_softap, 0, 0);  
  }

  connection_state = DISCONNECTED;

  for (i = 0; i < WGX_MAX_STREAMS; i++)
  {
    if ((streams[i].type != WGX_STREAM_TYPE_CMD) &&
        (streams[i].type != WGX_STREAM_TYPE_FILE))
    {
      // reset stream
      streams[i].handle = -1;
      streams[i].status = WGX_STATUS_CLOSED;
    }
  }

  return status;
}

uint8_t WGX_SoftAPClientCount(void)
{
  char *s;
  int8_t handle;
  int16_t len;
  uint8_t clients = 0;

  if (WGX_Cmd(cmd_get, 0, cmd_softap_info, 0) < 0)
  {
    return 0;
  }

  if (parse_stream_handle(&handle, &len) < 0)
  {
    return 0;   
  }

  if (stream_read(handle, WGX_BUFFER_SIZE) < 0)
  {
    return 0;
  }

  s = strstr(buffer, cmd_clients);

  if (s != 0)
  {
    clients = atoi(&s[8]);
  }

  if (WGX_Cmd(cmd_stream_close, 0, temp_buf, 0) < 0)
  {
    return 0;
  }

  return clients;  
}

wgx_status_t WGX_IP(char* ip, uint8_t len)
{
  if (connection_state == CONNECTED_AP)
  {
    return WGX_Get(cmd_wlan_ip, ip, len);
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    return WGX_Get(cmd_softap_ip, ip, len);
  }

  return WGX_STATUS_NO_CONNECTION;
}

wgx_status_t WGX_SSID(char* ssid, uint8_t len)
{
  if (connection_state == CONNECTED_AP)
  {
    return WGX_Get(cmd_wlan_ssid, ssid, len);
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    return WGX_Get(cmd_softap_ssid, ssid, len);
  }

  ssid[0] = 0; // null string

  return WGX_STATUS_NO_CONNECTION;
}

wgx_status_t WGX_RSSI(int8_t *rssi)
{
  wgx_status_t status = WGX_Cmd(cmd_rssi, 0, 0, 0);

  if (status != WGX_STATUS_OK)
  {
    return status;
  }

  *rssi = atoi(&buffer[RX_HEADER_LEN]);

  return WGX_STATUS_OK;
}
  
wgx_status_t WGX_Http_Get(wgx_stream_t stream, char *url)
{
  SI_VARIABLE_SEGMENT_POINTER(iface, char, SI_SEG_CODE);
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (connection_state == CONNECTED_AP)
  {
    iface = cmd_network_wlan;
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    iface = cmd_network_softap;
  }
  else
  {
    return WGX_STATUS_NO_CONNECTION;  
  }

  stream_state->status = WGX_STATUS_ERR;

  if (WGX_Cmd(cmd_http_get, iface, url, 0) < 0)
  {
    return WGX_STATUS_ERR;
  }

  stream_state->status = WGX_STATUS_OK;
  stream_state->type = WGX_STREAM_TYPE_HTTP;

  if (parse_stream_handle(&stream_state->handle, 0) == -1)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;  
}

wgx_status_t WGX_Http_Post(wgx_stream_t stream, char *url, char *content_type)
{
  SI_VARIABLE_SEGMENT_POINTER(iface, char, SI_SEG_CODE);
  wgx_stream_state_t *stream_state = &streams[stream];

  if (stream >= WGX_MAX_STREAMS)
  {
    return WGX_STATUS_ERR;
  }

  if (connection_state == CONNECTED_AP)
  {
    iface = cmd_network_wlan;
  }
  else if (connection_state == CONNECTED_SOFTAP)
  {
    iface = cmd_network_softap;
  }
  else
  {
    return WGX_STATUS_NO_CONNECTION;  
  }

  stream_state->status = WGX_STATUS_ERR;

  if (WGX_Cmd(cmd_http_post, iface, url, content_type) < 0)
  {
    return WGX_STATUS_ERR;
  }

  stream_state->status = WGX_STATUS_OK;
  stream_state->type = WGX_STREAM_TYPE_HTTP;

  if (parse_stream_handle(&stream_state->handle, 0) == -1)
  {
    stream_state->handle = -1;

    return WGX_STATUS_ERR;   
  }

  stream_state->status = WGX_STATUS_OK;

  return WGX_STATUS_OK;  
}


void WGX_Bootload(char *filename)
{
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // This function will corrupt XRAM, so it must terminate
  // with a restart of the CPU.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

  // get pointer to signature region
  uint8_t SI_SEG_XDATA *sig = (char SI_SEG_XDATA *)0x2;

  // Disable all interrupts
  IE_EA = 0;

  // copy filename to signature (if supplied)
  if (filename)
  {
    strcpy(&sig[2], filename);
  }

  // set up bootloader signature bytes to request a bootloader session
  sig[0] = '@';
  sig[1] = '#';

  // if the watchdog is enabled, now would be a good time to turn if off...
  WDT_stop();

  //reset CPU
  while (true)
  {
    // set bootloader signature so it knows we want to load a program.
    *((uint8_t SI_SEG_DATA *)0x00) = BL_SIGNATURE;
    
    // initiate software reset
    RSTSRC = RSTSRC_SWRSF__SET;
  }
}

wgx_status_t WGX_WebSetup(char* ssid, char* pass)
{
  if (busy())
  {
    return WGX_STATUS_BUSY;
  }

  // the module behaves better in setup mode from a fresh reboot
  reboot();

  // if ssid and/or password provided, configure them.
  // otherwise, use factory defaults
  if (ssid != 0)
  {
    if (WGX_Set(cmd_setup_ssid, ssid) < 0)
    {
      return WGX_STATUS_ERR;
    }
  }

  if (pass != 0)
  {
    if (WGX_Set(cmd_setup_pass, pass) < 0)
    {
      return WGX_STATUS_ERR;
    }
  }

  // save ssid and pass parameters
  if (WGX_Cmd(cmd_save, 0, 0, 0) < 0)
  {
    return WGX_STATUS_ERR;  
  }

  // enter setup mode
  if (WGX_Cmd(cmd_setup_web, 0, 0, 0) < 0)
  {
    return WGX_STATUS_ERR;
  }

  setup_state = SETUP_WEB;
  connection_state = CONNECTED_SOFTAP;

  return WGX_STATUS_OK;
}

bool WGX_IsWebSetup(void)
{
  return setup_state == SETUP_WEB;
}

bool WGX_IsWebSetupConnecting(void)
{
  return setup_state == SETUP_CONNECT;
}

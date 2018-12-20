#include <string.h>

#include "bsp.h"
#include "disp.h"
#include "render.h"
#include "rgb_led.h"
#include "joystick.h"
#include "wgx.h"
#include "ui.h"
#include "tick.h"

/////////////////////////////////////////////////////////////////////////////
// Globals
/////////////////////////////////////////////////////////////////////////////

// Generic line buffer
static SI_SEGMENT_VARIABLE(line_buffer[DISP_BUF_SIZE], uint8_t, RENDER_LINE_SEG);

// Strings
static SI_SEGMENT_VARIABLE(main_line_top[], char, SI_SEG_CODE)       = "EFM8 <-> WGX Demos";
static SI_SEGMENT_VARIABLE(main_line_bottom[], char, SI_SEG_CODE)    = "Select";
static SI_SEGMENT_VARIABLE(main_menu_items[][DISP_MAX_LINES_X - CURSOR_X_OFFSET], char, SI_SEG_CODE) =
{
  "WiFi Soft AP Mode ",
  "AP Web Setup      ",
  "WiFi Scan         ",
  "Load Program File ",
  "Sleep             ",
};

static SI_SEGMENT_VARIABLE(efm8_wgx_startup_str[], char, SI_SEG_CODE)= "Starting...";

static SI_SEGMENT_VARIABLE(menu_cursor_str[], char, SI_SEG_CODE)     = "*";
static SI_SEGMENT_VARIABLE(proglist_line_top[], char, SI_SEG_CODE)   = "EFM8 Program List";
static SI_SEGMENT_VARIABLE(menu_line_dashes[], char, SI_SEG_CODE)    = "---------------------";
static SI_SEGMENT_VARIABLE(proglist_line_bottom[], char, SI_SEG_CODE)= "Load File        Back";
static SI_SEGMENT_VARIABLE(noprog_line[], char, SI_SEG_CODE)         = "No files found";
static SI_SEGMENT_VARIABLE(softapefm8_line[], char, SI_SEG_CODE)     = "softAP.efm8";

static SI_SEGMENT_VARIABLE(wifilist_line_top[], char, SI_SEG_CODE)   = "Wifi Scan";
static SI_SEGMENT_VARIABLE(wifilist_scanning[], char, SI_SEG_CODE)   = "Scanning...";

static SI_SEGMENT_VARIABLE(wifisoftap_line_top[], char, SI_SEG_CODE) = "Wifi Soft AP";
static SI_SEGMENT_VARIABLE(wifisoftap_line_1[], char, SI_SEG_CODE)   = "SSID: Honeybee";
static SI_SEGMENT_VARIABLE(wifisoftap_line_2[], char, SI_SEG_CODE)   = "Pass: password";
static SI_SEGMENT_VARIABLE(websetup_line_1[], char, SI_SEG_CODE)     = "SSID: Honeybee_setup";

static SI_SEGMENT_VARIABLE(line_bottom_back[], char, SI_SEG_CODE)    = "                 Back";

static SI_SEGMENT_VARIABLE(websetup_line_top[], char, SI_SEG_CODE)   = "Web Setup";
static SI_SEGMENT_VARIABLE(setupcomplete_line[], char, SI_SEG_CODE)  = "Web setup complete";

static SI_SEGMENT_VARIABLE(confirm_line_top[], char, SI_SEG_CODE)    = "Confirm file";
static SI_SEGMENT_VARIABLE(confirm_line_bottom[], char, SI_SEG_CODE) = "Cancel      Load File";
static SI_SEGMENT_VARIABLE(bytes_str[], char, SI_SEG_CODE)           = " bytes";

static SI_SEGMENT_VARIABLE(loading_line_top[], char, SI_SEG_CODE)    = "Loading file";

static SI_SEGMENT_VARIABLE(starting_str[], char, SI_SEG_CODE)        = "Starting...";
static SI_SEGMENT_VARIABLE(clear_str[], char, SI_SEG_CODE)           = "                    ";
static SI_SEGMENT_VARIABLE(error_str[], char, SI_SEG_CODE)           = "ERROR";
static SI_SEGMENT_VARIABLE(channel_str[], char, SI_SEG_CODE)         = "Channel ";
static SI_SEGMENT_VARIABLE(rssi_str[], char, SI_SEG_CODE)            = "RSSI ";
static SI_SEGMENT_VARIABLE(clients_str[], char, SI_SEG_CODE)         = "Clients: ";
static SI_SEGMENT_VARIABLE(ip_str[], char, SI_SEG_CODE)              = "IP: ";
static SI_SEGMENT_VARIABLE(portsopen_str1[], char, SI_SEG_CODE)      = "Telnet port:   12345";
static SI_SEGMENT_VARIABLE(portsopen_str2[], char, SI_SEG_CODE)      = "UDP echo port: 12345";
static SI_SEGMENT_VARIABLE(connected_str[], char, SI_SEG_CODE)       = "Connected ";
static SI_SEGMENT_VARIABLE(connecting_str[], char, SI_SEG_CODE)      = "Connecting...";
static SI_SEGMENT_VARIABLE(failed_str[], char, SI_SEG_CODE)          = "Connection failed!";
static SI_SEGMENT_VARIABLE(disconnected_str[], char, SI_SEG_CODE)    = "Disconnected ";
static SI_SEGMENT_VARIABLE(hello_str[], char, SI_SEG_CODE)           = "EFM8 says hi!\n";

#ifdef EFM8UB1
static SI_SEGMENT_VARIABLE(file_dir_str[], char, SI_SEG_CODE)        = "EFM8/UB1/";
#endif
#ifdef EFM8UB2
static SI_SEGMENT_VARIABLE(file_dir_str[], char, SI_SEG_CODE)        = "EFM8/UB2/";
#endif

static SI_SEGMENT_VARIABLE(sleeping_str[], char, SI_SEG_CODE)        = "Sleeping... ";
static SI_SEGMENT_VARIABLE(awake_str[], char, SI_SEG_CODE)           = "Awake!";

static SI_SEGMENT_VARIABLE(current_file_str[WGX_MAX_FILENAME_LEN], char, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE(temp_buffer[WGX_MAX_FILENAME_LEN], char, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE(temp_ssid[WGX_SSID_STR_LEN], char, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE(temp_mac[WGX_MAC_STR_LEN], char, SI_SEG_XDATA);

static SI_SEGMENT_VARIABLE(current_file_size, int32_t, SI_SEG_XDATA);
static SI_SEGMENT_VARIABLE(current_rssi, int8_t, SI_SEG_XDATA);

#define GENERIC_STREAM  0
#define UDP_STREAM      1
#define TCP_STREAM      2

static uint8_t max_cursor;
static uint8_t menu_cursor;
static int8_t file_count = -1;
static bool joystick_pressed;
static bool button0_pressed;
static bool button1_pressed;

enum menu_pages 
{
  PAGE_STARTUP,
  PAGE_MAIN_MENU,
  PAGE_WIFI_LIST,
  PAGE_WIFI_SOFTAP,
  PAGE_WEB_SETUP,
  PAGE_WEB_SETUP_CONNECTING,
  PAGE_WEB_SETUP_COMPLETE,
  PAGE_PROGRAM_LIST,
  PAGE_CONFIRM,
  PAGE_LOADING,
  PAGE_SLEEP,
};
static uint8_t menu_page;


#define LED_GREEN     Colors[0]
#define LED_YELLOW    Colors[1]
#define LED_ORANGE    Colors[2]
#define LED_RED       Colors[3]
#define LED_BLUE      Colors[4]
#define LED_TEAL      Colors[5]
#define LED_PURPLE    Colors[6]
#define LED_WHITE     Colors[7]
#define LED_OFF       Colors[8]

SI_SEGMENT_VARIABLE(Colors[], Color, const SI_SEG_CODE) = {
  {0,     255,  0},   // Green
  {255,   255,  0},   // Yellow
  {255,   165,  0},   // Orange
  {255,   0,    0},   // Red
  {0,     0,    255}, // Blue
  {0,     165,  255}, // Teal
  {165,   0,    255}, // Purple
  {165,   165,  165}, // White
  {0,     0,    0},   // Off
};

#define MAX_FILE_COUNT (DISP_MAX_LINES_Y - 5)


#define SOFTAP_SSID "Honeybee"
#define SOFTAP_PASS "password"

#define SETUP_SSID "Honeybee_setup"
#define SETUP_PASS SOFTAP_PASS

#define DEMO_SERVER_PORT 12345

/////////////////////////////////////////////////////////////////////////////
// Static Function Prototypes
/////////////////////////////////////////////////////////////////////////////


/////////////////////////////////////////////////////////////////////////////
// Function
/////////////////////////////////////////////////////////////////////////////

static void render_line(
  uint8_t posx,
  uint8_t posy,
  SI_VARIABLE_SEGMENT_POINTER(str, char, RENDER_STR_SEG))
{
  uint8_t y;

  posx *= FONT_WIDTH;
  posy *= FONT_HEIGHT;

  for (y = 0; y < FONT_HEIGHT; y++)
  {
    RENDER_ClrLine(line_buffer);
    RENDER_StrLine(line_buffer, posx, y, str);
    DISP_WriteLine(y + posy, line_buffer);
  }
}

static void render_cursor_line(
  uint8_t posx,
  uint8_t posy,
  bool cursor,
  SI_VARIABLE_SEGMENT_POINTER(str, char, RENDER_STR_SEG))
{
  uint8_t y;

  posx *= FONT_WIDTH;
  posy *= FONT_HEIGHT;

  for (y = 0; y < FONT_HEIGHT; y++)
  {
    RENDER_ClrLine(line_buffer);

    if (cursor)
    {
      RENDER_StrLine(line_buffer, posx, y, menu_cursor_str); 
      RENDER_StrLine(line_buffer, posx + (CURSOR_X_OFFSET * FONT_WIDTH), y, str); 
    }
    else
    {
      RENDER_StrLine(line_buffer, posx + (CURSOR_X_OFFSET * FONT_WIDTH), y, str);
    }

    DISP_WriteLine(y + posy, line_buffer);
  }
}

static uint8_t read_joystick_raw(void)
{
  uint32_t mv;

  ADC0CN0_ADBUSY = 1;
  while (!ADC0CN0_ADINT);
  ADC0CN0_ADINT = 0;

  mv = ((uint32_t)ADC0) * 3300 / 1024;

  return JOYSTICK_convert_mv_to_direction(mv);
}

static uint8_t read_joystick(void)
{

  // debounce joystick by reading 3 times.
  // all 3 samples must match.

  uint8_t dir0, dir1, dir2;
    
  dir0 = read_joystick_raw();
  dir1 = read_joystick_raw();
  dir2 = read_joystick_raw();

  if ((dir0 == dir1) && (dir0 == dir2))
  {
    return dir0;
  }

  return JOYSTICK_NONE;
}

static uint8_t read_button0(void)
{

  // read button multiple times to debounce
  uint8_t i = 10;

  while (i > 0)
  {
    i--;

    if (BSP_PB0 != BSP_PB_PRESSED)  
    {

      return BSP_PB_UNPRESSED;
    }
  }

  return BSP_PB_PRESSED;
}

static uint8_t read_button1(void)
{
  // read button multiple times to debounce
  uint8_t i = 10;

  while (i > 0)
  {
    i--;

    if (BSP_PB1 != BSP_PB_PRESSED)  
    {

      return BSP_PB_UNPRESSED;
    }
  }

  return BSP_PB_PRESSED;
}

static void itoa (int32_t num, char *buf)
{
  char res[16];
  int n;
  char *p = &res[sizeof(res) - 1];

  n = num < 0 ? -num : num;

  *--p = 0;

  do 
  {
    *--p = (n % 10) + '0';
  } while (n /= 10);

  if (num < 0)
  {
    *--p = '-';
  }

  strcpy(buf, p);
}

static void print_ip_address(uint8_t line)
{
  // print IP address
  memset(temp_buffer, 0, sizeof(temp_buffer));
  strcpy(temp_buffer, ip_str);
  WGX_IP(&temp_buffer[4], sizeof(temp_buffer) - sizeof(ip_str));
  render_line(0, line, temp_buffer);  
}

static void draw_startup(void)
{
  DISP_ClearAll();

  render_line(0, 0, main_line_top);
  render_line(0, 1, menu_line_dashes);

  render_line(0, 4, efm8_wgx_startup_str);
}

static void draw_main(void)
{
  uint8_t i;

  DISP_ClearAll();

  render_line(0, 0, main_line_top);
  render_line(0, 1, menu_line_dashes);

  for (i = 0; i < cnt_of_array(main_menu_items); i++)
  {
    render_cursor_line(0, i + 2, menu_cursor == i, main_menu_items[i]);  
  }

  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, main_line_bottom);

  max_cursor = cnt_of_array(main_menu_items) - 1;
}

static void draw_program_list(void)
{
  uint32_t length;
  char *s;

  DISP_ClearAll();

  render_line(0, 0, proglist_line_top);
  render_line(0, 1, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, proglist_line_bottom);

  WGX_ListFiles(GENERIC_STREAM);

  file_count = 0;

  while (WGX_ReadFileList(GENERIC_STREAM, temp_buffer, sizeof(temp_buffer), &length) == WGX_STATUS_OK)
  {
    // search for *softAP.efm8. this file is from an old demo and we want to skip it.
    s = strstr(temp_buffer, softapefm8_line);  

    if (s != 0)
    {
      continue;
    }

    // search for file dir name prefix
    s = strstr(temp_buffer, file_dir_str);

    if (s != 0)
    {
      render_cursor_line(0, file_count + 2, file_count == menu_cursor, &s[strlen(file_dir_str)]);

      if (file_count == menu_cursor)
      {
        // remember name of current selection
        strcpy(current_file_str, temp_buffer);
        current_file_size = length;
      }

      file_count++;
      max_cursor = file_count - 1;
    }
  }

  if (file_count == 0)
  {
    render_line(0, 4, noprog_line); 
  }

  WGX_StreamClose(GENERIC_STREAM);
}

static void draw_wifi_list(void)
{
  uint8_t channel;
  int8_t rssi;
  uint8_t count;
  
  DISP_ClearAll();

  render_line(0, 0, wifilist_line_top);
  render_line(0, 1, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, line_bottom_back);

  render_line(0, 2, wifilist_scanning);

  WGX_ScanWifi(GENERIC_STREAM);
 
  count = 0;

  while ((WGX_ParseWifiList(GENERIC_STREAM, temp_ssid, &channel, temp_mac, &rssi) == WGX_STATUS_OK) &&
         (count < MAX_FILE_COUNT))
  {
    memset(temp_buffer, ' ', sizeof(temp_buffer));
    strcpy(temp_buffer, temp_ssid);
    temp_buffer[strlen(temp_ssid)] = ' ';
    itoa(rssi, &temp_buffer[18]);
    temp_buffer[DISP_MAX_LINES_X] = 0;

    render_line(0, count + 2, temp_buffer);

    count++;
    max_cursor = count - 1;
  }

  WGX_StreamClose(GENERIC_STREAM);
}

static void draw_server_info(void)
{
  render_line(0, 5, menu_line_dashes);
  render_line(0, 6, portsopen_str1);
  render_line(0, 7, disconnected_str);

  render_line(0, 11, menu_line_dashes);
  render_line(0, 12, portsopen_str2);
}

static void draw_wifi_softap(void)
{
  DISP_ClearAll();

  render_line(0, 0, wifisoftap_line_top);
  render_line(0, 1, wifisoftap_line_1);
  render_line(0, 2, wifisoftap_line_2);

  render_line(0, 3, starting_str);

  draw_server_info();
  
  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, line_bottom_back);
}

static void draw_web_setup(void)
{
  DISP_ClearAll();

  render_line(0, 0, websetup_line_top);
  render_line(0, 1, websetup_line_1);
  render_line(0, 2, wifisoftap_line_2);
  
  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, line_bottom_back);

  print_ip_address(5);
}

static void draw_web_setup_connecting(void)
{
  DISP_ClearAll();

  render_line(0, 0, websetup_line_top);

  render_line(0, 3, connecting_str);

  // get ssid
  WGX_SSID(temp_buffer, sizeof(temp_buffer));
  render_line(0, 5, temp_buffer);
}

static void draw_web_setup_complete(void)
{
  DISP_ClearAll();

  render_line(0, 0, connected_str);
  // get ssid
  WGX_SSID(temp_buffer, sizeof(temp_buffer));
  render_line(0, 1, temp_buffer);

  print_ip_address(3);  

  draw_server_info(); 

  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, line_bottom_back);
}

static void draw_page_confirm(void)
{
  DISP_ClearAll();

  render_line(0, 0, confirm_line_top);
  render_line(0, 1, menu_line_dashes);

  // render file name, but strip the dir prefix
  render_line(0, 6, &current_file_str[strlen(file_dir_str)]);

  // render file size to temp buffer
  memset(temp_buffer, 0, sizeof(temp_buffer));
  itoa(current_file_size, temp_buffer);
  strcpy(&temp_buffer[strlen(temp_buffer)], bytes_str);

  render_line(0, 7, temp_buffer);

  render_line(0, DISP_MAX_LINES_Y - 2, menu_line_dashes);
  render_line(0, DISP_MAX_LINES_Y - 1, confirm_line_bottom);
}

static void draw_page_loading(void)
{
  DISP_ClearAll();

  render_line(0, 0, loading_line_top);

  // render file name, but strip the dir prefix
  render_line(0, 6, &current_file_str[strlen(file_dir_str)]);
}

static void draw_page_error(void)
{
  DISP_ClearAll();
  render_line(0, DISP_MAX_LINES_Y / 2, error_str);
}

static void sleep_demo(void)
{
  uint8_t seconds = 10;

  // see note in UI_Init() for info on why we need to set the LEDs
  // to teal.
  RGB_SetColor(LED_TEAL, 64);

  WGX_Sleep(seconds);

  while (WGX_IsSleeping())
  {
    DISP_ClearAll();

    strcpy(temp_buffer, sleeping_str);

    itoa(seconds, &temp_buffer[strlen(sleeping_str)]);

    render_line(0, 7, temp_buffer);  

    Wait(1000);
    seconds--;
  }

  DISP_ClearAll();    
  render_line(0, 7, awake_str);
   Wait(1000);
}

static void start_servers(void)
{
  WGX_StartServer(WGX_PROTOCOL_TCP, DEMO_SERVER_PORT);
  WGX_StartServer(WGX_PROTOCOL_UDP, DEMO_SERVER_PORT);
}

static void stop_servers(void)
{
  WGX_StopServer(WGX_PROTOCOL_TCP, DEMO_SERVER_PORT);
  WGX_StopServer(WGX_PROTOCOL_UDP, DEMO_SERVER_PORT);

  WGX_StreamClose(TCP_STREAM);
  WGX_StreamClose(UDP_STREAM);
}

static void start_softap_demo(void)
{
  // start AP
  WGX_SoftAP(SOFTAP_SSID, SOFTAP_PASS);
  
  start_servers();
}

static void stop_softap_demo(void)
{
  stop_servers();

  WGX_Disconnect();
}

static void start_web_setup(void)
{
  // set to teal. See UI_Init() for explanation.
  // Web setup will trigger a reboot of the module.
  RGB_SetColor(LED_TEAL, 64);
  WGX_WebSetup(SETUP_SSID, SETUP_PASS);  
}

void UI_Init(void)
{
  // need to set teal so we drive the blue and green LEDs.
  // they are unfortunately wired to the GPIO0 factory reset
  // and GPIO5 web setup connections on the AMW007 EXP board,
  // so we need to make sure we are not continually asserting
  // either of them while the module is starting up.
  RGB_SetColor(LED_TEAL, 64);

  // initialize LCD
  DISP_Init();

  menu_page = PAGE_STARTUP;
  draw_startup();
}

void UI_Poll(void){

  uint8_t dir;
  bool redraw = false;
  uint8_t button0 = read_button0();
  uint8_t button1 = read_button1();
  int16_t len;

  // wait for comms
  if (WGX_Busy())
  {
    return;
  }

  // check if button has been pressed
  if (!button0_pressed && (button0 == BSP_PB_PRESSED))
  {
    button0_pressed = true;
  }
  // check if button was previously pressed and is still pressedacep
  else if (button0_pressed && (button0 == BSP_PB_PRESSED))
  { 
    // this is to require the button to be released between
    // presses to initiate the next available action.
    button0 = BSP_PB_UNPRESSED;
  }
  else if (button0_pressed && (button0 != BSP_PB_PRESSED))
  {
    button0_pressed = false;
  }

  // repeat with button 1  
  if (!button1_pressed && (button1 == BSP_PB_PRESSED))
  {
    button1_pressed = true;
  }
  else if (button1_pressed && (button1 == BSP_PB_PRESSED))
  { 
    button1 = BSP_PB_UNPRESSED;
  }
  else if (button1_pressed && (button1 != BSP_PB_PRESSED))
  {
    button1_pressed = false;
  }

  // check joystick
  dir = read_joystick();

  // process joystick
  if ((menu_page == PAGE_MAIN_MENU) ||
      (menu_page == PAGE_PROGRAM_LIST))
  {
    if (!joystick_pressed)
    {
      joystick_pressed = true;

      if (dir == JOYSTICK_N){

        if (menu_cursor > 0)
        {
          menu_cursor--;
          redraw = true;
        }
      }
      else if (dir == JOYSTICK_S){

        if ((menu_cursor < (DISP_MAX_LINES_Y - 3)) && (menu_cursor < max_cursor))
        {
          menu_cursor++;
          redraw = true;
        }
      }
      else
      {
        joystick_pressed = false;
      }
    }
    else if (dir == JOYSTICK_NONE)
    {
      joystick_pressed = false;
    }
  }

  // process buttons and other actions
  if (menu_page == PAGE_STARTUP)
  {
    menu_page = PAGE_MAIN_MENU; // change page
    redraw = true; 
  }
  else if (menu_page == PAGE_MAIN_MENU)
  {
    // check buttons
    if (button1 == BSP_PB_PRESSED) // select
    {
      if (menu_cursor == 0)
      { 
        menu_page = PAGE_WIFI_SOFTAP;
        redraw = true;

        draw_wifi_softap();
        start_softap_demo();
      }
      else if (menu_cursor == 1)
      {
        menu_page = PAGE_WEB_SETUP;
        redraw = true;

        draw_web_setup();
        start_web_setup();
      }
      else if (menu_cursor == 2)
      {
        menu_page = PAGE_WIFI_LIST;
        redraw = true;
      }
      else if (menu_cursor == 3)
      { 
        menu_page = PAGE_PROGRAM_LIST;
        redraw = true;
      }
      else if (menu_cursor == 4)
      { 
        // this demo is self-contained, so we don't need to change the page
        sleep_demo();

        // need to redraw after demo to get main screen back
        redraw = true;
      }

      // reset menu cursor
      menu_cursor = 0;
    }
  }
  else if (menu_page == PAGE_PROGRAM_LIST)
  {
    // check buttons
    if (button1 == BSP_PB_PRESSED) // load file
    {

      // only change to file confirm page if we have
      // files available

      if (file_count > 0)
      {
        menu_page = PAGE_CONFIRM; // change page
        redraw = true;
      }
    }
    else if (button0 == BSP_PB_PRESSED) // back to main
    {
      menu_page = PAGE_MAIN_MENU; // change page
      redraw = true; 

      // reset menu cursor
      menu_cursor = 0;
    }
  }
  else if (menu_page == PAGE_CONFIRM)
  {
    // check buttons
    if (button1 == BSP_PB_PRESSED) // cancel
    {
      menu_page = PAGE_PROGRAM_LIST; // change page
      redraw = true;

      // reset menu cursor
      menu_cursor = 0;
    }
    else if (button0 == BSP_PB_PRESSED) // load file
    {
      // loading file.  this involves a trip to the bootloader, which mains
      // the main app is terminating here. we need to update the display
      // since we aren't going to make it to our redraw function.
      draw_page_loading();

      // bootload function will NOT return
      WGX_Bootload(current_file_str);

      // !!!!!!
      // cannot get here
    }
  }
  else if (menu_page == PAGE_WIFI_LIST)
  {
    if (button0 == BSP_PB_PRESSED) // back
    {
      menu_page = PAGE_MAIN_MENU; // change page
      redraw = true; 

      // reset menu cursor
      menu_cursor = 0;
    }
  }
  else if (menu_page == PAGE_WEB_SETUP)
  { 
    if (WGX_IsWebSetupConnecting())
    {
      menu_page = PAGE_WEB_SETUP_CONNECTING;
      redraw = true;
    }
    
    if (button0 == BSP_PB_PRESSED) // back
    {
      // only way to get out of web setup mode is to reboot module
      WGX_Reboot();

      menu_page = PAGE_MAIN_MENU; // change page
      redraw = true; 

      // reset menu cursor
      menu_cursor = 0;
    }
  }
  else if (menu_page == PAGE_WEB_SETUP_CONNECTING)
  {
    if (WGX_IsWebSetup()){
      // connection failed and we're back in setup mode
      menu_page = PAGE_WEB_SETUP;
      redraw = true;

      render_line(0, 5, failed_str);

      Wait(2000);
    }
    else if (WGX_IsConnected())
    {
      start_servers();
      menu_page = PAGE_WEB_SETUP_COMPLETE;
      redraw = true; 
    }
  }
  else if (menu_page == PAGE_WEB_SETUP_COMPLETE)
  {
    WGX_RSSI(&current_rssi);

    // print RSSI
    memset(temp_buffer, 0, sizeof(temp_buffer));
    strcpy(temp_buffer, rssi_str);
    itoa(current_rssi, &temp_buffer[5]); 
    render_line(0, 2, temp_buffer);

    if (button0 == BSP_PB_PRESSED) // back
    {
      stop_servers();

      menu_page = PAGE_MAIN_MENU; // change page
      redraw = true; 

      // reset menu cursor
      menu_cursor = 0;
    }
  }
  else if (menu_page == PAGE_WIFI_SOFTAP)
  {
    print_ip_address(3);

    // print number of clients connected
    memset(temp_buffer, 0, sizeof(temp_buffer));
    strcpy(temp_buffer, clients_str);
    itoa(WGX_SoftAPClientCount(), &temp_buffer[9]); 
    render_line(0, 4, temp_buffer);

    if (button0 == BSP_PB_PRESSED) // back
    {
      stop_softap_demo();

      menu_page = PAGE_MAIN_MENU; // change page
      redraw = true; 

      // reset menu cursor
      menu_cursor = 0;
    }
  }

  // server demo handlers
  if ((menu_page == PAGE_WIFI_SOFTAP) || (menu_page == PAGE_WEB_SETUP_COMPLETE))
  {
    // check for TCP connection
    if (WGX_AcceptConnection(WGX_PROTOCOL_TCP, TCP_STREAM) == WGX_STATUS_OK)
    {
      render_line(0, 7, connected_str); 

      // send a hello!
      WGX_StreamWrite(TCP_STREAM, hello_str, sizeof(hello_str)); 
    }

    // check if connected
    if (WGX_StreamStatus(TCP_STREAM) == WGX_STATUS_CLOSED)
    {
      render_line(0, 7, disconnected_str);
      render_line(0, 9, clear_str);
    }

    // read any data from TCP connection
    memset(temp_buffer, 0, sizeof(temp_buffer));
    len = WGX_StreamRead(TCP_STREAM, temp_buffer, sizeof(temp_buffer));

    if (len > 0)
    {
      render_line(0, 9, temp_buffer);

      // echo back
      WGX_StreamWrite(TCP_STREAM, temp_buffer, len);
    }

    // check for UDP connection
    WGX_AcceptConnection(WGX_PROTOCOL_UDP, UDP_STREAM);
    
    // read any data on UDP stream, if available
    len = WGX_StreamRead(UDP_STREAM, temp_buffer, sizeof(temp_buffer));

    if (len > 0)
    {
      render_line(0, 13, temp_buffer);

      // echo back
      WGX_StreamWrite(UDP_STREAM, temp_buffer, len);
    }
  }
  
  // check if screen redraw requested
  if (redraw)
  {
    if (menu_page == PAGE_MAIN_MENU)
    {
      draw_main();
    }
    else if (menu_page == PAGE_PROGRAM_LIST)
    {   
      draw_program_list();
    }
    else if (menu_page == PAGE_CONFIRM)
    {
      draw_page_confirm();
    }
    else if (menu_page == PAGE_LOADING)
    {
      draw_page_loading();
    }
    else if (menu_page == PAGE_WIFI_LIST)
    {
      draw_wifi_list();
    }
    else if (menu_page == PAGE_WIFI_SOFTAP)
    {
      draw_wifi_softap();
    }
    else if (menu_page == PAGE_WEB_SETUP)
    {
      draw_web_setup();
    }
    else if (menu_page == PAGE_WEB_SETUP_CONNECTING)
    {
      draw_web_setup_connecting();
    }
    else if (menu_page == PAGE_WEB_SETUP_COMPLETE)
    {
      draw_web_setup_complete();
    }
  }

  if (WGX_IsWebSetup() || WGX_IsWebSetupConnecting() )
  {
    // must set to teal in web setup mode, because the web setup
    // will trigger a reboot.  See UI_Init for explanation.
    RGB_SetColor(LED_TEAL, 64);
  }
  else if (WGX_IsConnected())
  {
    RGB_SetColor(LED_BLUE, 64);
  }
  else
  {
    RGB_SetColor(LED_GREEN, 64);
  }
}



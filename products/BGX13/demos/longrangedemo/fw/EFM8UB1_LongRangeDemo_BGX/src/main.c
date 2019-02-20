/*****************************************************************************/
/* Includes                                                                  */
/*****************************************************************************/

//#include "pwr.h" // Note: unnecessary unless we use the power management functions
#include "bsp.h"
#include "tick.h"
#include "InitDevice.h"

#include "uart_1.h"
#include "BGX_uart.h"
#include "bgx.h"

#include "draw.h"
#include "light.h"
#include "scanning.h"
#include "led_control.h"

/*-----------------------------------------------------------------------------
 * Resources:
 *-----------------------------------------------------------------------------
 * SYSCLK - 24.5 MHz HFOSC / 4
 * SPI1   - 500 kHz
 * Timer2 - 2 MHz (SPI CS delay)
 * Timer3 - 1 kHz (1 ms tick)
 * P0.2 - Push Button 0
 * P0.3 - Push Button 1
 * P0.4 - UART1 TX
 * P0.5 - UART1 RX
 * P1.0 - LCD SCK
 * P1.2 - LCD MOSI
 * P1.4 - RGB pin 1
 * P1.5 - LCD CS (Active High)
 * P1.6 - RGB pin 2
 * P1.6 - BGX Mode GPIO (0 - COMMAND_MODE / 1 - STREAM_MODE)
 * P2.0 - LED Green
 * P2.1 - LED Blue
 * P2.2 - LED Red
 * P2.6 - Display enable
 * ---------------------------------------------------------------------------*/

/**************************************************************************//**
 * SiLabs_Startup Routine.
 *
 * This function is called immediately after reset, before the initialization
 * code is run in SILABS_STARTUP.A51 (which runs before main() ). This is a
 * useful place to disable the watchdog timer, which is enable by default
 * and may trigger before main() in some instances.
 *****************************************************************************/
void SiLabs_Startup(void)
{
  // Disable the watchdog here
}

/**************************************************************************//**
 * This example shows us the capabilities of the BGX board.
 *
 * This example demonstrates the stream and command functionality of the BGX
 * in Peripheral and Central mode using the STK, LCD, push buttons, joystick,
 * and the Silabs BGX Commander mobile app.
 *****************************************************************************/
void main(void)
{
  // Initialize the device
  enter_DefaultMode_from_RESET();

  // Enable all interrupts
  IE_EA = 1;

  // Board controller disconnected from EFM8 UART pins
  BSP_BC_EN = BSP_BC_DISCONNECTED;

  // Resets the BGX board and initializes it's baud rates and GPIOs
  UART1_init(6125000, 115200, UART1_DATALEN_8, UART1_STOPLEN_SHORT,
             UART1_FEATURE_DISABLE,
             UART1_PARITY_ODD, UART1_RX_ENABLE, UART1_MULTIPROC_DISABLE);
  UART1_initTxFifo(UART1_TXTHRSH_ZERO, UART1_TXFIFOINT_DISABLE);
  UART1_initRxFifo(UART1_RXTHRSH_ZERO,
                   UART1_RXTIMEOUT_16,
                   UART1_RXFIFOINT_ENABLE);

  // Enable the device to start listening/receiving data
  listenerOn();

  // Initialize the BGX device and LCD display
  BGX_init();
  drawInit();
  LED_init();

  // Pause to ensure that the LCD has finished initialization
  Wait(100);

  // Get the device name from the BGX device
  BGX_getDeviceName();

  // Draw the title screen
  Light_drawTitleScreen();

  // Wait for a button press
  while ((BSP_PB0 == BSP_PB_UNPRESSED) && (BSP_PB1 == BSP_PB_UNPRESSED))
    ;

  // Button PB0: Light mode
  if (BSP_PB0 == BSP_PB_PRESSED)
  {
    Light_main();
  }

  // Button PB1: Scanning mode
  if (BSP_PB1 == BSP_PB_PRESSED)
  {
    Scanning_main();
  }
}


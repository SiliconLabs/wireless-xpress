//-----------------------------------------------------------------------------
// Includes
//-----------------------------------------------------------------------------
#include "bsp.h"
#include "efm8_config.h"
#include "SI_EFM8SB1_Defs.h"
#include <stdio.h>
#include <string.h>
#include "InitDevice.h"
#include "tempMeasurement.h"
#include "delay.h"
#include "BGX_uart.h"
#include "uart_0.h"
#include "power.h"
#include "SmaRTClock.h"

typedef enum {pb1_pressed, bgx_connected, bgx_disconnected_or_data_received_or_rtc}event;
void init_example(void);
void sleep_with_wake(event wake_event);

/**************************************************************************//**
 * ----------------------------------------------------------------------------
 * How To Test: EFM8SB1 STK + BGX Board
 * ----------------------------------------------------------------------------
 * NOTE: If a change is made to EFM8SB1_BGX.hwconf, an Interrupts.c will be
 *       generated. Interrupts.c must be deleted to avoid compilation
 *       errors.
 * NOTE: To achieve lowest power, LED0 must be removed from the EFM8SB1 STK.
 *       It is connected to BTN1 on the BGX Board which drives the LED on.
 *
 * Target:         EFM8SB1
 * Tool chain:     Generic
 *
 * Release 0.1 (MD;AT)
 *    - Initial Revision
 *    - 15 NOV 2018
 *
 *-----------------------------------------------------------------------------
 * Resources:
 *-----------------------------------------------------------------------------
 * SYSCLK - 10 MHz Low Power Oscillator
 * Timer3 - 1 kHz (1 ms tick)
 * P1.2 - Push Button 0
 * P1.3 - Push Button 1
 * P0.4 - UART0 TX
 * P0.5 - UART0 RX
 * P0.1 - BGX Mode Pin (1 - COMMAND_MODE / 0 - STREAM_MODE)
 * ----------------------------------------------------------------------------
 *****************************************************************************/

/**************************************************************************//**
 * SiLabs_Startup() Routine
 * ----------------------------------------------------------------------------
 * This function is called immediately after reset, before the initialization
 * code is run in SILABS_STARTUP.A51 (which runs before main() ). This is a
 * useful place to disable the watchdog timer, which is enable by default
 * and may trigger before main() in some instances.
 *****************************************************************************/
void SiLabs_Startup(void)
{

}

/**************************************************************************//**
 * Connects to a BGX and sends temperature sensor data via uart in a low
 * power mode.
 *****************************************************************************/
int main(void)
{
  int16_t new_cal_value;

  // Initializes the EFM8SB1 STK
  init_example();
  // Resets the BGX board with 2 baud rates accounted for
  BGX_reset(9600);
  BGX_reset(115200);
  // Sets baud rate to 9600 for low power
  BGX_setBaudRate();
  // Puts the BGX BLE command interface to machine mode for easy parsing
  BGX_Write("set sy c m machine\r", GET_RESPONSE);
  // Turns advertising off
  BGX_Write("adv off\r", GET_RESPONSE);
  // Clears GPIO4, as it is connected to PB1
  BGX_Write("gfu 4 none\r", GET_RESPONSE);
  // Sets CONNECTION_PIN
  BGX_Write("gfu 6 none\r", GET_RESPONSE);
  BGX_Write("gfu 6 con_active_n\r", GET_RESPONSE);
  // Decreases BLE advertising high duration 30s -> 5s
  BGX_Write("set bl v h d 5\r", GET_RESPONSE);

  while (1)
  {
    // BGX and EFM8SB1 sleep until PB1 is pressed
    BGX_Write("sleep\r", GET_RESPONSE);
    delayAndWaitFor(20);
    sleep_with_wake(pb1_pressed);
    while (BSP_PB1 == BSP_PB_UNPRESSED);

    // EFM8SB1 sleeps until BGX is connected to via BGX Commander
    BGX_Write("wake\r", GET_RESPONSE);
    delayAndWaitFor(20);
    sleep_with_wake(bgx_connected);
    while (CONNECTION_PIN == DISCONNECTED);

    // Connecting to BGX Commander will take a significant amount of
    // time if the BLE connection interval was 500ms so we can quickly
    // set it after establishing a connection

    // Exit Stream mode, enter Command mode
    BGX_sendBreakoutSequence();

    // Increase BLE connection interval 12 (15 ms) -> 625 (500 ms)
    // Units: 1.25 ms
    BGX_Write("set bl c i 625\r", GET_RESPONSE);

    // Enter stream mode
    BGX_Write("str\r", IGNORE_RESPONSE);
    delayAndWaitFor(500);

    listenerOn();

    P0MASK = 0x22;    // Wake up on CONNECTION_PIN high or UART_RX low
    P0MAT = 0xFD;

    while (1)
    {
      RTC_Alarm = 0;
      Port_Match_Wakeup = 0;
      PMU0CF |= 0x20; // Clear flags
      PMU0CF = 0x86;  // Enter Sleep mode with RTC and Port Match Wake Up

      if(RTC_Alarm)
      {
        ADC0CN0 |= ADC0CN0_ADEN__ENABLED;  // Enable ADC
        delayAndWaitFor(20);
        BGX_Write (getTempString(BGX_transmitBuffer), IGNORE_RESPONSE);
        delayAndWaitFor(50);
        RTC_Alarm = 0;
        ADC0CN0 &= ~ADC0CN0_ADEN__ENABLED; // Disable ADC
      }

      if(Port_Match_Wakeup)
      {
        if(CONNECTION_PIN == DISCONNECTED)
        {
          Port_Match_Wakeup = 0;
          break;
        }
        else
        {
          delayFor(500);
          while(!delayIsFinished())
          {
            if(listenerFoundLineEnd())
            {
              sscanf(BGX_receiveBuffer, "%d", &new_cal_value);
              calibrateWith(new_cal_value);
              listenerReset();
            }
          }
        }
        Port_Match_Wakeup = 0;
      }
    }
  }
}

/**************************************************************************//**
 * Initializes EFM8SB1.
 *****************************************************************************/
void init_example(void)
{
  // Initialize the device
  enter_DefaultMode_from_RESET();
  // Initialize the UART
  UART0_init(UART0_RX_ENABLE, UART0_WIDTH_8, UART0_MULTIPROC_DISABLE);
  // Display not driven by EFM8
  BSP_DISP_EN = BSP_DISP_BC_DRIVEN;
  // Board controller disconnected from EFM8 UART pins
  BSP_BC_EN = BSP_BC_DISCONNECTED;
}

/**************************************************************************//**
 * Places EFM8SB1 to sleep with a given wake condition.
 *****************************************************************************/
void sleep_with_wake(event wake_event)
{
  uint8_t CLKSEL_save;
  CLKSEL_save = CLKSEL;
  CLKSEL = 0x04;            // Set sysclk to low power oscillator divided by 1
  while(!(CLKSEL & 0x80));

  if(wake_event == bgx_disconnected_or_data_received_or_rtc)
  {
    P0MASK = 0x22;    // Wake up on MODE_PIN high or UART_RX low
    P0MAT = 0xFD;
    PMU0CF |= 0x20;         // Clear flags
    PMU0CF = 0x86;          // Enter Sleep mode with RTC and Port Match Wake Up
  }
  else if(wake_event == bgx_connected)
  {
    PMU0CF |= 0x20;         // Clear flags
    P0MASK = 0x02;          // Wake up on MODE_PIN low only
    P1MASK = 0x00;
    P0MAT = 0xFF;
    PMU0CF = 0x82;          // Enter Sleep mode with Port Match Wake Up
  }
  else if(wake_event == pb1_pressed)
  {
    PMU0CF |= 0x20;         // Clear flags
    delayAndWaitFor(20);
    P1MASK = 0x08;          // Wake up on PB1 only
    P0MASK = 0x00;
    PMU0CF = 0x82;          // Enter Sleep mode with Port Match Wake Up
  }
  NOP(); NOP(); NOP(); NOP();
  CLKSEL = CLKSEL_save;     // Restore the System Clock
}

#include "bsp.h"
#include "efm8_config.h"
#include "SI_EFM8UB1_Defs.h"
#include <stdio.h>
#include "InitDevice.h"
#include "tempMeasurement.h"
#include "delay.h"
#include "BGX_uart.h"
#include "uart_1.h"

void init_example(void);


/**************************************************************************//**
 * Program Description:
 *
 * ----------------------------------------------------------------------------
 * How To Test: EFM8UB1 STK + BGX Board
 * ----------------------------------------------------------------------------

 * NOTE: If a change is made to EFM8UB1_BGX.hwconf, an Interrupts.c will be
 *       generated. Interrupts.c must be deleted to avoid compilation
 *       errors.
 * 
 * Target:         EFM8SB2
 * Tool chain:     Generic
 *
 * Release 0.1 (MD;AT)
 *    - Initial Revision
 *    - 7 JUN 2018
 *
 *-----------------------------------------------------------------------------
 * Resources:
 *-----------------------------------------------------------------------------
 * SYSCLK - 24.5 MHz HFOSC / 4
 * Timer3 - 1 kHz (1 ms tick)
 * P0.2 - Push Button 0
 * P0.3 - Push Button 1
 * P0.4 - UART0 TX
 * P0.5 - UART0 RX
 * P1.6 - BGX Mode GPIO (0 - COMMAND_MODE / 1 - STREAM_MODE)
 * ----------------------------------------------------------------------------
 *
 *****************************************************************************/

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
 *
 *****************************************************************************/
void main(void)
{
  int16_t new_cal_value;

  init_example();

  BGX_Write("set sy c m machine\r");
  BGX_Write("adv off\r");
  BGX_Write("gfu 6 none\r");
  BGX_Write("gfu 6 str_active_n\r");

  BGX_Write("set sy i s 00000404\r");
  BGX_Write("gfu 0 none\r");
  BGX_Write("gfu 0 con_status_led\r");

  while(1)
  {
     BGX_Write("adv off\r");
     while(BSP_PB1 != BSP_PB_PRESSED);

     BGX_Write("adv high\r");
     while(MODE_PIN == COMMAND_MODE);

     listenerOn();

     do
     {
        BGX_Write(getTempString(BGX_transmitBuffer));
        delayFor(1000);

        while(!delayIsFinished())
        {
           if(listenerFoundLineEnd())
           {
              sscanf(BGX_receiveBuffer, "%d", &new_cal_value);
              calibrateWith(new_cal_value);
              listenerReset();
           }
        }

     } while(MODE_PIN == STREAM_MODE);

  }
}

void init_example(void)
{
  // Initialize the device
  enter_DefaultMode_from_RESET();
  // Board controller disconnected from EFM8 UART pins
  BSP_BC_EN = BSP_BC_DISCONNECTED;
  // Resets the BGX board and initializes it's baud rates and GPIOs
  BSP_DISP_EN = BSP_DISP_BC_DRIVEN;   // Display not driven by EFM8

  UART1_init(6125000, 115200, UART1_DATALEN_8, UART1_STOPLEN_SHORT,
               UART1_FEATURE_DISABLE, UART1_PARITY_ODD, UART1_RX_ENABLE, UART1_MULTIPROC_DISABLE);
  UART1_initTxFifo(UART1_TXTHRSH_ZERO, UART1_TXFIFOINT_DISABLE);
  UART1_initRxFifo(UART1_RXTHRSH_ZERO, UART1_RXTIMEOUT_16, UART1_RXFIFOINT_ENABLE);

  IE_EA = 1;

  BGX_RESET_PIN = BGX_RESET_PIN&0;
  delayAndWaitFor(50);

  BGX_RESET_PIN = BGX_RESET_PIN|1;

  if(BGX_didReceiveLine("COMMAND_MODE"))
  {
    // Indicates BGX has exited reset and can receive commands
  }
  else
  {
    // Error condition.  Should not happen in this example.  Simply fall through.
  }

}

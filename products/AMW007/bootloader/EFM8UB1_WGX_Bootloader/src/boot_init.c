#include "efm8_device.h"
#include "boot.h"
#include "hboot_config.h"
#include "leds.h"

uint8_t reset_source;

#if EFM8_UART == 0
static void uart0_setup(void)
{
  // P0MDOUT - Port 0 Output Mode
  // P0.4 (UART0.TX) = PUSH_PULL
  P0MDOUT = P0MDOUT_B4__PUSH_PULL;

  // XBR0 - Port I/O Crossbar 0
  // URT0E (UART0 I/O Enable) = ENABLED (UART0 TX0, RX0 routed to Port pins P0.4 and P0.5.)
  XBR0 = XBR0_URT0E__ENABLED;

  // XBR2 - Port I/O Crossbar 2
  // WEAKPUD (Port I/O Weak Pullup Disable) = PULL_UPS_ENABLED (Weak Pullups enabled.)
  // XBARE (Crossbar Enable) = ENABLED (Crossbar enabled.)
  XBR2 = XBR2_WEAKPUD__PULL_UPS_ENABLED | XBR2_XBARE__ENABLED;

  // configure timer 1 clock to SYSCLK
  CKCON0 = CKCON0_T1M__SYSCLK;

  // set baud to 115200
  TH1 = (0x98 << TH1_TH1__SHIFT);

  TMOD = TMOD_T1M__MODE2;         // 8-bit timer with auto-reload
  TCON_TR1 = 1;

  // Enable UART0 receiver  
  SCON0_REN = 1;
}
#else
static void uart1_setup(void)
{
  uint8_t save_page = SFRPAGE;

  SFRPAGE = 0x20;

  P0MDOUT = P0MDOUT_B0__OPEN_DRAIN | P0MDOUT_B1__OPEN_DRAIN
        | P0MDOUT_B2__OPEN_DRAIN | P0MDOUT_B3__OPEN_DRAIN
        | P0MDOUT_B4__OPEN_DRAIN | P0MDOUT_B5__OPEN_DRAIN
        | P0MDOUT_B6__OPEN_DRAIN | P0MDOUT_B7__OPEN_DRAIN;

  P0SKIP = P0SKIP_B0__SKIPPED | P0SKIP_B1__SKIPPED | P0SKIP_B2__SKIPPED
      | P0SKIP_B3__SKIPPED | P0SKIP_B4__SKIPPED
      | P0SKIP_B5__SKIPPED | P0SKIP_B6__SKIPPED
      | P0SKIP_B7__SKIPPED;

  P1MDOUT = P1MDOUT_B0__OPEN_DRAIN | P1MDOUT_B1__OPEN_DRAIN
      | P1MDOUT_B2__OPEN_DRAIN | P1MDOUT_B3__OPEN_DRAIN
      | P1MDOUT_B4__OPEN_DRAIN | P1MDOUT_B5__OPEN_DRAIN
      | P1MDOUT_B6__OPEN_DRAIN | P1MDOUT_B7__OPEN_DRAIN;

  P1SKIP = P1SKIP_B0__SKIPPED | P1SKIP_B1__SKIPPED | P1SKIP_B2__SKIPPED
      | P1SKIP_B3__SKIPPED | P1SKIP_B4__SKIPPED | P1SKIP_B5__SKIPPED
      | P1SKIP_B6__SKIPPED | P1SKIP_B7__SKIPPED;

  P2MDOUT = P2MDOUT_B1__PUSH_PULL;
  
  // XBR2 - Port I/O Crossbar 2
  // WEAKPUD (Port I/O Weak Pullup Disable) = PULL_UPS_ENABLED (Weak Pullups enabled.)
  // XBARE (Crossbar Enable) = ENABLED (Crossbar enabled.)
  // URTE1 (UART1 I/O Enable) = ENABLED (UART1 TX1, RX1 routed to Port pins P2.1 and P2.2)
  XBR2 = XBR2_WEAKPUD__PULL_UPS_ENABLED | XBR2_XBARE__ENABLED
      | XBR2_URT1E__ENABLED;

  P2SKIP = P2SKIP_B0__SKIPPED | P2SKIP_B1__NOT_SKIPPED
      | P2SKIP_B2__NOT_SKIPPED | P2SKIP_B3__SKIPPED;
  P2MAT = P2MAT_B0__LOW | P2MAT_B1__HIGH | P2MAT_B2__HIGH;

  SBCON1 = SBCON1_BREN__ENABLED | SBCON1_BPS__DIV_BY_1;
  UART1FCN0 = UART1FCN0_RXTH__ZERO | UART1FCN0_TXTH__ZERO
      | UART1FCN0_RFRQE__DISABLED | UART1FCN0_TFRQE__DISABLED | UART1FCN0_TFLSH__FLUSH;

  // set baud to 115200
  SBRLH1 = (0xFF << SBRLH1_BRH__SHIFT);
  SBRLL1 = (0x96 << SBRLL1_BRL__SHIFT);
  SCON1 |= SCON1_REN__RECEIVE_ENABLED;

  SFRPAGE = save_page;
}
#endif

// ----------------------------------------------------------------------------
// Initialize all hardware resources needed by the bootloader.
// ----------------------------------------------------------------------------
void boot_initDevice(void)
{
  // Disable Watchdog with key sequence
  WDTCN = 0xDE;
  WDTCN = 0xAD;

  // Save reset source.
  // This is so we can check later if there was a power on reset.
  // We will not be able to check the RSTSRC register because we 
  // need to write a 1 to the PORSF bit to enable the supply monitor.
  reset_source = RSTSRC;

  // Enable VDD monitor and set it as a reset source
  VDM0CN |= VDM0CN_VDMEN__ENABLED;
  RSTSRC = RSTSRC_PORSF__SET;

  // CLKSEL - Clock Select
  // CLKSL (Clock Source Select) = HFOSC0 (Clock derived from the Internal
  //     High Frequency Oscillator 0.)
  // CLKDIV (Clock Source Divider) = SYSCLK_DIV_1 (SYSCLK is equal to
  //     selected clock source divided by 1.)
  CLKSEL = CLKSEL_CLKSL__HFOSC0 | CLKSEL_CLKDIV__SYSCLK_DIV_1;

  #if EFM8_UART == 0
  uart0_setup();

  #ifdef ENABLE_BOARD_CONTROLLER
  // Enable board controller on P2.0
  // This connects UART0 to the virtual com port provided by the
  // board controller.
  // This is only needed when running on the evaluation board.
  // User hardware can remove this line.
  P2MDOUT |= P2MDOUT_B0__PUSH_PULL;
  P2_B0 = 1;
  #endif
  #ifdef DISABLE_BOARD_CONTROLLER
  // Disable board controller on P2.0
  // Disconnects UART0 from board controller. This allows UART0 
  // to be used on the IO breakout connections.
  P2MDOUT |= P2MDOUT_B0__PUSH_PULL;
  P2_B0 = 0;
  #endif
  
  #else
  uart1_setup();
  #endif

  // configure LEDs 
  #ifdef ENABLE_LEDS
  P1MDOUT |= P1MDOUT_B4__PUSH_PULL | P1MDOUT_B5__PUSH_PULL | P1MDOUT_B6__PUSH_PULL;

  LED0 = 0;
  #endif
}

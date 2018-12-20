//-----------------------------------------------------------------------------
// Includes
//-----------------------------------------------------------------------------
#include "InitDevice.h"

#include "wdt_0.h"
#include "ui.h"
#include "wgx.h"

#include <string.h>

//-----------------------------------------------------------------------------
// SiLabs_Startup() Routine
// ----------------------------------------------------------------------------
// This function is called immediately after reset, before the initialization
// code is run in SILABS_STARTUP.A51 (which runs before main() ). This is a
// useful place to disable the watchdog timer, which is enable by default
// and may trigger before main() in some instances.
//-----------------------------------------------------------------------------
void SiLabs_Startup(void) {
  
  WDT0_stop();
}

//-----------------------------------------------------------------------------
// main() Routine
// ----------------------------------------------------------------------------
int main(void) {
  // Call hardware initialization routine
  enter_DefaultMode_from_RESET();

  // Enable all interrupts
  IE_EA = 1;

  // initialize user interface
  UI_Init();
  
  WGX_Init();

  while (1) {
      
    // process user interface
    UI_Poll();

    WGX_Poll();
  }
}



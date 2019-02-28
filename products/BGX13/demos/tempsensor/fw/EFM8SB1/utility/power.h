//-----------------------------------------------------------------------------
// power.h
//-----------------------------------------------------------------------------
// Program Description:
//
// Driver for the  power management function.
//
// Target:         EFM8SB1
// Tool chain:     Generic
// Command Line:   None
//
// Release 1.0
//    - Initial Revision (FB)
//    - 19 MAY 2010
//

//-----------------------------------------------------------------------------
// Register and Bit Definitions
//-----------------------------------------------------------------------------

// Power Management Bit Definitions
#define SLEEP        0x80              // Sleep Mode Select
#define SUSPEND      0x40              // Suspend Mode Select
#define CLEAR        0x20              // Wake-Up Flag Clear
#define RSTWK        0x10              // Reset Pin Falling Edge Wake-Up
#define RTCFWK       0x08              // SmaRTClock Failure Wake-Up
#define RTCAWK       0x04              // SmaRTClock Alarm Wake-Up
#define PMATWK       0x02              // Port Match Wake-Up
#define CMP0WK       0x01              // Comparator0 Wake-Up

// Friendly names for the LPM function arguments
#define PORT_MATCH   PMATWK
#define RTC          RTCFWK + RTCAWK
#define COMPARATOR   CMP0WK

// FLSCL Bit Definition
#define BYPASS    0x40
#define NON_ZERO  0x01

//-----------------------------------------------------------------------------
// Extern Variable Definitions
//-----------------------------------------------------------------------------
extern uint8_t PMU0CF_Local; 

extern uint8_t RTC_Alarm;
extern uint8_t RTC_Failure;
extern uint8_t Comparator_Wakeup;
extern uint8_t Port_Match_Wakeup;

//-----------------------------------------------------------------------------
// Extern Function PROTOTYPES
//-----------------------------------------------------------------------------

extern void LPM_Init (void);
extern void LPM(uint8_t mode);
extern void LPM_Enable_Wakeup (uint8_t wakeup);
extern void LPM_Disable_Wakeup (uint8_t wakeup);

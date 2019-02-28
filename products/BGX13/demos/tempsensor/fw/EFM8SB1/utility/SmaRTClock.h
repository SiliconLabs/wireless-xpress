//-----------------------------------------------------------------------------
// SmaRTClock.h
//-----------------------------------------------------------------------------
// Program Description:
//
// Driver for the SmaRTClock peripheral.
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
// RTC Configuration Options
//-----------------------------------------------------------------------------
//
// Please configure the following options
//
#define SYSCLK  20000000

#define SMARTCLOCK_ENABLED   1

#define SELFOSC 0
#define CRYSTAL 1

// RTC Clock Source
#define RTC_CLKSRC   CRYSTAL

// Crystal Load Capacitance
#define LOADCAP_VALUE  3               //  0 =  4.0 pf,  1 =  4.5 pf
                                       //  2 =  5.0 pf,  3 =  5.5 pf
                                       //  4 =  6.0 pf,  5 =  6.5 pf
                                       //  6 =  7.0 pf,  7 =  7.5 pf 
                                       //  8 =  8.0 pf,  9 =  8.5 pf
                                       // 10 =  9.0 pf, 11 =  9.5 pf
                                       // 12 = 10.5 pf, 13 = 11.5 pf
                                       // 14 = 12.5 pf, 15 = 13.5 pf

#if RTC_CLKSRC == SELFOSC
   #define RTCCLK   31500
#else
   #define RTCCLK   32768
#endif

// Constants for determining the RTC wake-up interval
#define WAKE_INTERVAL   100         // Wakeup-interval in milliseconds
#define WAKE_INTERVAL_TICKS  ((RTCCLK * WAKE_INTERVAL) / 1000L)
#define ONE_MS_TICKS         ((RTCCLK * 1 ) / 1000L)

// SmaRTClock Internal Registers
#define CAPTURE0  0x00                 // RTC address of CAPTURE0 register
#define CAPTURE1  0x01                 // RTC address of CAPTURE1 register
#define CAPTURE2  0x02                 // RTC address of CAPTURE2 register
#define CAPTURE3  0x03                 // RTC address of CAPTURE3 register
#define RTC0CN0    0x04                 // RTC address of RTC0CN0 register
#define RTC0XCN   0x05                 // RTC address of RTC0XCN register
#define RTC0XCF   0x06                 // RTC address of RTC0XCF register
#define RTC0PIN   0x07                 // RTC address of RTC0PIN register
#define ALARM0    0x08                 // RTC address of ALARM0 register
#define ALARM1    0x09                 // RTC address of ALARM1 register
#define ALARM2    0x0A                 // RTC address of ALARM2 register
#define ALARM3    0x0B                 // RTC address of ALARM3 register

// SmaRTClock Bit Definitions
#define RTC0CAP   0x01
#define RTC0SET   0x02
#define ALRM      0x04
#define RTC0AEN   0x08
#define RTC0TR    0x10
#define OSCFAIL   0x20
#define MCLKEN    0x40
#define RTC0EN    0x80

//-----------------------------------------------------------------------------
// Variables
//-----------------------------------------------------------------------------
extern uint8_t RTC0CN0_Local;                 // Holds the desired RTC0CN0 settings
 
//-----------------------------------------------------------------------------
// Function PROTOTYPES
//-----------------------------------------------------------------------------
extern void RTC_Init (void);
extern uint8_t   RTC_Read (uint8_t reg);
extern void RTC_Write (uint8_t reg, uint8_t value);
extern void RTC_WriteAlarm (uint32_t alarm);
extern uint32_t  RTC_GetCurrentTime(void);
extern void RTC_SetCurrentTime(uint32_t time);
extern void RTC0CN0_SetBits(uint8_t bits);
extern void RTC0CN0_ClearBits(uint8_t bits);
extern void RTC_SleepTicks(uint32_t ticks);

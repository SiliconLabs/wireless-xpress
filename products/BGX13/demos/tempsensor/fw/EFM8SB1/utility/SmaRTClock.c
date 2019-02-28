//-----------------------------------------------------------------------------
// SmaRTClock.c
//-----------------------------------------------------------------------------
// Program Description:
//
// Driver for the SmaRTClock peripheral.
//
// Target:         EFM8SB1
// Tool chain:     Generic
// Command Line:   None
//
// Release 1.0 (BL)
//    - Initial Release
//    - 9 JAN 2015
//
// Release 1.0
//    - Initial Revision (FB)
//    - 19 MAY 2010
//

//-----------------------------------------------------------------------------
// Includes
//-----------------------------------------------------------------------------
#include "SI_EFM8SB1_Register_Enums.h"
#include "smartclock.h"                // RTC Functionality
#include "power.h"                     // Power Management Functionality

#if SMARTCLOCK_ENABLED

//-----------------------------------------------------------------------------
// Global Variables
//-----------------------------------------------------------------------------

// Variables used for the RTC interface
uint8_t RTC0CN0_Local;                       // Holds the desired RTC0CN0 settings
 
//-----------------------------------------------------------------------------
// Global CONSTANTS
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------
// Function PROTOTYPES
//-----------------------------------------------------------------------------

uint8_t   RTC_Read (uint8_t reg);
void RTC_Write (uint8_t reg, uint8_t value);
void RTC_WriteAlarm (uint32_t alarm);
uint32_t  RTC_GetCurrentTime(void);
void RTC_SetCurrentTime(uint32_t time);
void RTC0CN0_SetBits(uint8_t bits);
void RTC0CN0_ClearBits(uint8_t bits);
void RTC_SleepTicks(uint32_t ticks);

//-----------------------------------------------------------------------------
// RTC_Read ()
//-----------------------------------------------------------------------------
//
// Return Value : RTC0DAT
// Parameters   : 1) uint8_t reg - address of RTC register to read
//
// This function will read one byte from the specified RTC register.
// Using a register number greater that 0x0F is not permited.
//
//  Timing of SmaRTClock register read with short bit set
//
//     mov      RTC0ADR, #094h
//     nop
//     nop
//     nop
//     mov      A, RTC0DAT
//
//-----------------------------------------------------------------------------
uint8_t RTC_Read (uint8_t reg)
{

   RTC0ADR  = (0x90 | reg);            // pick register and set BUSY to 
                                       // initiate the read
                              
   NOP(); NOP(); NOP();                // delay 3 system clocks
   
   return RTC0DAT;                     // return value
}

//-----------------------------------------------------------------------------
// RTC_Write ()
//-----------------------------------------------------------------------------
//
// Return Value : none
// Parameters   : 1) uint8_t reg - address of RTC register to write
//                2) uint8_t value - the value to write to <reg>
//
// This function will Write one byte to the specified RTC register.
// Using a register number greater that 0x0F is not permited.
//
// Timing of SmaRTClock register write with short bit set
//
//       mov      RTC0ADR, #014h
//       mov      RTC0DAT, #088h
//       nop
//
//-----------------------------------------------------------------------------
void RTC_Write (uint8_t reg, uint8_t value)
{
   RTC0ADR  = (0x10 | reg);            // pick register
   RTC0DAT = value;                    // write data
}

//-----------------------------------------------------------------------------
// RTC_WriteAlarm ()
//-----------------------------------------------------------------------------
//
// Return Value : none
// Parameters   : 1) uint32_t alarm - the value to write to <ALARM>
//
// This function writes a value to the <ALRM> registers
//
// Instruction timing of multi-byte write with short bit set
//
//       mov      RTC0ADR, #010h
//       mov      RTC0DAT, #05h
//       nop
//       mov      RTC0DAT, #06h
//       nop
//       mov      RTC0DAT, #07h
//       nop
//       mov      RTC0DAT, #08h
//       nop
//
//-----------------------------------------------------------------------------

void RTC_WriteAlarm (uint32_t alarm)
{   
   SI_UU32_t alarm_value;

   alarm_value.u32 = alarm;   

   // Temporarily disable alarm while updating registers
   RTC0ADR = (0x10 | RTC0CN0);
   RTC0DAT = (RTC0CN0_Local & ~RTC0AEN);       
   NOP();
   
   // Write the value to the alarm registers
   RTC0ADR = (0x10 | ALARM0);       
   RTC0DAT = alarm_value.u8[B0];    // write data
   NOP();
   RTC0DAT = alarm_value.u8[B1];    // write data
   NOP();
   RTC0DAT = alarm_value.u8[B2];    // write data
   NOP(); 
   RTC0DAT = alarm_value.u8[B3];    // write data
   NOP(); 
   
   // Restore alarm state after registers have been written
   RTC0ADR = (0x10 | RTC0CN0);
   RTC0DAT = RTC0CN0_Local;  
}

//-----------------------------------------------------------------------------
// RTC_GetCurrentTime()
//-----------------------------------------------------------------------------
//
// Return Value : uint32_t value - the value of the SmaRTClock
// Parameters   : none
//
// This function reads the current value of the SmaRTClock
//
// Instruction timing of multi-byte read with short bit set
//
//       mov      RTC0ADR, #0d0h
//       nop
//       nop
//       nop
//       mov      A, RTC0DAT
//       nop
//       nop
//       mov      A, RTC0DAT
//       nop
//       nop
//       mov      A, RTC0DAT
//       nop
//       nop
//       mov      A, RTC0DAT
//
//-----------------------------------------------------------------------------
uint32_t RTC_GetCurrentTime(void)
{
   SI_UU32_t current_time;   

   RTC_Write( RTC0CN0, RTC0CN0_Local | RTC0CAP);   // Write '1' to RTC0CAP   
   while((RTC_Read(RTC0CN0) & RTC0CAP));          // Wait for RTC0CAP -> 0
   
   RTC0ADR = (0xD0 | CAPTURE0);
   NOP(); NOP(); NOP();
   current_time.u8[B0] = RTC0DAT;               // Least significant byte
   NOP(); NOP();
   current_time.u8[B1] = RTC0DAT;
   NOP(); NOP();
   current_time.u8[B2] = RTC0DAT;
   NOP(); NOP();
   current_time.u8[B3] = RTC0DAT;               // Most significant byte
   
   return current_time.u32;

}

//-----------------------------------------------------------------------------
// RTC_SetCurrentTime()
//-----------------------------------------------------------------------------
//
// Return Value : none 
// Parameters   : 
//
// This function sets the current value of the SmaRTClock
//
// Instruction timing of multi-byte write with short bit set
//
//       mov      RTC0ADR, #010h
//       mov      RTC0DAT, #05h
//       nop
//       mov      RTC0DAT, #06h
//       nop
//       mov      RTC0DAT, #07h
//       nop
//       mov      RTC0DAT, #08h
//       nop

//-----------------------------------------------------------------------------
void RTC_SetCurrentTime(uint32_t time)
{
   SI_UU32_t current_time;
   
   current_time.u32 = time;

   // Write the time to the capture registers
   RTC0ADR = (0x10 | CAPTURE0);       
   RTC0DAT = current_time.u8[B0];    // write data
   NOP();
   RTC0DAT = current_time.u8[B1];    // write data
   NOP();
   RTC0DAT = current_time.u8[B2];    // write data
   NOP(); 
   RTC0DAT = current_time.u8[B3];    // write data
   NOP(); 
 
   RTC_Write( RTC0CN0, RTC0CN0_Local | RTC0SET);   // Write '1' to RTC0SET   
   while((RTC_Read(RTC0CN0) & RTC0SET));          // Wait for RTC0SET -> 0

}

//-----------------------------------------------------------------------------
// RTC0CN0_SetBits()
//-----------------------------------------------------------------------------
//
// Return Value : none 
// Parameters   : 
//
// This function sets bits in the RTC0CN0 register
//-----------------------------------------------------------------------------
void RTC0CN0_SetBits(uint8_t bits)
{
   RTC0CN0_Local |= (bits & ~0x03);
   RTC_Write( RTC0CN0, RTC0CN0_Local | bits);
}

//-----------------------------------------------------------------------------
// RTC0CN0_ClearBits()
//-----------------------------------------------------------------------------
//
// Return Value : none 
// Parameters   : 
//
// This function clears bits in the RTC0CN0 register
//-----------------------------------------------------------------------------
void RTC0CN0_ClearBits(uint8_t bits)
{
   RTC0CN0_Local &= bits;
   RTC_Write( RTC0CN0, RTC0CN0_Local);
}    

/*
//-----------------------------------------------------------------------------
// RTC_SleepTicks
//-----------------------------------------------------------------------------
//
// Return Value : none
// Parameters   : 1) uint32_t the number of ticks to Sleep
//
// This function sleeps for the specified number of RTC ticks.  This function
// is commented out and has been replaced by a simplified version below which
// does not check for a "wrap" condition.  The simplified version of this
// function cannot be used asynchronously with another "sleep" process,
// however, this version may be used asynchronously at the expense of 
// increased code space and execution time.
// 
// Note: The minimum number of ticks is 3
//
// Note: This routine will exit if a pre-configured RTC Alarm occurs, or if
// another wake-up source occurs.
// 
//-----------------------------------------------------------------------------

void RTC_SleepTicks(uint32_t ticks)
{
   uint8_t EA_save;
   uint8_t PMU0CF_save;
   uint8_t RTC0CN0_save;

   SI_UU32_t current_time;
   SI_UU32_t current_alarm;
   SI_UU32_t alarm_value;
   
   uint8_t pending_alarm;
   uint8_t alarm_wrap;  
   
   // Disable Interrupts
   EA_save = IE_EA;
   IE_EA = 0;
   
   // Check for pending alarms
   pending_alarm = RTC_Alarm;

   // Initiate Capture of the current time 
   RTC_Write( RTC0CN0, RTC0CN0_Local | RTC0CAP);   // Write '1' to RTC0CAP   
   
   // Read the current alarm value
   RTC0ADR = (0xD0 | ALARM0);
   NOP(); NOP(); NOP();
   current_alarm.u8[B0] = RTC0DAT;               // Least significant byte
   NOP(); NOP();
   current_alarm.u8[B1] = RTC0DAT;
   NOP(); NOP();
   current_alarm.u8[B2] = RTC0DAT;
   NOP(); NOP();
   current_alarm.u8[B3] = RTC0DAT;               // Most significant byte
   
   // Copy the current time into <current_time>
   while((RTC_Read(RTC0CN0) & RTC0CAP));         // Wait for RTC0CAP -> 0
   RTC0ADR = (0xD0 | CAPTURE0);
   NOP(); NOP(); NOP();
   current_time.u8[B0] = RTC0DAT;               // Least significant byte
   NOP(); NOP();
   current_time.u8[B1] = RTC0DAT;
   NOP(); NOP();
   current_time.u8[B2] = RTC0DAT;
   NOP(); NOP();
   current_time.u8[B3] = RTC0DAT;               // Most significant byte

   // Preserve RTC0CN0
   RTC0CN0_save = RTC0CN0_Local;

   // Check for wrap if alarm is enabled
   if((RTC0CN0_Local & RTC0AEN) && (current_time.u32 + ticks > current_alarm.u32))
   {  
      alarm_value.u32 = current_alarm.u32;
      alarm_wrap = 1;

   } else
   {
      alarm_value.u32 = current_time.u32 + ticks;
      RTC0CN0_Local &= ~ALRM;
      alarm_wrap = 0;
   }
          
   // Write the alarm value
   RTC_WriteAlarm(alarm_value.u32);   

   // Force the RTC wake-up sources to be enabled
   PMU0CF_save = PMU0CF_Local;
   PMU0CF_Local |= RTC;
   
   // Place device in Sleep Mode
   LPM(SLEEP);

   // Restore Enabled Wake-up Sources and RTC state
   PMU0CF_Local = PMU0CF_save;
   RTC0CN0_Local = RTC0CN0_save;   

   // Clear Alarm Flag unless a wrap was detected or it was already
   // set upon entry into the function
   if(alarm_wrap == 0 && pending_alarm == 0)
   {
      RTC_Alarm = 0;
   }

   // Restore alarm value unless a wrap was detected
   if(alarm_wrap == 0)
   {
      RTC_WriteAlarm(current_alarm.u32);
   }  
   
   // Restore Interrupts
   IE_EA = EA_save;

}   

*/

//-----------------------------------------------------------------------------
// RTC_SleepTicks
//-----------------------------------------------------------------------------
//
// Return Value : none
// Parameters   : 1) uint32_t the number of ticks to Sleep
//
// This function sleeps for the specified number of RTC ticks.  Software should
// ensure that the device will wake up and restore the original RTC alarm 
// value before the next alarm occurs. 
// 
// Note: The minimum number of ticks is 3
//
// 
//-----------------------------------------------------------------------------

void RTC_SleepTicks(uint32_t ticks)
{
   uint8_t EA_save;
   uint8_t PMU0CF_save;
   uint8_t RTC0CN0_save;

   SI_UU32_t current_time;
   
   // Disable Interrupts
   EA_save = IE_EA;
   IE_EA = 0;

   // Preserve RTC0CN0
   RTC0CN0_save = RTC0CN0_Local;
   
   // Disable Auto Reset
   RTC0CN0_ClearBits(~ALRM);

   // Obtain the current time
   current_time.u32 = RTC_GetCurrentTime();
              
   // Write the alarm value
   RTC_WriteAlarm(current_time.u32 + ticks);   

   // Force the RTC wake-up sources to be enabled
   PMU0CF_save = PMU0CF_Local;
   PMU0CF_Local |= RTC;
   
   // Place device in Sleep Mode
   LPM(SLEEP);

   // Restore Enabled Wake-up Sources and RTC state
   PMU0CF_Local = PMU0CF_save;
   RTC0CN0_Local = RTC0CN0_save;   

   // Clear Alarm Flag
   RTC_Alarm = 0;

   // Restore alarm value unless a wrap was detected
   RTC_WriteAlarm(WAKE_INTERVAL_TICKS);
   
   // Restore Interrupts
   IE_EA = EA_save;

}

#endif       

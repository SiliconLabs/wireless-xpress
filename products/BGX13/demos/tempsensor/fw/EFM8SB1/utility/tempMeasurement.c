#include "efm8_config.h"
#include "SI_EFM8SB1_Defs.h"
#include "stdio.h"

#define SCALE                    1000L // Scale for temp calculations
#define OVER_ROUND                  10 // Number of shifts (>>N) to reach the
                                       // correct number of bits of precision

#define SLOPE     		          1930 // Slope of the temp transfer function
#define OFFSET		           276471L // Offset for the temp transfer function

#define COMP_ADDRESS            0x1E00 // Location of compensation

// Flash stored compensation
SI_LOCATED_VARIABLE_NO_INIT(COMPENSATION, uint32_t, const SI_SEG_CODE, COMP_ADDRESS);

bool NEW_CAL;                           // Flag - new calibration value available
int16_t CAL_VAL;                        // Calibration value

int32_t TempWhole, TempFrac;
void updateTemp(void);
void Calibrate(int16_t cal_input, int32_t temp);

void calibrateWith (int16_t new_cal_value)
{
  CAL_VAL = new_cal_value;
  NEW_CAL = true;
}
uint8_t* getTempString(uint8_t* buffer)
{
  updateTemp();
  sprintf(buffer, "%ld.%ld\r",TempWhole,TempFrac);
  return buffer;
}

// This program uses the following equation from the datasheet:
//
//    Vtemp = 3.4T(C) + 940mV
//
// Moving T(C) to the left-hand side:
//
//    T(C) = (1 / 3.4)Vtemp - (940 mV / 3.4)
//
// In this instance, the equivalent linear equation is given by:
//
//    T(C) = 0.294*Vtemp - 276.471 (V in millivolts)
//
// Converting from V to an ADC reading:
//
//    T(C) = (0.294*VREF*ADC) - 276.471;
//
// Assuming a VREF = 1680 mV:
//
//    T(C) = 1.930*ADC_sum - 276.471;
void updateTemp(void)
{
	int32_t temp_scaled;                    // Scaled temperature value
	int32_t temp_whole;                     // Stores integer portion for output
	int32_t temp_comp;                      // Scaled and compensated temp
	int16_t temp_frac = 0;                  // Stores fractional portion of temp
    uint16_t counter;

	SI_VARIABLE_SEGMENT_POINTER(pCompensation, uint32_t, SI_SEG_CODE) = (SI_VARIABLE_SEGMENT_POINTER(, uint32_t, SI_SEG_CODE))COMP_ADDRESS;

	temp_scaled = 0;
	for(counter = 0; counter <256; counter++)
	{
		// start a conversion
		ADC0CN0_ADINT = 0;
		ADC0CN0_ADBUSY = 1;
		while(ADC0CN0_ADINT == 0);
		temp_scaled += (int32_t)ADC0>>6;
	}

    temp_scaled *= SLOPE;

    // With a left-justified ADC, we have to shift the decimal place
    // of temp_scaled to the right so we can match the format of
    // OFFSET. Once the formats are matched, we can subtract OFFSET.
    temp_scaled = temp_scaled >> OVER_ROUND;

    temp_scaled -= OFFSET;


    temp_comp = temp_scaled - *pCompensation; //Apply compensation
    //temp_comp = temp_scaled; //Apply compensation

    // Take the whole number portion unscaled
    temp_whole = temp_comp/SCALE;

    // The temp_frac value is the unscaled decimal portion of
    // temperature
    temp_frac = (int16_t)((temp_comp - temp_whole * SCALE) / (SCALE/10));

    // If the temperature is negative, remove the negative from
    // temp_frac portion for output.
    if(temp_frac < 0)
    {
       temp_frac *= -1;
    }
    TempWhole = temp_whole;
    TempFrac = temp_frac;
    // RETARGET_PRINTF("\r  T = %ld.%d(C)\n   ",temp_whole,temp_frac);

    if (NEW_CAL == true)                  // If new calibration data
    {
       Calibrate(CAL_VAL, temp_scaled); // Rewrite Calibration value
       NEW_CAL = false;           // Reset flag
    }
 }

//-----------------------------------------------------------------------------
// Calibrate
//-----------------------------------------------------------------------------
//
// Return Value : None
// Parameters   : cal - the value used as reference temperature
//                temp - the temperature measured at the moment
//
// This function rewrites the value of the compensation temperature, the
// difference between the reference temperature and the one being measured at
// that moment. This difference is saved in flash to be added to further
// measurements.
//
// This function erases the code page where the compensation variable is
// stored. This is done to enable the variable to be correctly modified. The
// content of the page is not saved because this is the only info stored in
// this page and there is no code in this page. A program that has code and/or
// data in this page must first save the content of the page before erasing it.
//
//-----------------------------------------------------------------------------
void Calibrate(int16_t cal_input, int32_t temp)
{
   bool EA_state = IE_EA;              // Preserves IE_EA state
   SI_VARIABLE_SEGMENT_POINTER(codePtr, uint8_t, SI_SEG_XDATA); // Used to write calibration
                                       // Value into flash memory
   SI_UU32_t COMP_VALUE;               // Byte-addressable S32 variable

   // Point to the compensation register that will contain the temperature
   // offset to be used with the temperature sensor
   codePtr = (SI_VARIABLE_SEGMENT_POINTER(, uint8_t, SI_SEG_XDATA))&COMPENSATION;

   COMP_VALUE.s32 = (int32_t) cal_input;
   COMP_VALUE.s32 *= 100;
   COMP_VALUE.s32 = temp - COMP_VALUE.s32;

   IE_EA = 0;                             // Disable interrupts

   VDM0CN = 0x80;                      // Enable VDD monitor

   RSTSRC = 0x02;                      // Enable VDD monitor as a reset source

   FLKEY  = 0xA5;                      // Key Sequence 1
   FLKEY  = 0xF1;                      // Key Sequence 2
   PSCTL |= 0x03;                      // PSWE = 1; PSEE = 1

   VDM0CN = 0x80;                      // Enable VDD monitor

   RSTSRC = 0x02;                      // Enable VDD monitor as a reset source
   *codePtr = 0;                        // Initiate page erase

   PSCTL &= ~0x03;                     // PSWE = 0; PSEE = 0

   	// Write first byte of compensation
	FLKEY  = 0xA5;                      // Key Sequence 1
	FLKEY  = 0xF1;                      // Key Sequence 2
	PSCTL |= 0x01;                      // PSWE = 1 which enables writes

	VDM0CN = 0x80;                      // Enable VDD monitor

	RSTSRC = 0x02;                      // Enable VDD monitor as a reset source

   // Write high byte of compensation
   *(codePtr++) = (uint8_t)(COMP_VALUE.u8[0]);

	PSCTL &= ~0x01;                     // PSWE = 0 which disable writes

	// Write second byte of compensation
	FLKEY  = 0xA5;                      // Key Sequence 1
	FLKEY  = 0xF1;                      // Key Sequence 2
	PSCTL |= 0x01;                      // PSWE = 1 which enables writes

	VDM0CN = 0x80;                      // Enable VDD monitor

	RSTSRC = 0x02;                      // Enable VDD monitor as a reset source

   // Write 2nd byte of compensation
   *(codePtr++) = (uint8_t)(COMP_VALUE.u8[1]);

	PSCTL &= ~0x01;                     // PSWE = 0 which disable writes

	// Write third byte of compensation
	FLKEY  = 0xA5;                      // Key Sequence 1
	FLKEY  = 0xF1;                      // Key Sequence 2
	PSCTL |= 0x01;                      // PSWE = 1 which enables writes

	VDM0CN = 0x80;                      // Enable VDD monitor

	RSTSRC = 0x02;                      // Enable VDD monitor as a reset source

   // Write 3rd byte of compensation
   *(codePtr++) = (uint8_t)(COMP_VALUE.u8[2]);

	PSCTL &= ~0x01;                     // PSWE = 0 which disable writes

	// Write fourth byte of compensation
	FLKEY  = 0xA5;                      // Key Sequence 1
	FLKEY  = 0xF1;                      // Key Sequence 2
	PSCTL |= 0x01;                      // PSWE = 1 which enables writes

	VDM0CN = 0x80;                      // Enable VDD monitor

	RSTSRC = 0x02;                      // Enable VDD monitor as a reset source

   // Write last byte of compensation
   *codePtr = (uint8_t)(COMP_VALUE.u8[3]);

	PSCTL &= ~0x01;                     // PSWE = 0 which disable writes

    IE_EA = EA_state;                   // Restore interrupt state
}

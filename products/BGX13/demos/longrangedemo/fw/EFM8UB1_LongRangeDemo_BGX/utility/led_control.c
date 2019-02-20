#include "efm8_config.h"
#include "SI_EFM8UB1_Defs.h"
#include "led_control.h"
SI_SBIT(RGB_PIN_1, SFR_P1, 4);                  // P1.4 pin to RGB
SI_SBIT(RGB_PIN_2, SFR_P1, 6);                  // P1.6 pin to RGB

#define LED_ON_STATE 0
#define LED_OFF_STATE 1

uint8_t RGB_LED_1_state[3] =
{
 LED_OFF_STATE,
 LED_ON_STATE,
 LED_OFF_STATE
};
uint8_t RGB_LED_2_state[3] =
{
 LED_OFF_STATE,
 LED_OFF_STATE,
 LED_ON_STATE
};

uint8_t LED_local_state;
uint8_t LED_last_color;

void LED_changeState(uint8_t state)
{/*
 if(LED_local_state != LED_OFF)
 {
 LED_last_color = LED_local_state;
 }
 */
  if ((state == LED_OFF) && (LED_local_state != LED_OFF))
  {
    LED_last_color = LED_local_state;
  }
  RGB_PIN_1 = RGB_LED_1_state[state];
  RGB_PIN_2 = RGB_LED_2_state[state];
  LED_local_state = state;
}
void LED_init(void)
{
  LED_changeState(LED_OFF);
  LED_last_color = LED_RED;
}

void LED_changeColor(uint8_t led_state)
{
  // Don't adjust color to off
  LED_changeState(led_state);
}

void LED_turnOn(void)
{
  LED_changeState(LED_last_color);
}
void LED_turnOff(void)
{
  LED_changeState(LED_OFF);
}

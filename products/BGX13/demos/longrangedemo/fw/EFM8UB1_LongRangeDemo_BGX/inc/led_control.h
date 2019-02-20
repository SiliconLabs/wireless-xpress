#ifndef _LED_CONTROL_H
#define _LED_CONTROL_H

enum {
  LED_OFF = 0,
  LED_GREEN = 1,
  LED_RED = 2
};

void LED_turnOn(void);
void LED_turnOff(void);
void LED_changeColor(uint8_t);
void LED_init(void);

#endif /* _LED_CONTROL_H */

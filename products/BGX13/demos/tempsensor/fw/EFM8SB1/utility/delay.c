/*
 * delay.c
 *
 *  Created on: Jul 14, 2018
 *      Author: padorris
 */

#include "efm8_config.h"
#include "SI_EFM8UB1_Defs.h"

uint16_t delayDuration = 0;

bool delayIsFinished(void)
{
  if(delayDuration)
  {
    return false;
  }
  else
  {
    return true;
  }
}
void delayFor(uint16_t duration)
{
  delayDuration = duration;
}
void delayAndWaitFor(uint16_t duration)
{
  delayFor(duration);
  while(delayDuration);
}

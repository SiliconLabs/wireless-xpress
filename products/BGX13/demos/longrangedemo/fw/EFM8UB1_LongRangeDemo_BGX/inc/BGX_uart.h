#ifndef BGX_UART_H_
#define BGX_UART_H_

#include <compiler_defs.h>

extern SI_SEGMENT_VARIABLE(BGX_transmitBuffer[], uint8_t, SI_SEG_XDATA);
extern SI_SEGMENT_VARIABLE(BGX_receiveBuffer[], uint8_t, SI_SEG_XDATA);

uint8_t BGX_Write(const char*);
uint8_t BGX_getResponse(void);
uint8_t BGX_didReceiveLine(const char *buff);

void listenerOn(void);
void listenerOff(void);
void listenerReset(void);
bool listenerReceivedLineEnd(void);
bool listenerReceivedEntireResponse(void);
bool listenerReceivedEntireResponse(void);

#endif /* BGX_UART_H_ */

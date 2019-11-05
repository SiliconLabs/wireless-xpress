#ifndef BGX_UART_H_
#define BGX_UART_H_
#include <si_toolchain.h>
uint8_t BGX_Write(const char*, bool expectResponse);
uint8_t BGX_getResponse(void);
void listenerOn(void);
void listenerOff(void);
void listenerReset(void);
void BGX_reset(int baudrate);
void BGX_setBaudRate(void);
void BGX_sendBreakoutSequence(void);
bool listenerFoundLineEnd(void);
uint8_t BGX_didReceiveLine(const char *buff);
extern SI_SEGMENT_VARIABLE(BGX_transmitBuffer[30], uint8_t, SI_SEG_XDATA);
extern SI_SEGMENT_VARIABLE(BGX_receiveBuffer[90], uint8_t, SI_SEG_XDATA);
#endif /* BGX_UART_H_ */

#ifndef __EFM8_CONFIG_H__
#define __EFM8_CONFIG_H__

// SPI configurations
#define EFM8PDL_SPI0_USE          1
#define EFM8PDL_SPI0_USE_BUFFER   1
#define EFM8PDL_SPI0_USE_FIFO     0
#define EFM8PDL_SPI0_TX_SEGTYPE   SI_SEG_PDATA

// Select Power Mode driver options
#define EFM8PDL_PWR_USE_STOP      1

// UART1 configurations to use buffering
#define EFM8PDL_UART1_USE_STDIO   0
#define EFM8PDL_UART1_USE_BUFFER  1

// The two modes the STK Buttons could be in
#define COMMAND_MODE 1
#define STREAM_MODE  0

// The two modes the connection status could be in
#define CONNECTED    1
#define DISCONNECTED 0

// BGX pins
#define BGX_RESET_PIN  P1_B5
#define MODE_PIN       P0_B0
#define CONNECTION_PIN P1_B3

#endif // __EFM8_CONFIG_H__

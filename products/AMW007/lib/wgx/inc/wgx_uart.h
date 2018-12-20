#ifndef __WGX_UART_H__
#define __WGX_UART_H__

/////////////////////////////////////////////////////////////////////////////
// Includes
/////////////////////////////////////////////////////////////////////////////
#include "wgx_config.h"
#include "efm8_config.h"

#if WGX_EFM8_UART == 0

#include "uart_0.h"
#define UART_init 					UART0_init
#define UART_writeBuffer			UART0_writeBuffer
#define UART_readBuffer				UART0_readBuffer
#define UART_txBytesRemaining		UART0_txBytesRemaining
#define UART_rxBytesRemaining		UART0_rxBytesRemaining
#define UART_enableRxFifoInt		UART0_enableRxFifoInt
#define UART_enableTxFifoInt		UART0_enableTxFifoInt
#define UART_getRxFifoCount			UART0_getRxFifoCount
#define UART_getTxFifoCount			UART0_getTxFifoCount
#define UART_read 					UART0_read
#define UART_write 					UART0_write
#define UART_initTxFifo				UART0_initTxFifo
#define UART_initRxFifo				UART0_initRxFifo
#define UART_receiveCompleteCb		UART0_receiveCompleteCb
#define UART_transmitCompleteCb		UART0_transmitCompleteCb

#define UART_DATALEN_8				UART0_DATALEN_8
#define UART_STOPLEN_SHORT 			UART0_STOPLEN_SHORT
#define UART_FEATURE_DISABLE		UART0_FEATURE_DISABLE
#define UART_RX_ENABLE				UART0_RX_ENABLE
#define UART_MULTIPROC_DISABLE		UART0_MULTIPROC_DISABLE
#define UART_TXTHRSH_ZERO			UART0_TXTHRSH_ZERO
#define UART_TXFIFOINT_DISABLE		UART0_TXFIFOINT_DISABLE
#define UART_RXTHRSH_ZERO 			UART0_RXTHRSH_ZERO
#define UART_RXTIMEOUT_DISABLE		UART0_RXTIMEOUT_DISABLE
#define UART_RXFIFOINT_DISABLE		UART0_RXFIFOINT_DISABLE

#else

#include "uart_1.h"
#define UART_init 					UART1_init
#define UART_writeBuffer			UART1_writeBuffer
#define UART_readBuffer				UART1_readBuffer
#define UART_txBytesRemaining		UART1_txBytesRemaining
#define UART_rxBytesRemaining		UART1_rxBytesRemaining
#ifdef EFM8UB2
	#define UART_enableRxFifoInt(a)		
	#define UART_enableTxFifoInt(a)		
	#define UART_getRxFifoCount 	UART1_rxBytesRemaining	
	#define UART_getTxFifoCount 	UART1_txBytesRemaining	
	#define UART_initTxFifo(a, b)
	#define UART_initRxFifo(a, b, c)		
#else
	#define UART_enableRxFifoInt		UART1_enableRxFifoInt
	#define UART_enableTxFifoInt		UART1_enableTxFifoInt
	#define UART_getRxFifoCount			UART1_getRxFifoCount
	#define UART_getTxFifoCount			UART1_getTxFifoCount
	#define UART_initTxFifo				UART1_initTxFifo
	#define UART_initRxFifo				UART1_initRxFifo
#endif
#define UART_read 					UART1_read
#define UART_write 					UART1_write
#define UART_receiveCompleteCb		UART1_receiveCompleteCb
#define UART_transmitCompleteCb		UART1_transmitCompleteCb

#define UART_DATALEN_8				UART1_DATALEN_8
#define UART_STOPLEN_SHORT 			UART1_STOPLEN_SHORT
#define UART_FEATURE_DISABLE		UART1_FEATURE_DISABLE
#define UART_RX_ENABLE				UART1_RX_ENABLE
#define UART_MULTIPROC_DISABLE		UART1_MULTIPROC_DISABLE
#define UART_TXTHRSH_ZERO			UART1_TXTHRSH_ZERO
#define UART_TXFIFOINT_DISABLE		UART1_TXFIFOINT_DISABLE
#define UART_RXTHRSH_ZERO 			UART1_RXTHRSH_ZERO
#define UART_RXTIMEOUT_DISABLE		UART1_RXTIMEOUT_DISABLE
#define UART_RXFIFOINT_DISABLE		UART1_RXFIFOINT_DISABLE

#endif

#endif

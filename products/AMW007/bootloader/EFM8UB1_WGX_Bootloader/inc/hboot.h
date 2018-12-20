#ifndef __HBOOT_H__
#define __HBOOT_H__

#define HBOOT_CMD_START "hboot "

/**************************************************************************//**
 * Wait for host connection
 *****************************************************************************/
void hboot_pollForHost(void);

/**************************************************************************//**
 * Request bootload file from host
 *****************************************************************************/
void hboot_requestBootload(void);

#endif

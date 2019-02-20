/*****************************************************************************/
/* scanning.h                                                                */
/*****************************************************************************/

#ifndef SCANNING_H_
#define SCANNING_H_

/*****************************************************************************/
/* Function Prototypes                                                       */
/*****************************************************************************/

// Drawing functions
void Scanning_drawScanningScreen(void);
void Scanning_drawNoScanResultsScreen(void);
void Scanning_drawScanResultsScreen(void);
void Scanning_drawConnectedScreen(void);
void Scanning_drawInventoryInfo(uint8_t numCans);

// TODO
uint8_t Scanning_selectDevice(void);

// TODO
void Scanning_main(void);

#endif /* SCANNING_H_ */

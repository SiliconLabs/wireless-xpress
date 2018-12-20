#ifndef __CRITICAL_SECTION_H__
#define __CRITICAL_SECTION_H__


// declare variable needed for interrupt enable enter/exit
#define DECL_CRITICAL uint8_t savedEA

// enter critical section
#define ENTER_CRITICAL  do                                                    \
                        {                                                     \
                          savedEA = IE_EA;  /* save current EA */   		  \
                          IE_EA = 0;        /* disable interrupts */          \
                        } while(0)
// exit autopage section
#define EXIT_CRITICAL   do                                                    \
                        {                                                     \
                          IE_EA = savedEA;  /* restore saved EA */  		  \
                        } while(0)

#endif

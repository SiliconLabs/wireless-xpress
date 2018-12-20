$NOMOD51
;
;

#include "hboot_config.h"
#include "efm8_device.h"

#define BL_SIGNATURE 0xA5

    NAME    BOOT_STARTUP

    PUBLIC  boot_otp
    PUBLIC  _flash_readByte
    PUBLIC  ?C_STARTUP
    EXTRN   CODE (?C_START)

; Declare and locate all memory segments used by the bootloader
?BL_EXTRA   SEGMENT CODE AT BL_LIMIT_ADDRESS
?BL_START   SEGMENT CODE AT BL_START_ADDRESS
?BL_RSVD    SEGMENT CODE AT BL_LOCK_ADDRESS-2
?BL_STACK   SEGMENT IDATA

; Create idata segment for stack
    RSEG    ?BL_STACK
    DS      16

; Create code segment for firmware that doesn't fit in security page
    RSEG    ?BL_EXTRA
boot_extra:
    LJMP    ?C_STARTUP

; uint8_t flash_readByte(uint16_t addr);
; C callable function reads one byte from flash. Instruction that reads flash must 
; be located in the security page to work with locked flash. The entire function did 
; not fit in the security page, so it is split into 2 pieces.
_flash_readByte:
    MOV     DPL,R7
    MOV     DPH,R6
    LJMP    read_byte

; Bootloader entry point (boot_vector)
    RSEG    ?BL_START
?C_STARTUP:
    USING   0

    ; Start bootloader if reset vector is not programmed
    MOV     DPTR,#00H
    CLR     A
    MOVC    A,@A+DPTR
    CPL     A
    JZ      boot_start

    ; Start bootloader if software reset and R0 == signature
    MOV     A,RSTSRC
    CJNE    A,#010H,pin_test
    MOV     A,R0
    XRL     A,#BL_SIGNATURE
    JZ      boot_start

; Start the application by jumping to the reset vector
app_start:
    LJMP    00H

#ifdef LOAD_DEFAULT_ON_STARTUP
; Start bootloader if POR|Pin reset
pin_test:
    ANL     A,#03H                  ; A = RSTSRC
    JZ      app_start               ; POR or PINR only
#else
; Start bootloader if POR|Pin reset and boot pin held low
pin_test:
    ANL     A,#03H                  ; A = RSTSRC
    JZ      app_start               ; POR or PINR only
    MOV     R0,#(BL_PIN_LOW_CYCLES / 7)
?C0001:                             ; deglitch loop
    JB      BL_START_PIN,app_start  ; +3
    DJNZ    R0,?C0001               ; +4 = 7 cycles per loop
#endif

; Setup the stack and jump to the bootloader
boot_start:
    MOV     SP, #?BL_STACK-1
    LJMP    ?C_START

; Second piece of the function _flash_readByte, which must be located in the 
; security page. Reads and returns the CODE byte pointed to by DPTR.
read_byte:
    CLR     A
    MOVC    A,@A+DPTR
    MOV     R7,A
    RET

; Reserved Bytes (bl_revision, bl_signature, lock_byte)
    RSEG    ?BL_RSVD
boot_rev:
    DB      BL_REVISION
boot_otp:
    DB      BL_SIGNATURE
lock_byte:
    DB      0xFF

    END

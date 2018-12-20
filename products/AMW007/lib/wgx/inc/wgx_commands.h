#ifndef __WGX_COMMANDS_H__
#define __WGX_COMMANDS_H__

#include <stdint.h>
#include "bsp.h"

extern SI_SEGMENT_VARIABLE(cmd_set[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_get[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_true[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_false[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_start[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stop[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_machine_mode[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_buffered[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_auto_close[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_ver[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_file_open[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_file_create[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_ls[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_scan[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_reboot[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_save[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_tcpc[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_tcpcl[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_tcps[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_tlsc[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_tlscl[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_tlss[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_udpc[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_udps[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_webc[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_stream_close_all[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_stream_close[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_write[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_read[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_list[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_stream_poll[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_network_up[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_network_wlan[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_network_softap[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_network_down[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_wlan_ssid[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_wlan_pass[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_wlan_status[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_wlan_ip[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_softap_ip[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_softap_ssid[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_softap_pass[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_softap_info[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_clients[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_rssi[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_http_get[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_http_post[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_wakeup_timeout[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_sleep[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_ota[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_setup_web[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_setup_status[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_setup_ssid[], char, SI_SEG_CODE);
extern SI_SEGMENT_VARIABLE(cmd_setup_pass[], char, SI_SEG_CODE);

extern SI_SEGMENT_VARIABLE(cmd_enable_dns[], char, SI_SEG_CODE);

#endif



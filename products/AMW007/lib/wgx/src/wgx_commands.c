#include "wgx_commands.h"
#include "wgx_config.h"

SI_SEGMENT_VARIABLE(cmd_set[], char, SI_SEG_CODE)             	= "set ";
SI_SEGMENT_VARIABLE(cmd_get[], char, SI_SEG_CODE)             	= "get ";

SI_SEGMENT_VARIABLE(cmd_true[], char, SI_SEG_CODE)             	= " 1";
SI_SEGMENT_VARIABLE(cmd_false[], char, SI_SEG_CODE)             = " 0";
SI_SEGMENT_VARIABLE(cmd_start[], char, SI_SEG_CODE)             = " start";
SI_SEGMENT_VARIABLE(cmd_stop[], char, SI_SEG_CODE)              = " stop";

SI_SEGMENT_VARIABLE(cmd_machine_mode[], char, SI_SEG_CODE)     	= "sy c f machine";
SI_SEGMENT_VARIABLE(cmd_stream_buffered[], char, SI_SEG_CODE)	= "sy c b";
SI_SEGMENT_VARIABLE(cmd_stream_auto_close[], char, SI_SEG_CODE)	= "st a";
SI_SEGMENT_VARIABLE(cmd_ver[], char, SI_SEG_CODE)              	= "ver";
SI_SEGMENT_VARIABLE(cmd_file_open[], char, SI_SEG_CODE)			= "fop";
SI_SEGMENT_VARIABLE(cmd_file_create[], char, SI_SEG_CODE)		= "fcr -o";
SI_SEGMENT_VARIABLE(cmd_ls[], char, SI_SEG_CODE)               	= "ls";
SI_SEGMENT_VARIABLE(cmd_scan[], char, SI_SEG_CODE)             	= "scan";
SI_SEGMENT_VARIABLE(cmd_reboot[], char, SI_SEG_CODE)           	= "reboot";
SI_SEGMENT_VARIABLE(cmd_save[], char, SI_SEG_CODE)           	= "save";

SI_SEGMENT_VARIABLE(cmd_tcpc[], char, SI_SEG_CODE)           	= "tcpc ";
SI_SEGMENT_VARIABLE(cmd_tcpcl[], char, SI_SEG_CODE)           	= "tcpc -l ";
SI_SEGMENT_VARIABLE(cmd_tcps[], char, SI_SEG_CODE)           	= "tcps ";
SI_SEGMENT_VARIABLE(cmd_tlsc[], char, SI_SEG_CODE)           	= "tlsc ";
SI_SEGMENT_VARIABLE(cmd_tlscl[], char, SI_SEG_CODE)           	= "tlsc -l ";
SI_SEGMENT_VARIABLE(cmd_udpc[], char, SI_SEG_CODE)           	= "udpc ";
SI_SEGMENT_VARIABLE(cmd_udps[], char, SI_SEG_CODE)           	= "udps ";
SI_SEGMENT_VARIABLE(cmd_webc[], char, SI_SEG_CODE)           	= "webc ";

SI_SEGMENT_VARIABLE(cmd_stream_close_all[], char, SI_SEG_CODE)  = "close all";

SI_SEGMENT_VARIABLE(cmd_stream_close[], char, SI_SEG_CODE)  	= "close ";
SI_SEGMENT_VARIABLE(cmd_stream_write[], char, SI_SEG_CODE)  	= "write ";
SI_SEGMENT_VARIABLE(cmd_stream_read[], char, SI_SEG_CODE)  		= "read ";
SI_SEGMENT_VARIABLE(cmd_stream_list[], char, SI_SEG_CODE)  		= "list";
SI_SEGMENT_VARIABLE(cmd_stream_poll[], char, SI_SEG_CODE)  		= "poll";

SI_SEGMENT_VARIABLE(cmd_network_up[], char, SI_SEG_CODE)  		= "nup";
SI_SEGMENT_VARIABLE(cmd_network_wlan[], char, SI_SEG_CODE)  	= " -i wlan ";
SI_SEGMENT_VARIABLE(cmd_network_softap[], char, SI_SEG_CODE)  	= " -i softap ";
SI_SEGMENT_VARIABLE(cmd_network_down[], char, SI_SEG_CODE)  	= "ndo";
SI_SEGMENT_VARIABLE(cmd_wlan_ssid[], char, SI_SEG_CODE)  		= "wl s ";
SI_SEGMENT_VARIABLE(cmd_wlan_pass[], char, SI_SEG_CODE)  		= "wl p ";
SI_SEGMENT_VARIABLE(cmd_wlan_status[], char, SI_SEG_CODE)  		= "wl n s";
SI_SEGMENT_VARIABLE(cmd_wlan_ip[], char, SI_SEG_CODE)  			= "wl n i";
SI_SEGMENT_VARIABLE(cmd_softap_ip[], char, SI_SEG_CODE)  		= "so s i";
SI_SEGMENT_VARIABLE(cmd_softap_ssid[], char, SI_SEG_CODE)  		= "so s ";
SI_SEGMENT_VARIABLE(cmd_softap_pass[], char, SI_SEG_CODE)  		= "so p ";
SI_SEGMENT_VARIABLE(cmd_softap_info[], char, SI_SEG_CODE)  		= "so o";
SI_SEGMENT_VARIABLE(cmd_clients[], char, SI_SEG_CODE)  			= "clients";
SI_SEGMENT_VARIABLE(cmd_rssi[], char, SI_SEG_CODE)  			= "rssi";

SI_SEGMENT_VARIABLE(cmd_http_get[], char, SI_SEG_CODE)          = "hge ";
SI_SEGMENT_VARIABLE(cmd_http_post[], char, SI_SEG_CODE)         = "hpo ";

SI_SEGMENT_VARIABLE(cmd_wakeup_timeout[], char, SI_SEG_CODE)    = "sy w t ";
SI_SEGMENT_VARIABLE(cmd_sleep[], char, SI_SEG_CODE)   			= "sleep";

SI_SEGMENT_VARIABLE(cmd_ota[], char, SI_SEG_CODE)   			= "ota";

SI_SEGMENT_VARIABLE(cmd_setup_web[], char, SI_SEG_CODE)   		= "setup web";
SI_SEGMENT_VARIABLE(cmd_setup_status[], char, SI_SEG_CODE)   	= "setup status";
SI_SEGMENT_VARIABLE(cmd_setup_ssid[], char, SI_SEG_CODE)   		= "se w s ";
SI_SEGMENT_VARIABLE(cmd_setup_pass[], char, SI_SEG_CODE)   		= "se w p ";


SI_SEGMENT_VARIABLE(cmd_enable_dns[], char, SI_SEG_CODE)     	= "softap.dns_server.enabled ";



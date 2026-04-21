#ifndef MINDWEAVE_BRIDGE_H
#define MINDWEAVE_BRIDGE_H

#ifdef __cplusplus
extern "C" {
#endif

char* mindweave_bridge_bootstrap(const char* request_json);
char* mindweave_bridge_get_snapshot(const char* request_json);
char* mindweave_bridge_capture_diary(const char* request_json);
char* mindweave_bridge_capture_schedule(const char* request_json);
char* mindweave_bridge_send_chat_message(const char* request_json);
char* mindweave_bridge_run_sync(void);
char* mindweave_bridge_save_preferences(const char* request_json);
char* mindweave_bridge_authenticate(const char* request_json);
char* mindweave_bridge_force_reset_credentials(const char* request_json);
char* mindweave_bridge_change_credentials(const char* request_json);
void mindweave_bridge_dispose_string(char* value);

#ifdef __cplusplus
}
#endif

#endif

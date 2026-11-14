#include "quiet.h"

#include <time.h>
#include "nvs.h"

/* Quiet hours suppress the buzzer and the chime only. Default 22:00-07:00 UTC. */

static uint8_t s_start = 22;
static uint8_t s_end = 7;

void quiet_init(void)
{
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READONLY, &nvs) == ESP_OK) {
        nvs_get_u8(nvs, "q_start", &s_start);
        nvs_get_u8(nvs, "q_end", &s_end);
        nvs_close(nvs);
    }
}

void quiet_set_window(uint8_t start_hour, uint8_t end_hour)
{
    s_start = start_hour % 24;
    s_end = end_hour % 24;
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READWRITE, &nvs) == ESP_OK) {
        nvs_set_u8(nvs, "q_start", s_start);
        nvs_set_u8(nvs, "q_end", s_end);
        nvs_commit(nvs);
        nvs_close(nvs);
    }
}

bool quiet_hours_active(void)
{
    time_t now = time(NULL);
    if (now < 1700000000) {
        return false; /* clock not set; do not guess */
    }
    struct tm t;
    gmtime_r(&now, &t);
    int hour = t.tm_hour;
    if (s_start == s_end) {
        return false;
    }
    if (s_start < s_end) {
        return hour >= s_start && hour < s_end;
    }
    return hour >= s_start || hour < s_end;
}

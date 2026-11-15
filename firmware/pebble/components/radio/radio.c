#include "radio.h"

#include <string.h>
#include "nvs.h"
#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_log.h"
#include "esp_sntp.h"

/* Up to five saved networks. The station joins the strongest saved match. */

static const char *TAG = "radio";

#define WIFI_MAX RADIO_WIFI_MAX

static radio_network_t s_creds[WIFI_MAX];
static int s_count;
static bool s_connected;
static bool s_started;
static bool s_sntp;
static int s_retries;

static void persist(void)
{
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READWRITE, &nvs) != ESP_OK) {
        return;
    }
    nvs_set_blob(nvs, "wifi", s_creds, sizeof(s_creds));
    nvs_set_u8(nvs, "wifi_n", (uint8_t)s_count);
    nvs_commit(nvs);
    nvs_close(nvs);
}

static void on_event(void *arg, esp_event_base_t base, int32_t id, void *data)
{
    (void)arg;
    (void)data;
    if (base == IP_EVENT && id == IP_EVENT_STA_GOT_IP) {
        s_connected = true;
        s_retries = 0;
        if (!s_sntp) {
            esp_sntp_setoperatingmode(SNTP_OPMODE_POLL);
            esp_sntp_setservername(0, "pool.ntp.org");
            esp_sntp_init();
            s_sntp = true;
        }
        ESP_LOGI(TAG, "Wi-Fi up");
    } else if (base == WIFI_EVENT && id == WIFI_EVENT_STA_DISCONNECTED) {
        s_connected = false;
        if (s_retries < 5) {
            s_retries++;
            esp_wifi_connect();
        }
    }
}

esp_err_t radio_init(void)
{
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READONLY, &nvs) == ESP_OK) {
        uint8_t n = 0;
        nvs_get_u8(nvs, "wifi_n", &n);
        size_t len = sizeof(s_creds);
        if (nvs_get_blob(nvs, "wifi", s_creds, &len) == ESP_OK) {
            s_count = n > WIFI_MAX ? WIFI_MAX : n;
        }
        nvs_close(nvs);
    }

    ESP_ERROR_CHECK(esp_netif_init());
    ESP_ERROR_CHECK(esp_event_loop_create_default());
    esp_netif_create_default_wifi_sta();
    wifi_init_config_t cfg = WIFI_INIT_CONFIG_DEFAULT();
    ESP_ERROR_CHECK(esp_wifi_init(&cfg));
    ESP_ERROR_CHECK(esp_event_handler_register(WIFI_EVENT, ESP_EVENT_ANY_ID, on_event, NULL));
    ESP_ERROR_CHECK(esp_event_handler_register(IP_EVENT, IP_EVENT_STA_GOT_IP, on_event, NULL));
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));
    ESP_ERROR_CHECK(esp_wifi_start());
    s_started = true;

    if (s_count == 0) {
        ESP_LOGI(TAG, "no saved networks");
        return ESP_OK;
    }

    wifi_scan_config_t scan = {.show_hidden = false};
    esp_wifi_scan_start(&scan, true);
    uint16_t found = 0;
    esp_wifi_scan_get_ap_num(&found);
    if (found > 16) {
        found = 16;
    }
    wifi_ap_record_t recs[16];
    uint16_t copied = found;
    esp_wifi_scan_get_ap_records(&copied, recs);

    int best = -1;
    int best_rssi = -1000;
    for (int i = 0; i < s_count; i++) {
        for (int j = 0; j < copied; j++) {
            if (strcmp(s_creds[i].ssid, (char *)recs[j].ssid) == 0 && recs[j].rssi > best_rssi) {
                best = i;
                best_rssi = recs[j].rssi;
            }
        }
    }
    if (best < 0) {
        best = 0;
    }
    wifi_config_t sta = {0};
    strncpy((char *)sta.sta.ssid, s_creds[best].ssid, sizeof(sta.sta.ssid) - 1);
    strncpy((char *)sta.sta.password, s_creds[best].pass, sizeof(sta.sta.password) - 1);
    esp_wifi_set_config(WIFI_IF_STA, &sta);
    esp_wifi_connect();
    ESP_LOGI(TAG, "joining %s", s_creds[best].ssid);
    return ESP_OK;
}

bool radio_is_connected(void)
{
    return s_connected;
}

bool radio_has_networks(void)
{
    return s_count > 0;
}

esp_err_t radio_save_network(const char *ssid, const char *pass)
{
    if (!ssid || !ssid[0] || s_count >= WIFI_MAX) {
        return ESP_ERR_INVALID_ARG;
    }
    strncpy(s_creds[s_count].ssid, ssid, sizeof(s_creds[s_count].ssid) - 1);
    strncpy(s_creds[s_count].pass, pass ? pass : "", sizeof(s_creds[s_count].pass) - 1);
    s_count++;
    persist();
    return ESP_OK;
}

static void connect_first(void)
{
    if (!s_started || s_count <= 0) {
        return;
    }
    wifi_config_t sta = {0};
    strncpy((char *)sta.sta.ssid, s_creds[0].ssid, sizeof(sta.sta.ssid) - 1);
    strncpy((char *)sta.sta.password, s_creds[0].pass, sizeof(sta.sta.password) - 1);
    esp_wifi_set_config(WIFI_IF_STA, &sta);
    esp_wifi_connect();
    ESP_LOGI(TAG, "joining %s", s_creds[0].ssid);
}

esp_err_t radio_replace_networks(const radio_network_t *list, int count)
{
    if (count < 0 || count > WIFI_MAX || (count > 0 && !list)) {
        return ESP_ERR_INVALID_ARG;
    }
    memset(s_creds, 0, sizeof(s_creds));
    for (int i = 0; i < count; i++) {
        if (!list[i].ssid[0]) {
            return ESP_ERR_INVALID_ARG;
        }
        strncpy(s_creds[i].ssid, list[i].ssid, sizeof(s_creds[i].ssid) - 1);
        strncpy(s_creds[i].pass, list[i].pass, sizeof(s_creds[i].pass) - 1);
    }
    s_count = count;
    s_retries = 0;
    persist();
    if (s_started && count == 0) {
        esp_wifi_disconnect();
        return ESP_OK;
    }
    connect_first();
    return ESP_OK;
}

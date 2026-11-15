#pragma once

#include <stdbool.h>
#include "esp_err.h"

esp_err_t radio_init(void);
bool radio_is_connected(void);
bool radio_has_networks(void);
#define RADIO_WIFI_MAX 5

typedef struct {
    char ssid[33];
    char pass[65];
} radio_network_t;

esp_err_t radio_save_network(const char *ssid, const char *pass);
esp_err_t radio_replace_networks(const radio_network_t *list, int count);

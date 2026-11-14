#pragma once

#include <stdbool.h>

esp_err_t radio_init(void);
bool radio_is_connected(void);
bool radio_has_networks(void);
esp_err_t radio_save_network(const char *ssid, const char *pass);
void radio_prov_stub(void);

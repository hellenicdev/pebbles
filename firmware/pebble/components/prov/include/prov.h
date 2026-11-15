#pragma once

#include "esp_err.h"

/*
 * Pairing payload, version 1, UTF-8 JSON, written to the provision
 * characteristic. The same bytes are built by the Android app.
 *
 * {"v":1,"colour":"Amber","rgb":[224,138,69],"quiet":[22,7],
 *  "names":["Sam","Noor","Alex","Jo","Ren"],
 *  "wifi":[{"ssid":"home","pass":"secret"}]}
 *
 * Service 5b1a8c10-6e2d-4a7f-9c31-0d4e5f6a7b80
 * Write   5b1a8c11-6e2d-4a7f-9c31-0d4e5f6a7b80
 * Status  5b1a8c12-6e2d-4a7f-9c31-0d4e5f6a7b80  ("ok" or "err")
 *
 * There is no PIN yet. Anyone nearby can write while the Pebble is advertising.
 */

esp_err_t prov_start(void);

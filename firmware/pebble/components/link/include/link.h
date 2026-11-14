#pragma once

#include "esp_err.h"
#include "touch_queue.h"

/* Future socket. The server does not exist yet. */
#define LINK_URI "wss://api.hellenicdev.eu/v1/pebble"

void link_init(void);
esp_err_t link_send(const queued_touch_t *event);
void link_flush(void);

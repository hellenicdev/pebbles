#pragma once

#include <stdint.h>
#include "esp_err.h"

typedef enum {
    LED_CTX_IDLE = 0,
    LED_CTX_UNPROVISIONED,
    LED_CTX_CHARGING,
    LED_CTX_FULL,
    LED_CTX_LOW_BATTERY,
} led_ctx_t;

esp_err_t leds_init(void);
void leds_set_colour(uint8_t r, uint8_t g, uint8_t b);
void leds_note_sent(void);
void leds_note_offline(void);
void leds_note_pickup(void);
void leds_set_context(led_ctx_t ctx);
void leds_tick(void);
void leds_factory_chase(void);

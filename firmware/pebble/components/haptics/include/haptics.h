#pragma once

#include <stdbool.h>
#include "esp_err.h"

typedef enum {
    HAPTIC_TAP = 0,
    HAPTIC_HUG,
} haptic_kind_t;

esp_err_t haptics_init(void);
void haptics_pulse(haptic_kind_t kind);
void haptics_tick(void);
bool haptics_present(void);

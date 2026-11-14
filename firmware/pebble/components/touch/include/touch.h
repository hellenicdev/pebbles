#pragma once

#include <stdbool.h>

typedef enum {
    TOUCH_NONE = 0,
    TOUCH_TAP,
    TOUCH_HUG,
} touch_kind_t;

esp_err_t touch_init(void);
touch_kind_t touch_poll(void);
bool touch_is_down(void);

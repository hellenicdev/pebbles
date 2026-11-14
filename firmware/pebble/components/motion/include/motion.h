#pragma once

#include <stdbool.h>

esp_err_t motion_init(void);
bool motion_pickup(void);
bool motion_present(void);

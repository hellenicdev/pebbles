#pragma once

#include "esp_err.h"

void ota_confirm_or_rollback(void);
esp_err_t ota_start_when_docked(const char *url);

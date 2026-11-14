#pragma once

#include <stdbool.h>

esp_err_t heater_init(void);
void heater_request_warmth(void);
void heater_stop(void);
void heater_kill_low_battery(void);
void heater_clear_battery_latch(void);
void heater_tick(void);
bool heater_fault(void);
int heater_film_c_x10(void);

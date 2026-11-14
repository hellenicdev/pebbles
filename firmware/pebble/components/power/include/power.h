#pragma once

#include <stdbool.h>

typedef enum {
    CHARGE_UNKNOWN = 0,
    CHARGE_DISCHARGING,
    CHARGE_CHARGING,
    CHARGE_FULL,
} charge_state_t;

esp_err_t power_init(void);
void power_periph_on(void);
int power_vbat_mv(void);
int power_sample_vbat_mv(void);
int power_ntc_pin_mv(void);
charge_state_t power_charge_state(void);
bool power_is_low_battery(void);
bool power_is_docked(void);
void power_tick(void);

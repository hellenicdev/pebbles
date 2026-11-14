#pragma once

#include <stdbool.h>
#include <stdint.h>

void quiet_init(void);
bool quiet_hours_active(void);
void quiet_set_window(uint8_t start_hour, uint8_t end_hour);

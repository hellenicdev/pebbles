#pragma once

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

esp_err_t bus_init(void);
esp_err_t bus_probe(uint8_t addr);
esp_err_t bus_write_reg(uint8_t addr, uint8_t reg, uint8_t value);
esp_err_t bus_read_reg(uint8_t addr, uint8_t reg, uint8_t *out, size_t len);
bool bus_ready(void);
void bus_log_known(void);

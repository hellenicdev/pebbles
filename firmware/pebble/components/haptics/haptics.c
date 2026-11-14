#include "haptics.h"

#include "pins.h"
#include "bus.h"
#include "quiet.h"
#include "esp_log.h"
#include "esp_timer.h"

/*
 * U7 DRV2605L, I2C 0x5A. EN is tied to +3V3, so the chip is powered whenever
 * the cell is. Standby is an I2C mode bit. The LRA is LD0832AA-0099F; the
 * schematic does not give its rated voltage, so the voltage registers are
 * left at reset and only the ROM library is used.
 */

static const char *TAG = "haptics";
static bool s_present;
static int64_t s_standby_at;

esp_err_t haptics_init(void)
{
    s_present = bus_probe(I2C_ADDR_DRV2605L) == ESP_OK;
    if (!s_present) {
        ESP_LOGW(TAG, "DRV2605L not answering");
        return ESP_ERR_NOT_FOUND;
    }
    bus_write_reg(I2C_ADDR_DRV2605L, 0x01, 0x00);
    bus_write_reg(I2C_ADDR_DRV2605L, 0x1A, 0x80); /* N_ERM_LRA */
    bus_write_reg(I2C_ADDR_DRV2605L, 0x03, 6);    /* LRA library */
    bus_write_reg(I2C_ADDR_DRV2605L, 0x01, 0x40); /* standby */
    return ESP_OK;
}

bool haptics_present(void)
{
    return s_present;
}

void haptics_pulse(haptic_kind_t kind)
{
    if (!s_present || quiet_hours_active()) {
        return;
    }
    uint8_t effect = kind == HAPTIC_HUG ? 14 : 1;
    bus_write_reg(I2C_ADDR_DRV2605L, 0x01, 0x00);
    bus_write_reg(I2C_ADDR_DRV2605L, 0x04, effect);
    bus_write_reg(I2C_ADDR_DRV2605L, 0x0C, 0x01);
    s_standby_at = esp_timer_get_time() + (kind == HAPTIC_HUG ? 800000 : 300000);
}

void haptics_tick(void)
{
    if (!s_present || s_standby_at == 0) {
        return;
    }
    if (esp_timer_get_time() >= s_standby_at) {
        bus_write_reg(I2C_ADDR_DRV2605L, 0x01, 0x40);
        s_standby_at = 0;
    }
}

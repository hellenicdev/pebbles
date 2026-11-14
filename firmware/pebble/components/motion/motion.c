#include "motion.h"

#include "pins.h"
#include "bus.h"
#include "esp_log.h"

/*
 * LIS2DW12 at 0x19 (SA0 high). INT1 shares GPIO4 with the IO expander and the
 * NFC field-detect pin. CTRL3 is set open-drain and active-low before any
 * interrupt is enabled. INT1 itself is left unrouted until that routing is
 * checked on hardware. Pickup is a polled acceleration change.
 *
 * TODO: route INT1_WU only after CTRL3.PP_OD and H_LACTIVE are confirmed.
 * TODO: NT3H2111 FD_ON/FD_OFF session registers for the shared IRQ.
 */

static const char *TAG = "motion";
static bool s_present;
static int s_last;

esp_err_t motion_init(void)
{
    uint8_t who = 0;
    if (bus_read_reg(I2C_ADDR_LIS2DW12, 0x0F, &who, 1) != ESP_OK || who != 0x44) {
        ESP_LOGW(TAG, "LIS2DW12 missing (who=0x%02x)", who);
        return ESP_ERR_NOT_FOUND;
    }
    bus_write_reg(I2C_ADDR_LIS2DW12, 0x22, 0x30); /* PP_OD | H_LACTIVE */
    bus_write_reg(I2C_ADDR_LIS2DW12, 0x20, 0x50); /* ODR ~50 Hz */
    s_present = true;
    return ESP_OK;
}

bool motion_present(void)
{
    return s_present;
}

bool motion_pickup(void)
{
    if (!s_present) {
        return false;
    }
    uint8_t raw[6];
    if (bus_read_reg(I2C_ADDR_LIS2DW12, 0x28, raw, sizeof(raw)) != ESP_OK) {
        return false;
    }
    int16_t x = (int16_t)((raw[1] << 8) | raw[0]);
    int16_t y = (int16_t)((raw[3] << 8) | raw[2]);
    int16_t z = (int16_t)((raw[5] << 8) | raw[4]);
    int mag = (x / 16) * (x / 16) + (y / 16) * (y / 16) + (z / 16) * (z / 16);
    int delta = mag - s_last;
    if (delta < 0) {
        delta = -delta;
    }
    s_last = mag;
    return delta > 40000;
}

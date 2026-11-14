#include "bus.h"

#include <stdbool.h>
#include "pins.h"
#include "driver/i2c_master.h"
#include "esp_log.h"

static const char *TAG = "bus";
static i2c_master_bus_handle_t s_bus;
static bool s_ready;

static const uint8_t s_addrs[] = {
    I2C_ADDR_XL9535,
    I2C_ADDR_LIS2DW12,
    I2C_ADDR_DRV2605L,
    I2C_ADDR_NT3H2111,
};

esp_err_t bus_init(void)
{
    i2c_master_bus_config_t cfg = {
        .i2c_port = I2C_NUM_0,
        .sda_io_num = PIN_I2C_SDA,
        .scl_io_num = PIN_I2C_SCL,
        .clk_source = I2C_CLK_SRC_DEFAULT,
        .glitch_ignore_cnt = 7,
        .flags.enable_internal_pullup = false,
    };
    esp_err_t err = i2c_new_master_bus(&cfg, &s_bus);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "I2C bus failed: %s", esp_err_to_name(err));
        return err;
    }
    s_ready = true;
    ESP_LOGI(TAG, "I2C on GPIO%d/GPIO%d", PIN_I2C_SDA, PIN_I2C_SCL);
    return ESP_OK;
}

bool bus_ready(void)
{
    return s_ready;
}

static esp_err_t device(uint8_t addr, i2c_master_dev_handle_t *out)
{
    if (!s_ready) {
        return ESP_ERR_INVALID_STATE;
    }
    i2c_device_config_t cfg = {
        .dev_addr_length = I2C_ADDR_BIT_LEN_7,
        .device_address = addr,
        .scl_speed_hz = 100000,
    };
    return i2c_master_bus_add_device(s_bus, &cfg, out);
}

esp_err_t bus_probe(uint8_t addr)
{
    if (!s_ready) {
        return ESP_ERR_INVALID_STATE;
    }
    return i2c_master_probe(s_bus, addr, 50);
}

esp_err_t bus_write_reg(uint8_t addr, uint8_t reg, uint8_t value)
{
    i2c_master_dev_handle_t dev;
    esp_err_t err = device(addr, &dev);
    if (err != ESP_OK) {
        return err;
    }
    uint8_t buf[2] = {reg, value};
    err = i2c_master_transmit(dev, buf, sizeof(buf), 100);
    i2c_master_bus_rm_device(dev);
    return err;
}

esp_err_t bus_read_reg(uint8_t addr, uint8_t reg, uint8_t *out, size_t len)
{
    i2c_master_dev_handle_t dev;
    esp_err_t err = device(addr, &dev);
    if (err != ESP_OK) {
        return err;
    }
    err = i2c_master_transmit_receive(dev, &reg, 1, out, len, 100);
    i2c_master_bus_rm_device(dev);
    return err;
}

void bus_log_known(void)
{
    for (size_t i = 0; i < sizeof(s_addrs); i++) {
        esp_err_t err = bus_probe(s_addrs[i]);
        ESP_LOGI(TAG, "probe 0x%02x: %s", s_addrs[i], esp_err_to_name(err));
    }
}

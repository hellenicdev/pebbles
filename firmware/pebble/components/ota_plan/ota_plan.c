#include "ota_plan.h"

#include "pins.h"
#include "power.h"
#include "bus.h"
#include "esp_log.h"
#include "esp_ota_ops.h"

/*
 * Images are signed at build time (development ECDSA key in keys/). The
 * bootloader rejects an unsigned update. Rollback is armed by the otadata
 * partition: a new image stays pending until this boot's self-test passes.
 *
 * Installation is refused unless VBAT is rising. That is the only dock
 * signal on this board. A full, flat cell is not treated as docked.
 *
 * TODO: download `url` with esp_https_ota once the update server exists,
 * then esp_ota_set_boot_partition and restart. The URL is accepted here so
 * the policy is in one place.
 */

static const char *TAG = "ota";

void ota_confirm_or_rollback(void)
{
    const esp_partition_t *running = esp_ota_get_running_partition();
    esp_ota_img_states_t state;
    if (esp_ota_get_state_partition(running, &state) != ESP_OK ||
        state != ESP_OTA_IMG_PENDING_VERIFY) {
        return;
    }
    bool healthy = bus_ready() && bus_probe(I2C_ADDR_DRV2605L) == ESP_OK;
    if (healthy) {
        esp_ota_mark_app_valid_cancel_rollback();
        ESP_LOGI(TAG, "image confirmed");
    } else {
        ESP_LOGE(TAG, "self-test failed, rolling back");
        esp_ota_mark_app_invalid_rollback_and_reboot();
    }
}

esp_err_t ota_start_when_docked(const char *url)
{
    if (!power_is_docked()) {
        ESP_LOGW(TAG, "refusing update: not docked (VBAT is not rising)");
        return ESP_ERR_INVALID_STATE;
    }
    ESP_LOGW(TAG, "TODO: signed OTA from %s", url ? url : "(none)");
    return ESP_ERR_NOT_SUPPORTED;
}

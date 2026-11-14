#include <time.h>
#include "nvs.h"
#include "nvs_flash.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "pins.h"
#include "bus.h"
#include "power.h"
#include "heater.h"
#include "haptics.h"
#include "leds.h"
#include "audio.h"
#include "touch.h"
#include "quiet.h"
#include "touch_queue.h"
#include "motion.h"
#include "radio.h"
#include "link.h"
#include "factory.h"
#include "ota_plan.h"

static const char *TAG = "pebble";

static void load_colour(void)
{
    nvs_handle_t nvs;
    uint8_t rgb[3] = {0xE0, 0x8A, 0x45};
    if (nvs_open("pebble", NVS_READONLY, &nvs) == ESP_OK) {
        size_t len = sizeof(rgb);
        nvs_get_blob(nvs, "colour", rgb, &len);
        nvs_close(nvs);
    }
    leds_set_colour(rgb[0], rgb[1], rgb[2]);
}

static void on_touch(touch_kind_t kind)
{
    uint8_t code = kind == TOUCH_HUG ? 2 : 1;
    if (kind == TOUCH_HUG) {
        heater_request_warmth();
        haptics_pulse(HAPTIC_HUG);
    } else {
        haptics_pulse(HAPTIC_TAP);
    }
    audio_chime();
    leds_note_sent();
    if (!radio_is_connected()) {
        touch_queue_push(code, 0);
        leds_note_offline();
        ESP_LOGI(TAG, "queued offline touch");
        return;
    }
    queued_touch_t ev = {.kind = code, .target = 0, .unix_s = (uint32_t)time(NULL)};
    if (link_send(&ev) != ESP_OK) {
        touch_queue_push(code, 0);
    }
}

void app_main(void)
{
    esp_err_t err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND) {
        nvs_flash_erase();
        nvs_flash_init();
    }

    power_init();
    bus_init();
    heater_init();
    quiet_init();
    touch_queue_init();
    haptics_init();
    leds_init();
    audio_init();
    touch_init();
    motion_init();
    load_colour();
    link_init();
    ota_confirm_or_rollback();

    if (factory_hold_at_boot()) {
        factory_run();
    }

    radio_init();
    radio_prov_stub();

    bool low_latched = false;
    int64_t hello_block_until = 0;

    while (1) {
        power_tick();
        if (power_is_low_battery()) {
            if (!low_latched) {
                heater_kill_low_battery();
                low_latched = true;
            }
        } else if (low_latched && power_vbat_mv() > 3600) {
            heater_clear_battery_latch();
            low_latched = false;
        }

        if (power_is_low_battery()) {
            leds_set_context(LED_CTX_LOW_BATTERY);
        } else if (power_charge_state() == CHARGE_CHARGING) {
            leds_set_context(LED_CTX_CHARGING);
        } else if (power_charge_state() == CHARGE_FULL) {
            leds_set_context(LED_CTX_FULL);
        } else if (!radio_has_networks()) {
            leds_set_context(LED_CTX_UNPROVISIONED);
        } else {
            leds_set_context(LED_CTX_IDLE);
        }

        touch_kind_t kind = touch_poll();
        if (kind != TOUCH_NONE) {
            on_touch(kind);
        }
        if (motion_pickup() && esp_timer_get_time() > hello_block_until) {
            leds_note_pickup();
            hello_block_until = esp_timer_get_time() + 3000000;
        }

        heater_tick();
        haptics_tick();
        leds_tick();
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

#include "factory.h"

#include "pins.h"
#include "bus.h"
#include "power.h"
#include "heater.h"
#include "haptics.h"
#include "leds.h"
#include "audio.h"
#include "touch.h"
#include "motion.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

static const char *TAG = "factory";

bool factory_hold_at_boot(void)
{
    if (!touch_is_down()) {
        return false;
    }
    vTaskDelay(pdMS_TO_TICKS(1500));
    return touch_is_down();
}

void factory_run(void)
{
    ESP_LOGW(TAG, "factory test");
    bus_log_known();
    ESP_LOGI(TAG, "IRQ GPIO%d level %d (external pull-up, expect 1)", PIN_IRQ, gpio_get_level(PIN_IRQ));
    ESP_LOGI(TAG, "touch level %d, accel %d", touch_is_down(), motion_present());
    leds_factory_chase();
    haptics_pulse(HAPTIC_TAP);
    vTaskDelay(pdMS_TO_TICKS(400));
    audio_chime();
    heater_request_warmth();
    vTaskDelay(pdMS_TO_TICKS(300));
    heater_tick();
    int film = heater_film_c_x10();
    ESP_LOGI(TAG, "film %d.%d C fault=%d vbat=%d mV",
             film < 0 ? -1 : film / 10, film < 0 ? 0 : film % 10,
             heater_fault(), power_sample_vbat_mv());
    heater_stop();
    ESP_LOGW(TAG, "factory done, heater forced off");
}

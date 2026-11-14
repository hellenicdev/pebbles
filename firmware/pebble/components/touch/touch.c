#include "touch.h"

#include "pins.h"
#include "driver/gpio.h"
#include "esp_timer.h"

/* TTP233H Q is push-pull, active high, direct mode. A hold of 600 ms is a hug. */

#define HOLD_US 600000
#define DEBOUNCE_US 20000

static bool s_down;
static bool s_hug_sent;
static int64_t s_down_at;
static int64_t s_change_at;

esp_err_t touch_init(void)
{
    gpio_config_t cfg = {
        .pin_bit_mask = 1ULL << PIN_TOUCH,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
    };
    gpio_config(&cfg);
    gpio_config_t irq = {
        .pin_bit_mask = 1ULL << PIN_IRQ,
        .mode = GPIO_MODE_INPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
    };
    return gpio_config(&irq);
}

bool touch_is_down(void)
{
    return gpio_get_level(PIN_TOUCH) == 1;
}

touch_kind_t touch_poll(void)
{
    int64_t now = esp_timer_get_time();
    bool down = touch_is_down();
    if (down != s_down) {
        if (now - s_change_at < DEBOUNCE_US) {
            return TOUCH_NONE;
        }
        s_change_at = now;
        s_down = down;
        if (down) {
            s_down_at = now;
            s_hug_sent = false;
            return TOUCH_NONE;
        }
        if (!s_hug_sent && now - s_down_at > DEBOUNCE_US) {
            return TOUCH_TAP;
        }
        return TOUCH_NONE;
    }
    if (down && !s_hug_sent && now - s_down_at >= HOLD_US) {
        s_hug_sent = true;
        return TOUCH_HUG;
    }
    return TOUCH_NONE;
}

#include "leds.h"

#include "pins.h"
#include "power.h"
#include "led_strip.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

/* Eight SK6805-EC15 on GPIO8, GRB, level-shifted only while EN_PERIPH is on. */

static const char *TAG = "leds";
static led_strip_handle_t s_strip;
static uint8_t s_r = 0xE0, s_g = 0x8A, s_b = 0x45;
static led_ctx_t s_ctx = LED_CTX_IDLE;
static int64_t s_sent_until;
static int64_t s_hello_until;
static int s_flicker;

static void paint(uint8_t r, uint8_t g, uint8_t b, int scale)
{
    if (!s_strip) {
        return;
    }
    if (scale < 0) {
        scale = 0;
    }
    if (scale > 255) {
        scale = 255;
    }
    for (int i = 0; i < LED_COUNT; i++) {
        int s = (i == LED_CHAIN_CENTER) ? scale : (scale * 3) / 4;
        led_strip_set_pixel(s_strip, i,
                            (uint8_t)((r * s) / 255),
                            (uint8_t)((g * s) / 255),
                            (uint8_t)((b * s) / 255));
    }
    led_strip_refresh(s_strip);
}

static int breathe(int period_ms)
{
    int64_t t = (esp_timer_get_time() / 1000) % period_ms;
    int half = period_ms / 2;
    int up = t < half ? (int)t : period_ms - (int)t;
    return 16 + (up * 80) / half;
}

esp_err_t leds_init(void)
{
    power_periph_on();
    led_strip_config_t strip = {
        .strip_gpio_num = PIN_LED_DIN,
        .max_leds = LED_COUNT,
        .led_model = LED_MODEL_WS2812,
        .color_component_format = LED_STRIP_COLOR_COMPONENT_FMT_GRB,
        .flags.invert_out = false,
    };
    led_strip_rmt_config_t rmt = {
        .clk_src = RMT_CLK_SRC_DEFAULT,
        .resolution_hz = 10 * 1000 * 1000,
        .mem_block_symbols = 64,
        .flags.with_dma = false,
    };
    esp_err_t err = led_strip_new_rmt_device(&strip, &rmt, &s_strip);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "led strip: %s", esp_err_to_name(err));
        return err;
    }
    led_strip_clear(s_strip);
    return ESP_OK;
}

void leds_set_colour(uint8_t r, uint8_t g, uint8_t b)
{
    s_r = r;
    s_g = g;
    s_b = b;
}

void leds_note_sent(void)
{
    s_sent_until = esp_timer_get_time() + 2500000;
}

void leds_note_offline(void)
{
    s_flicker = 6;
}

void leds_note_pickup(void)
{
    s_hello_until = esp_timer_get_time() + 1600000;
}

void leds_set_context(led_ctx_t ctx)
{
    s_ctx = ctx;
}

void leds_tick(void)
{
    int64_t now = esp_timer_get_time();
    if (s_flicker > 0) {
        paint(180, 0, 0, (s_flicker % 2) ? 140 : 0);
        s_flicker--;
        return;
    }
    if (now < s_hello_until) {
        int left = (int)((s_hello_until - now) / 1000);
        int scale = left > 800 ? (1600 - left) / 8 : left / 8;
        paint(s_r, s_g, s_b, scale);
        return;
    }
    if (now < s_sent_until) {
        paint(s_r, s_g, s_b, 90);
        return;
    }
    switch (s_ctx) {
    case LED_CTX_LOW_BATTERY:
        paint(0xE0, 0x8A, 0x20, breathe(1400));
        break;
    case LED_CTX_CHARGING:
        paint(255, 255, 255, breathe(2800));
        break;
    case LED_CTX_FULL:
        paint(0, 0, 0, 0);
        break;
    case LED_CTX_UNPROVISIONED:
        paint(255, 255, 255, breathe(4000));
        break;
    default:
        paint(0, 0, 0, 0);
        break;
    }
}

void leds_factory_chase(void)
{
    if (!s_strip) {
        return;
    }
    for (int n = 0; n < LED_COUNT; n++) {
        for (int i = 0; i < LED_COUNT; i++) {
            led_strip_set_pixel(s_strip, i, i == n ? 40 : 0, i == n ? 20 : 0, 0);
        }
        led_strip_refresh(s_strip);
        vTaskDelay(pdMS_TO_TICKS(80));
    }
    led_strip_clear(s_strip);
}

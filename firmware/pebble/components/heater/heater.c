#include "heater.h"

#include <math.h>
#include "pins.h"
#include "power.h"
#include "driver/ledc.h"
#include "esp_log.h"
#include "esp_timer.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

/*
 * There is NO hardware cut-off on the film heater. No thermal fuse, no
 * comparator, no current limit. Q1 is a low-side MOSFET whose gate is
 * HEAT_EN (GPIO10) with only a 100k pulldown. The cell NTC (TH1) goes to the
 * charger, not to this path. Firmware is the only thing that keeps the film
 * at or below 42 C.
 *
 * The sense divider is not from 3V3. It is:
 *   HEAT_EN -- R20 10k -- GPIO0 -- TH2 10k B3380 -- GND
 * GPIO0 is meaningless while GPIO10 is low. Every reading forces the MOSFET
 * on for a short sample, then restores the PWM duty. An open or a short is a
 * fault, not a temperature, and latches the heater off.
 *
 * The film resistance is not in the schematic (notes call it a 30 mm 5 V
 * film on a 3.0-4.2 V cell). Duty is capped well below 100% anyway.
 */

static const char *TAG = "heater";

#define HARD_CAP_C_X10 420
#define TARGET_C_X10 390
#define MAX_ON_MS 15000
#define SAMPLE_MS 2
#define TICK_MS 200
#define GPIO_HIGH_MV 3300

static int s_duty;
static int s_on_ms;
static int s_film_c_x10 = -1;
static bool s_fault;
static bool s_killed;
static bool s_session;
static int64_t s_last_us;

static void set_duty_raw(int duty)
{
    if (duty < 0) {
        duty = 0;
    }
    if (duty > 1023) {
        duty = 1023;
    }
    s_duty = duty;
    ledc_set_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0, duty);
    ledc_update_duty(LEDC_LOW_SPEED_MODE, LEDC_CHANNEL_0);
}

static void apply_duty(int duty)
{
    /* Running duty stays at or below 80%. A sample pulse may go to 100% briefly. */
    if (duty > 800) {
        duty = 800;
    }
    set_duty_raw(duty);
}

static int sample_ntc_mv(void)
{
    int saved = s_duty;
    set_duty_raw(1023);
    vTaskDelay(pdMS_TO_TICKS(SAMPLE_MS));
    int mv = power_ntc_pin_mv();
    set_duty_raw(saved);
    return mv;
}

static int film_c_x10_from_mv(int mv)
{
    if (mv <= 50 || mv >= GPIO_HIGH_MV - 80) {
        return -1; /* short or open */
    }
    float r = (float)NTC_FIXED_OHMS * (float)mv / (float)(GPIO_HIGH_MV - mv);
    float inv = (1.0f / 298.15f) + logf(r / (float)NTC_R0_OHMS) / (float)NTC_BETA;
    float c = (1.0f / inv) - 273.15f;
    return (int)(c * 10.0f);
}

esp_err_t heater_init(void)
{
    ledc_timer_config_t timer = {
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .duty_resolution = LEDC_TIMER_10_BIT,
        .timer_num = LEDC_TIMER_0,
        .freq_hz = 100,
        .clk_cfg = LEDC_AUTO_CLK,
    };
    ESP_ERROR_CHECK(ledc_timer_config(&timer));
    ledc_channel_config_t chan = {
        .gpio_num = PIN_HEAT_EN,
        .speed_mode = LEDC_LOW_SPEED_MODE,
        .channel = LEDC_CHANNEL_0,
        .timer_sel = LEDC_TIMER_0,
        .duty = 0,
        .hpoint = 0,
    };
    ESP_ERROR_CHECK(ledc_channel_config(&chan));
    apply_duty(0);
    s_last_us = esp_timer_get_time();
    ESP_LOGI(TAG, "heater off; 42 C cap is software-only");
    return ESP_OK;
}

void heater_request_warmth(void)
{
    if (s_killed) {
        ESP_LOGW(TAG, "warmth refused: low battery latch");
        return;
    }
    s_fault = false;
    s_session = true;
    s_on_ms = 0;
}

void heater_stop(void)
{
    s_session = false;
    apply_duty(0);
}

void heater_kill_low_battery(void)
{
    /* Called before any other low-battery reaction. Warmth goes first. */
    s_killed = true;
    s_session = false;
    apply_duty(0);
    ESP_LOGW(TAG, "heater killed: low battery");
}

bool heater_fault(void)
{
    return s_fault;
}

int heater_film_c_x10(void)
{
    return s_film_c_x10;
}

void heater_tick(void)
{
    int64_t now = esp_timer_get_time();
    int elapsed = (int)((now - s_last_us) / 1000);
    s_last_us = now;
    if (s_duty > 0 && elapsed > 0) {
        s_on_ms += elapsed;
    }

    if (s_killed || !s_session) {
        apply_duty(0);
        return;
    }
    if (s_on_ms >= MAX_ON_MS) {
        ESP_LOGW(TAG, "heater max on-time");
        heater_stop();
        return;
    }

    int mv = sample_ntc_mv();
    int t = film_c_x10_from_mv(mv);
    if (mv < 0 || t < 0) {
        s_fault = true;
        s_film_c_x10 = -1;
        ESP_LOGE(TAG, "NTC fault (open/short or ADC), mv=%d", mv);
        heater_stop();
        return;
    }
    s_film_c_x10 = t;
    if (t >= HARD_CAP_C_X10) {
        s_fault = true;
        ESP_LOGE(TAG, "film %d.%d C, cap hit, heater off", t / 10, t % 10);
        heater_stop();
        return;
    }

    int duty = 0;
    if (t < 360) {
        duty = 700;
    } else if (t < TARGET_C_X10) {
        duty = 350;
    }
    apply_duty(duty);
}

void heater_clear_battery_latch(void)
{
    s_killed = false;
}

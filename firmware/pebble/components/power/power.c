#include "power.h"

#include "pins.h"
#include "driver/gpio.h"
#include "esp_adc/adc_oneshot.h"
#include "esp_adc/adc_cali.h"
#include "esp_adc/adc_cali_scheme.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"

/*
 * There is no charge-status pin. U1 BQ51050 ~CHG is unconnected, and nothing
 * else tells the MCU that Qi is present. "Charging" is inferred from VBAT_SW
 * rising. A high flat voltage is reported as full, but that also looks like a
 * rested full cell in the hand, so OTA does not treat FULL as docked.
 *
 * The divider is VBAT_SW - 1M - GPIO1 - 1M - GND, ratio 1/2. It is only valid
 * while EN_PERIPH (GPIO21) has turned the load switch on.
 */

#define LOW_BATTERY_MV 3500
#define SAMPLE_PERIOD_MS 2000
#define HISTORY 8

static adc_oneshot_unit_handle_t s_adc;
static adc_cali_handle_t s_cal_ntc;
static adc_cali_handle_t s_cal_vbat;
static SemaphoreHandle_t s_lock;
static int s_history[HISTORY];
static int s_count;
static int s_vbat_mv = -1;
static charge_state_t s_charge = CHARGE_UNKNOWN;

static bool cali(adc_channel_t chan, adc_cali_handle_t *out)
{
    adc_cali_curve_fitting_config_t cfg = {
        .unit_id = ADC_UNIT_1,
        .chan = chan,
        .atten = ADC_ATTEN_DB_12,
        .bitwidth = ADC_BITWIDTH_DEFAULT,
    };
    return adc_cali_create_scheme_curve_fitting(&cfg, out) == ESP_OK;
}

static int read_mv(adc_channel_t chan, adc_cali_handle_t cal)
{
    int raw = 0;
    int mv = 0;
    if (xSemaphoreTake(s_lock, pdMS_TO_TICKS(50)) != pdTRUE) {
        return -1;
    }
    if (!cal) {
        xSemaphoreGive(s_lock);
        return -1;
    }
    esp_err_t err = adc_oneshot_read(s_adc, chan, &raw);
    if (err == ESP_OK) {
        err = adc_cali_raw_to_voltage(cal, raw, &mv);
    }
    xSemaphoreGive(s_lock);
    if (err != ESP_OK) {
        return -1;
    }
    return mv;
}

esp_err_t power_init(void)
{
    s_lock = xSemaphoreCreateMutex();
    gpio_config_t periph = {
        .pin_bit_mask = 1ULL << PIN_EN_PERIPH,
        .mode = GPIO_MODE_OUTPUT,
        .pull_up_en = GPIO_PULLUP_DISABLE,
        .pull_down_en = GPIO_PULLDOWN_DISABLE,
    };
    gpio_config(&periph);
    gpio_set_level(PIN_EN_PERIPH, 0);

    adc_oneshot_unit_init_cfg_t unit = {.unit_id = ADC_UNIT_1};
    ESP_ERROR_CHECK(adc_oneshot_new_unit(&unit, &s_adc));
    adc_oneshot_chan_cfg_t chan = {
        .atten = ADC_ATTEN_DB_12,
        .bitwidth = ADC_BITWIDTH_DEFAULT,
    };
    ESP_ERROR_CHECK(adc_oneshot_config_channel(s_adc, ADC_CHANNEL_0, &chan));
    ESP_ERROR_CHECK(adc_oneshot_config_channel(s_adc, ADC_CHANNEL_1, &chan));
    cali(ADC_CHANNEL_0, &s_cal_ntc);
    cali(ADC_CHANNEL_1, &s_cal_vbat);
    power_periph_on();
    return ESP_OK;
}

void power_periph_on(void)
{
    gpio_set_level(PIN_EN_PERIPH, 1);
}

int power_ntc_pin_mv(void)
{
    return read_mv(ADC_CHANNEL_0, s_cal_ntc);
}

int power_vbat_mv(void)
{
    return s_vbat_mv;
}

int power_sample_vbat_mv(void)
{
    int pin = read_mv(ADC_CHANNEL_1, s_cal_vbat);
    if (pin < 0) {
        return -1;
    }
    s_vbat_mv = pin * VBAT_DIVIDER_NUM / VBAT_DIVIDER_DEN;
    return s_vbat_mv;
}

charge_state_t power_charge_state(void)
{
    return s_charge;
}

bool power_is_low_battery(void)
{
    return s_vbat_mv > 0 && s_vbat_mv < LOW_BATTERY_MV;
}

bool power_is_docked(void)
{
    /* Rising pack voltage is the only evidence of the dock. FULL is not. */
    return s_charge == CHARGE_CHARGING;
}

void power_tick(void)
{
    static int waited_ms;
    waited_ms += 200;
    if (waited_ms < SAMPLE_PERIOD_MS) {
        return;
    }
    waited_ms = 0;

    int pin = read_mv(ADC_CHANNEL_1, s_cal_vbat);
    if (pin < 0) {
        return;
    }
    int vbat = pin * VBAT_DIVIDER_NUM / VBAT_DIVIDER_DEN;
    s_vbat_mv = vbat;
    s_history[s_count % HISTORY] = vbat;
    s_count++;
    if (s_count < 4) {
        s_charge = CHARGE_UNKNOWN;
        return;
    }
    int newest = s_history[(s_count - 1) % HISTORY];
    int oldest = s_history[(s_count - 4) % HISTORY];
    int delta = newest - oldest;
    if (delta > 30 && newest < 4200) {
        s_charge = CHARGE_CHARGING;
    } else if (newest >= 4150 && delta > -20 && delta < 20) {
        s_charge = CHARGE_FULL;
    } else {
        s_charge = CHARGE_DISCHARGING;
    }
}

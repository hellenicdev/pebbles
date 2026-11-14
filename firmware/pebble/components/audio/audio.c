#include "audio.h"

#include <math.h>
#include "pins.h"
#include "power.h"
#include "quiet.h"
#include "driver/i2s_std.h"
#include "esp_log.h"

/*
 * NS4168 CTRL is tied to VBAT_SW, which selects the right I2S channel.
 * Send the chime on the right slot and silence on the left. No microphone.
 */

static const char *TAG = "audio";
static i2s_chan_handle_t s_tx;

esp_err_t audio_init(void)
{
    power_periph_on();
    i2s_chan_config_t chan = I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);
    esp_err_t err = i2s_new_channel(&chan, &s_tx, NULL);
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "i2s channel: %s", esp_err_to_name(err));
        return err;
    }
    i2s_std_config_t std = {
        .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(22050),
        .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(I2S_DATA_BIT_WIDTH_16BIT, I2S_SLOT_MODE_STEREO),
        .gpio_cfg = {
            .mclk = I2S_GPIO_UNUSED,
            .bclk = PIN_I2S_BCLK,
            .ws = PIN_I2S_LRCLK,
            .dout = PIN_I2S_DOUT,
            .din = I2S_GPIO_UNUSED,
        },
    };
    err = i2s_channel_init_std_mode(s_tx, &std);
    if (err != ESP_OK) {
        return err;
    }
    return i2s_channel_enable(s_tx);
}

void audio_chime(void)
{
    if (!s_tx || quiet_hours_active()) {
        return;
    }
    enum { FRAMES = 256 };
    int16_t buf[FRAMES * 2];
    float phase = 0;
    const float step = 2.0f * 3.1415926f * 880.0f / 22050.0f;
    for (int chunk = 0; chunk < 8; chunk++) {
        for (int i = 0; i < FRAMES; i++) {
            int env = 255 - ((chunk * FRAMES + i) * 255) / (8 * FRAMES);
            int16_t sample = (int16_t)(sinf(phase) * 2000.0f * env / 255.0f);
            buf[i * 2] = 0;
            buf[i * 2 + 1] = sample;
            phase += step;
        }
        size_t wrote = 0;
        i2s_channel_write(s_tx, buf, sizeof(buf), &wrote, 200);
    }
}

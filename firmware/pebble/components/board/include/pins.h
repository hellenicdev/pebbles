#pragma once

/*
 * Pebble pin map, taken from the KiCad netlist (ESP32-C3-MINI-1-N4, U3).
 * Do not retarget these. Several nets are not what their test-pad labels suggest.
 *
 * TP8 is labelled U0RXD but the net is I2S_DOUT (GPIO20).
 * TP9 is labelled U0TXD but the net is EN_PERIPH (GPIO21).
 * GPIO18/19 are USB D-/D+ to pogo pads TP5/TP4, not application GPIOs.
 */

#define PIN_NTC_ADC      0   /* NTC_ADC. Valid only while HEAT_EN is high. */
#define PIN_VBAT_ADC     1   /* VBAT_ADC. Valid only while EN_PERIPH is high. */
#define PIN_I2C_SDA      2   /* I2C_SDA. External 4.7k to +3V3. Strap, stays high. */
#define PIN_TOUCH        3   /* TOUCH_OUT from TTP233H Q, active high. */
#define PIN_IRQ          4   /* IRQ_SHARED, external 10k to +3V3. */
#define PIN_I2C_SCL      5   /* I2C_SCL. External 4.7k to +3V3. */
#define PIN_I2S_BCLK     6
#define PIN_I2S_LRCLK    7
#define PIN_LED_DIN      8   /* LED_DIN into SN74LV1T34. Idle pull-up. Needs EN_PERIPH. */
#define PIN_BOOT         9   /* BOOT switch to GND. Do not drive. */
#define PIN_HEAT_EN      10  /* HEAT_EN, MOSFET gate. External 100k pulldown. */
#define PIN_USB_DN       18
#define PIN_USB_DP       19
#define PIN_I2S_DOUT     20  /* NS4168 SDATA. Mono data; the amp plays the right slot. */
#define PIN_EN_PERIPH    21  /* TPS22916 ON. External 100k pulldown, off at boot. */

#define LED_COUNT            8
#define LED_CHAIN_CENTER     4  /* D5, the fifth SK6805. Position only; same data pin. */

#define I2C_ADDR_XL9535      0x20
#define I2C_ADDR_LIS2DW12    0x19
#define I2C_ADDR_DRV2605L    0x5A
#define I2C_ADDR_NT3H2111    0x55

#define NTC_FIXED_OHMS       10000
#define NTC_R0_OHMS          10000
#define NTC_BETA             3380
#define VBAT_DIVIDER_NUM     2
#define VBAT_DIVIDER_DEN     1

#include "ch32fun.h"
#include "pins.h"

/*
 * Stand v2. MODE (PC2) cycles the LED-ring pattern. QI (PC3) toggles QI_EN
 * (PC4), which is the only enable for 5 V to the external Qi transmitter.
 * D2 (PC1) is on while that enable is on. The ring data pin is PC5, not the
 * SPI MOSI pin the stock ch32fun demo uses, so the bits are sent directly.
 */

static uint8_t s_mode;
static uint8_t s_qi;
static uint16_t s_frame;

static void pin_high(uint32_t pin)
{
    funDigitalWrite(pin, FUN_HIGH);
}

static void pin_low(uint32_t pin)
{
    funDigitalWrite(pin, FUN_LOW);
}

static void ws_bit(int one)
{
    GPIOC->BSHR = 1 << 5;
    if (one) {
        ADD_N_NOPS(20);
        GPIOC->BSHR = 1 << (16 + 5);
        ADD_N_NOPS(8);
    } else {
        ADD_N_NOPS(6);
        GPIOC->BSHR = 1 << (16 + 5);
        ADD_N_NOPS(16);
    }
}

static void ws_show(const uint8_t *grb, int leds)
{
    __asm__ volatile("csrci mstatus, 8");
    for (int i = 0; i < leds * 3; i++) {
        for (int bit = 7; bit >= 0; bit--) {
            ws_bit((grb[i] >> bit) & 1);
        }
    }
    __asm__ volatile("csrsi mstatus, 8");
    Delay_Us(80);
}

static void render(void)
{
    uint8_t px[DOCK_RING_LEDS * 3];
    for (int i = 0; i < DOCK_RING_LEDS * 3; i++) {
        px[i] = 0;
    }
    uint8_t level = (uint8_t)(8 + (s_frame & 31));
    if ((s_frame & 63) >= 32) {
        level = (uint8_t)(40 - (s_frame & 31));
    }
    for (int i = 0; i < DOCK_RING_LEDS; i++) {
        uint8_t g = 0, r = 0, b = 0;
        if (s_mode == 1) {
            r = 24;
            g = 10;
        } else if (s_mode == 2) {
            r = level;
            g = level;
            b = (uint8_t)(level / 2);
        } else if (s_mode == 3) {
            if (i == (int)((s_frame / 4) % DOCK_RING_LEDS)) {
                r = 30;
                g = 16;
            }
        } else if (s_mode == 4) {
            r = 18;
            g = 18;
            b = 18;
        }
        px[i * 3] = g;
        px[i * 3 + 1] = r;
        px[i * 3 + 2] = b;
    }
    ws_show(px, DOCK_RING_LEDS);
}

static void apply_qi(void)
{
    if (s_qi) {
        pin_high(DOCK_QI_EN);
        pin_high(DOCK_LED_STATUS);
    } else {
        pin_low(DOCK_QI_EN);
        pin_low(DOCK_LED_STATUS);
    }
}

int main(void)
{
    SystemInit();
    funGpioInitAll();

    funPinMode(DOCK_LED_STATUS, GPIO_Speed_10MHz | GPIO_CNF_OUT_PP);
    funPinMode(DOCK_QI_EN, GPIO_Speed_10MHz | GPIO_CNF_OUT_PP);
    funPinMode(DOCK_LED_DIN, GPIO_Speed_10MHz | GPIO_CNF_OUT_PP);
    funPinMode(DOCK_BTN_MODE, GPIO_CNF_IN_PUPD);
    funPinMode(DOCK_BTN_QI, GPIO_CNF_IN_PUPD);
    pin_high(DOCK_BTN_MODE);
    pin_high(DOCK_BTN_QI);
    pin_low(DOCK_LED_DIN);
    apply_qi();

    uint8_t mode_level = 1;
    uint8_t qi_level = 1;
    uint8_t mode_count = 0;
    uint8_t qi_count = 0;

    while (1) {
        uint8_t mode_now = (uint8_t)funDigitalRead(DOCK_BTN_MODE);
        uint8_t qi_now = (uint8_t)funDigitalRead(DOCK_BTN_QI);
        if (mode_now == mode_level) {
            mode_count = 0;
        } else if (++mode_count >= 3) {
            mode_level = mode_now;
            mode_count = 0;
            if (mode_now == 0) {
                s_mode = (uint8_t)((s_mode + 1) % 5);
            }
        }
        if (qi_now == qi_level) {
            qi_count = 0;
        } else if (++qi_count >= 3) {
            qi_level = qi_now;
            qi_count = 0;
            if (qi_now == 0) {
                s_qi ^= 1;
                apply_qi();
            }
        }
        render();
        s_frame++;
        Delay_Ms(16);
    }
}

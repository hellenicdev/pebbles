#pragma once

/*
 * Dock / stand v2, CH32V003F4P6. From the KiCad netlist.
 * PD1 (SWIO) and PD7 (NRST) stay with the WCH-LinkE header. Do not reuse them.
 * PA1, PA2, PC0, PC6, PC7, PD0, PD2-PD6 have no nets.
 */

#define DOCK_LED_STATUS PC1 /* D2 green, active high through 1k */
#define DOCK_BTN_MODE   PC2 /* to GND, enable internal pull-up */
#define DOCK_BTN_QI     PC3 /* to GND, enable internal pull-up */
#define DOCK_QI_EN      PC4 /* high turns 5 V on to the Qi module */
#define DOCK_LED_DIN    PC5 /* WS2812 data, level-shifted to 5 V */

/*
 * J3 does not say how many LEDs are on the ring. 12 is a placeholder so the
 * modes have something to draw; correct it when the ring is chosen.
 */
#define DOCK_RING_LEDS 12

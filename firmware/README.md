# Firmware

Pebble firmware is ESP-IDF 5.x for the ESP32-C3-MINI-1-N4. Dock firmware is [ch32v003fun](https://github.com/cnlohr/ch32v003fun) for the CH32V003F4P6. Pin numbers come from the KiCad netlist and live in `pebble/components/board/include/pins.h` and `dock/pins.h`.

`pebble/keys/dev_rsa_signing_key.pem` is a development RSA-3072 key so CI can sign images. Replace it before any device leaves the bench. It is not a production secret, and secure-boot fuses are not burned.

## Pebble: native USB on the pogo pads

There is no USB-C socket. USB D+ is GPIO19 on TP4, USB D− is GPIO18 on TP5. Logs use that USB Serial/JTAG CDC. GPIO20 and GPIO21 are I2S data and the peripheral load switch, not a UART, even though the pads are labelled U0RXD and U0TXD.

1. Connect the cell. The LDO is powered from the battery.
2. Seat the pogo fixture on TP4, TP5, and GND.
3. Hold SW2 (BOOT, GPIO9) and press SW1 (EN) so the ROM starts the download.
4. From `firmware/pebble`, with ESP-IDF v5.4 exported:

```sh
idf.py build
idf.py -p /dev/ttyACM0 flash monitor
```

Hold the touch pad for about two seconds at boot to run the factory exercise (LEDs, haptic, chime, a short heater pulse, NTC, battery).

## Dock: WCH-LinkE over SDI

Header J4 is the WCH-SDI port:

| Pin | Signal |
| --- | --- |
| 1 | +3V3 |
| 2 | GND |
| 3 | SWIO (PD1) |
| 4 | NRST (PD7) |

PD1 and PD7 are left to the debug probe. The firmware does not reuse them.

```sh
git clone --depth 1 https://github.com/cnlohr/ch32v003fun.git
cd firmware/dock
make CH32FUN=/path/to/ch32v003fun/ch32fun
make CH32FUN=/path/to/ch32v003fun/ch32fun flash

On Ubuntu the toolchain package does not include C headers. Install `picolibc-riscv64-unknown-elf` as well; the Makefile picks those headers up.
```

`make` builds `dock.bin`. `make flash` uses minichlink from that checkout. MODE (PC2) cycles the ring. The charge button (PC3) toggles 5 V to the Qi module (PC4). The green LED (PC1) is on while that rail is enabled.

## What is still a stub

BLE provisioning, the WebSocket to `wss://api.hellenicdev.eu/v1/pebble`, and the OTA download itself. Signed-image verification, rollback, and the "only while VBAT is rising" rule are real. See the comments marked TODO.

## Pin-map ambiguities

- U1 `~CHG` is unconnected. Charging is inferred from VBAT_SW rising. A high flat voltage is not treated as "on the dock", because a rested full cell looks the same. OTA runs only while the voltage is rising.
- The heater film's resistance is not in the schematic. The 42 °C cap is a firmware limit. There is no hardware cut-off.
- The NTC divider is powered by HEAT_EN, so the film temperature is only readable while GPIO10 is high.
- The battery divider sits on VBAT_SW, so the reading exists only while GPIO21 has the load switch on.
- J3 on the dock does not say how many LEDs are in the ring. The firmware draws 12 (`DOCK_RING_LEDS`) until that is known.
- D5 is only the fifth LED in the pebble chain (index 4). It is not a separate signal.
- XL9535 is on the I2C bus at 0x20 with every IO unconnected. NFC field-detect and the accelerometer interrupt share GPIO4; the accelerometer interrupt is not enabled yet.

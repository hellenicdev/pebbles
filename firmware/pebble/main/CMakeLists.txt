idf_component_register(SRCS "app_main.c"
                       INCLUDE_DIRS "."
                       REQUIRES board bus power heater haptics leds audio touch quiet
                                touch_queue motion radio prov link factory ota_plan nvs_flash esp_timer)

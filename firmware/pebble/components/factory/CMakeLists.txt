idf_component_register(SRCS "factory.c" INCLUDE_DIRS "include"
  REQUIRES board bus power heater haptics leds audio touch quiet touch_queue motion driver)

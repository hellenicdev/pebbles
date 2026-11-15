idf_component_register(SRCS "prov.c" INCLUDE_DIRS "include"
                       REQUIRES bt nvs_flash json radio quiet leds)

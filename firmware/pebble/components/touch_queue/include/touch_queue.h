#pragma once

#include <stdbool.h>
#include <stdint.h>

typedef struct {
    uint8_t kind;   /* 1 tap, 2 hug, 3 got-it */
    uint8_t target; /* 0..4, the other Pebbles in the group */
    uint32_t unix_s;
} queued_touch_t;

void touch_queue_init(void);
bool touch_queue_push(uint8_t kind, uint8_t target);
bool touch_queue_pop(queued_touch_t *out);
int touch_queue_count(void);

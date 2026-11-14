#include "touch_queue.h"

#include <string.h>
#include <time.h>
#include "nvs.h"

#define QUEUE_MAX 16

static queued_touch_t s_items[QUEUE_MAX];
static int s_count;

static void persist(void)
{
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READWRITE, &nvs) != ESP_OK) {
        return;
    }
    if (s_count == 0) {
        nvs_erase_key(nvs, "queue");
    } else {
        nvs_set_blob(nvs, "queue", s_items, sizeof(queued_touch_t) * (size_t)s_count);
    }
    nvs_set_u8(nvs, "qcount", (uint8_t)s_count);
    nvs_commit(nvs);
    nvs_close(nvs);
}

void touch_queue_init(void)
{
    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READONLY, &nvs) != ESP_OK) {
        return;
    }
    uint8_t count = 0;
    nvs_get_u8(nvs, "qcount", &count);
    if (count > QUEUE_MAX) {
        count = QUEUE_MAX;
    }
    size_t len = sizeof(queued_touch_t) * count;
    if (count && nvs_get_blob(nvs, "queue", s_items, &len) == ESP_OK) {
        s_count = (int)(len / sizeof(queued_touch_t));
    }
    nvs_close(nvs);
}

bool touch_queue_push(uint8_t kind, uint8_t target)
{
    if (s_count == QUEUE_MAX) {
        memmove(&s_items[0], &s_items[1], sizeof(queued_touch_t) * (QUEUE_MAX - 1));
        s_count = QUEUE_MAX - 1;
    }
    s_items[s_count].kind = kind;
    s_items[s_count].target = target;
    s_items[s_count].unix_s = (uint32_t)time(NULL);
    s_count++;
    persist();
    return true;
}

bool touch_queue_pop(queued_touch_t *out)
{
    if (s_count == 0) {
        return false;
    }
    *out = s_items[0];
    memmove(&s_items[0], &s_items[1], sizeof(queued_touch_t) * (s_count - 1));
    s_count--;
    persist();
    return true;
}

int touch_queue_count(void)
{
    return s_count;
}

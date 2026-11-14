#include "link.h"

#include "esp_log.h"

static const char *TAG = "link";

void link_init(void)
{
    /* TODO: open LINK_URI once the protocol and the server exist. */
    ESP_LOGI(TAG, "stub client for %s", LINK_URI);
}

esp_err_t link_send(const queued_touch_t *event)
{
    (void)event;
    /* TODO: write one encrypted touch frame. Nothing is sent today. */
    return ESP_ERR_NOT_SUPPORTED;
}

void link_flush(void)
{
    queued_touch_t ev;
    while (touch_queue_count() > 0) {
        if (!touch_queue_pop(&ev)) {
            break;
        }
        if (link_send(&ev) != ESP_OK) {
            touch_queue_push(ev.kind, ev.target);
            break;
        }
    }
}

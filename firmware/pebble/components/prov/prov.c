#include "prov.h"

#include <stdio.h>
#include <string.h>
#include "cJSON.h"
#include "nvs.h"
#include "radio.h"
#include "quiet.h"
#include "leds.h"
#include "esp_log.h"
#include "nimble/nimble_port.h"
#include "nimble/nimble_port_freertos.h"
#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "host/util/util.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

static const char *TAG = "prov";

static const ble_uuid128_t s_svc =
    BLE_UUID128_INIT(0x80, 0x7b, 0x6a, 0x5f, 0x4e, 0x0d, 0x31, 0x9c,
                     0x7f, 0x4a, 0x2d, 0x6e, 0x10, 0x8c, 0x1a, 0x5b);
static const ble_uuid128_t s_write =
    BLE_UUID128_INIT(0x80, 0x7b, 0x6a, 0x5f, 0x4e, 0x0d, 0x31, 0x9c,
                     0x7f, 0x4a, 0x2d, 0x6e, 0x11, 0x8c, 0x1a, 0x5b);
static const ble_uuid128_t s_status_uuid =
    BLE_UUID128_INIT(0x80, 0x7b, 0x6a, 0x5f, 0x4e, 0x0d, 0x31, 0x9c,
                     0x7f, 0x4a, 0x2d, 0x6e, 0x12, 0x8c, 0x1a, 0x5b);

static char s_status[8] = "wait";
static uint16_t s_status_handle;
static uint8_t s_addr_type;

static void copy_str(char *dst, size_t dst_len, const cJSON *item)
{
    memset(dst, 0, dst_len);
    if (!cJSON_IsString(item) || !item->valuestring) {
        return;
    }
    strncpy(dst, item->valuestring, dst_len - 1);
}

static int apply_payload(const char *json, int len)
{
    cJSON *root = cJSON_ParseWithLength(json, len);
    if (!root) {
        return -1;
    }
    cJSON *version = cJSON_GetObjectItem(root, "v");
    cJSON *rgb = cJSON_GetObjectItem(root, "rgb");
    cJSON *quiet = cJSON_GetObjectItem(root, "quiet");
    cJSON *names = cJSON_GetObjectItem(root, "names");
    cJSON *wifi = cJSON_GetObjectItem(root, "wifi");
    if (!cJSON_IsNumber(version) || version->valueint != 1 ||
        !cJSON_IsArray(rgb) || cJSON_GetArraySize(rgb) != 3 ||
        !cJSON_IsArray(quiet) || cJSON_GetArraySize(quiet) != 2) {
        cJSON_Delete(root);
        return -1;
    }

    uint8_t colour[3];
    for (int i = 0; i < 3; i++) {
        cJSON *part = cJSON_GetArrayItem(rgb, i);
        if (!cJSON_IsNumber(part) || part->valueint < 0 || part->valueint > 255) {
            cJSON_Delete(root);
            return -1;
        }
        colour[i] = (uint8_t)part->valueint;
    }
    int q0 = cJSON_GetArrayItem(quiet, 0)->valueint;
    int q1 = cJSON_GetArrayItem(quiet, 1)->valueint;
    if (q0 < 0 || q0 > 23 || q1 < 0 || q1 > 23) {
        cJSON_Delete(root);
        return -1;
    }

    char group[5][33];
    memset(group, 0, sizeof(group));
    int name_count = names && cJSON_IsArray(names) ? cJSON_GetArraySize(names) : 0;
    if (name_count > 5) {
        name_count = 5;
    }
    for (int i = 0; i < name_count; i++) {
        copy_str(group[i], sizeof(group[i]), cJSON_GetArrayItem(names, i));
    }

    radio_network_t nets[RADIO_WIFI_MAX];
    memset(nets, 0, sizeof(nets));
    int net_count = wifi && cJSON_IsArray(wifi) ? cJSON_GetArraySize(wifi) : 0;
    if (net_count > RADIO_WIFI_MAX) {
        cJSON_Delete(root);
        return -1;
    }
    for (int i = 0; i < net_count; i++) {
        cJSON *item = cJSON_GetArrayItem(wifi, i);
        copy_str(nets[i].ssid, sizeof(nets[i].ssid), cJSON_GetObjectItem(item, "ssid"));
        copy_str(nets[i].pass, sizeof(nets[i].pass), cJSON_GetObjectItem(item, "pass"));
        if (!nets[i].ssid[0]) {
            cJSON_Delete(root);
            return -1;
        }
    }
    cJSON_Delete(root);

    nvs_handle_t nvs;
    if (nvs_open("pebble", NVS_READWRITE, &nvs) != ESP_OK) {
        return -1;
    }
    nvs_set_blob(nvs, "colour", colour, sizeof(colour));
    nvs_set_blob(nvs, "names", group, sizeof(group));
    nvs_commit(nvs);
    nvs_close(nvs);

    leds_set_colour(colour[0], colour[1], colour[2]);
    quiet_set_window((uint8_t)q0, (uint8_t)q1);
    if (radio_replace_networks(nets, net_count) != ESP_OK) {
        return -1;
    }
    ESP_LOGI(TAG, "paired colour %u,%u,%u quiet %d-%d networks %d",
             colour[0], colour[1], colour[2], q0, q1, net_count);
    return 0;
}

static int access_cb(uint16_t conn, uint16_t attr, struct ble_gatt_access_ctxt *ctxt, void *arg)
{
    (void)conn;
    (void)attr;
    (void)arg;
    if (ctxt->op == BLE_GATT_ACCESS_OP_READ_CHR) {
        int rc = os_mbuf_append(ctxt->om, s_status, strlen(s_status));
        return rc == 0 ? 0 : BLE_ATT_ERR_INSUFFICIENT_RES;
    }
    if (ctxt->op != BLE_GATT_ACCESS_OP_WRITE_CHR) {
        return BLE_ATT_ERR_UNLIKELY;
    }
    uint16_t len = OS_MBUF_PKTLEN(ctxt->om);
    if (len == 0 || len > 768) {
        snprintf(s_status, sizeof(s_status), "err");
        return BLE_ATT_ERR_INVALID_ATTR_VALUE_LEN;
    }
    char buf[769];
    uint16_t got = 0;
    if (ble_hs_mbuf_to_flat(ctxt->om, buf, sizeof(buf) - 1, &got) != 0) {
        snprintf(s_status, sizeof(s_status), "err");
        return BLE_ATT_ERR_UNLIKELY;
    }
    buf[got] = '\0';
    if (apply_payload(buf, (int)got) != 0) {
        snprintf(s_status, sizeof(s_status), "err");
        return BLE_ATT_ERR_UNLIKELY;
    }
    snprintf(s_status, sizeof(s_status), "ok");
    if (s_status_handle) {
        ble_gatts_chr_updated(s_status_handle);
    }
    return 0;
}

static const struct ble_gatt_chr_def s_chrs[] = {
    {
        .uuid = &s_write.u,
        .access_cb = access_cb,
        .flags = BLE_GATT_CHR_F_WRITE,
    },
    {
        .uuid = &s_status_uuid.u,
        .access_cb = access_cb,
        .flags = BLE_GATT_CHR_F_READ | BLE_GATT_CHR_F_NOTIFY,
        .val_handle = &s_status_handle,
    },
    {0},
};

static const struct ble_gatt_svc_def s_svcs[] = {
    {
        .type = BLE_GATT_SVC_TYPE_PRIMARY,
        .uuid = &s_svc.u,
        .characteristics = s_chrs,
    },
    {0},
};

static int gap_event(struct ble_gap_event *event, void *arg);

static void advertise(void)
{
    struct ble_hs_adv_fields fields = {0};
    fields.flags = BLE_HS_ADV_F_DISC_GEN | BLE_HS_ADV_F_BREDR_UNSUP;
    fields.uuids128 = (ble_uuid128_t *)&s_svc;
    fields.num_uuids128 = 1;
    fields.uuids128_is_complete = 1;
    ble_gap_adv_set_fields(&fields);

    struct ble_hs_adv_fields response = {0};
    const char *name = "Pebble";
    response.name = (uint8_t *)name;
    response.name_len = 6;
    response.name_is_complete = 1;
    ble_gap_adv_rsp_set_fields(&response);

    struct ble_gap_adv_params params = {0};
    params.conn_mode = BLE_GAP_CONN_MODE_UND;
    params.disc_mode = BLE_GAP_DISC_MODE_GEN;
    ble_gap_adv_start(s_addr_type, NULL, BLE_HS_FOREVER, &params, gap_event, NULL);
}

static int gap_event(struct ble_gap_event *event, void *arg)
{
    (void)arg;
    if (event->type == BLE_GAP_EVENT_DISCONNECT || event->type == BLE_GAP_EVENT_ADV_COMPLETE) {
        advertise();
    }
    return 0;
}

static void on_sync(void)
{
    ble_hs_id_infer_auto(0, &s_addr_type);
    advertise();
}

static void host_task(void *param)
{
    (void)param;
    nimble_port_run();
    nimble_port_freertos_deinit();
}

esp_err_t prov_start(void)
{
    esp_err_t err = nimble_port_init();
    if (err != ESP_OK) {
        ESP_LOGE(TAG, "nimble init: %s", esp_err_to_name(err));
        return err;
    }
    ble_hs_cfg.sync_cb = on_sync;
    ble_svc_gap_init();
    ble_svc_gatt_init();
    int rc = ble_gatts_count_cfg(s_svcs);
    if (rc == 0) {
        rc = ble_gatts_add_svcs(s_svcs);
    }
    if (rc != 0) {
        ESP_LOGE(TAG, "gatt setup failed");
        return ESP_FAIL;
    }
    ble_svc_gap_device_name_set("Pebble");
    nimble_port_freertos_init(host_task);
    ESP_LOGI(TAG, "advertising Pebble");
    return ESP_OK;
}

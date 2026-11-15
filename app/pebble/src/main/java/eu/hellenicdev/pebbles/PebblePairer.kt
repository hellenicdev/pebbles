package eu.hellenicdev.pebbles

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Writes the version-1 pairing JSON to a Pebble.
 * Service 5b1a8c10-6e2d-4a7f-9c31-0d4e5f6a7b80
 * Write   5b1a8c11-6e2d-4a7f-9c31-0d4e5f6a7b80
 * Status  5b1a8c12-6e2d-4a7f-9c31-0d4e5f6a7b80
 */
class PebblePairer(
    context: Context,
    private val onStatus: (String) -> Unit,
) {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var payload: ByteArray = ByteArray(0)
    private var discovering = false
    private var wrote = false

    fun start(settings: PebbleSettings) {
        val manager = app.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null) {
            onStatus("This phone has no Bluetooth.")
            return
        }
        if (!adapter.isEnabled) {
            onStatus("Turn Bluetooth on, then pair again.")
            return
        }
        payload = settings.toPairingJson()
        discovering = false
        wrote = false
        gatt?.close()
        gatt = null
        stopScan(adapter)
        onStatus("Looking for a Pebble…")
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE)).build()
        val settingsScan = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        adapter.bluetoothLeScanner?.startScan(listOf(filter), settingsScan, scan)
            ?: onStatus("Bluetooth scanning is unavailable.")
        main.postDelayed({
            stopScan(adapter)
            if (gatt == null) onStatus("No Pebble nearby. Settings stay on this phone.")
        }, 12_000)
    }

    fun close() {
        gatt?.close()
        gatt = null
    }

    private fun stopScan(adapter: BluetoothAdapter) {
        adapter.bluetoothLeScanner?.stopScan(scan)
    }

    private val scan = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
            stopScan(adapter)
            main.removeCallbacksAndMessages(null)
            onStatus("Found a Pebble. Connecting…")
            gatt?.close()
            gatt = result.device.connectGatt(app, false, callback)
        }
    }

    @SuppressLint("MissingPermission")
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt.requestMtu(247)
                main.postDelayed({ discover(gatt) }, 800)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                onStatus("The Pebble disconnected.")
                gatt.close()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            discover(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (wrote) return
            wrote = true
            val chr = gatt.getService(SERVICE)?.getCharacteristic(WRITE)
            if (chr == null) {
                onStatus("This device is not a Pebble.")
                gatt.disconnect()
                return
            }
            write(gatt, chr)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onStatus("The Pebble refused the settings.")
                return
            }
            val statusChr = gatt.getService(SERVICE)?.getCharacteristic(STATUS)
            if (statusChr == null || !gatt.readCharacteristic(statusChr)) {
                onStatus("Settings sent.")
            }
        }

        @Deprecated("Used on Android 12 and older")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            report(status, characteristic.value)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            report(status, value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun discover(gatt: BluetoothGatt) {
        if (discovering) return
        discovering = true
        gatt.discoverServices()
    }

    @SuppressLint("MissingPermission")
    private fun write(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        val ok = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = payload
            gatt.writeCharacteristic(characteristic)
        }
        if (!ok) onStatus("Could not send the settings.")
    }

    private fun report(status: Int, value: ByteArray?) {
        val text = value?.toString(Charsets.UTF_8) ?: ""
        onStatus(
            when {
                status != BluetoothGatt.GATT_SUCCESS -> "The Pebble did not confirm."
                text == "ok" -> "Paired. The Pebble stored these settings."
                else -> "The Pebble could not store these settings."
            },
        )
    }

    companion object {
        val SERVICE: UUID = UUID.fromString("5b1a8c10-6e2d-4a7f-9c31-0d4e5f6a7b80")
        val WRITE: UUID = UUID.fromString("5b1a8c11-6e2d-4a7f-9c31-0d4e5f6a7b80")
        val STATUS: UUID = UUID.fromString("5b1a8c12-6e2d-4a7f-9c31-0d4e5f6a7b80")
    }
}

private fun PebbleSettings.toPairingJson(): ByteArray {
    val colourBits = colour and 0xffffffffL
    val body = JSONObject()
        .put("v", 1)
        .put("colour", colourName)
        .put(
            "rgb",
            JSONArray()
                .put(((colourBits shr 16) and 0xff).toInt())
                .put(((colourBits shr 8) and 0xff).toInt())
                .put((colourBits and 0xff).toInt()),
        )
        .put("quiet", JSONArray().put(quietStart).put(quietEnd))
    val people = JSONArray()
    names.take(5).forEach { people.put(it.take(32)) }
    body.put("names", people)
    val networksJson = JSONArray()
    networks.take(5).forEach { network ->
        networksJson.put(
            JSONObject()
                .put("ssid", network.ssid.take(32))
                .put("pass", network.password.take(64)),
        )
    }
    body.put("wifi", networksJson)
    return body.toString().toByteArray(Charsets.UTF_8)
}

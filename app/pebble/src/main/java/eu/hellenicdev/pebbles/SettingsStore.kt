package eu.hellenicdev.pebbles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WifiNetwork(val ssid: String, val password: String)

data class PebbleSettings(
    val colourName: String,
    val colour: Long,
    val names: List<String>,
    val quietStart: Int,
    val quietEnd: Int,
    val networks: List<WifiNetwork>,
)

object Colours {
    val choices = listOf(
        "Amber" to 0xFFE08A45,
        "Rose" to 0xFFE08B78,
        "Sage" to 0xFF6F9A62,
        "Dusk" to 0xFF7F8CAE,
    )
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("pebbles", Context.MODE_PRIVATE)

    fun load(): PebbleSettings {
        val colourName = prefs.getString("colour_name", "Amber") ?: "Amber"
        val colour = Colours.choices.firstOrNull { it.first == colourName }?.second ?: Colours.choices[0].second
        val names = MutableList(5) { index ->
            prefs.getString("name_$index", defaultNames[index]) ?: defaultNames[index]
        }
        val networks = mutableListOf<WifiNetwork>()
        val raw = prefs.getString("wifi", "[]") ?: "[]"
        val array = JSONArray(raw)
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            networks.add(WifiNetwork(item.getString("ssid"), item.optString("password")))
        }
        return PebbleSettings(
            colourName = colourName,
            colour = colour,
            names = names,
            quietStart = prefs.getInt("quiet_start", 22),
            quietEnd = prefs.getInt("quiet_end", 7),
            networks = networks,
        )
    }

    fun save(settings: PebbleSettings) {
        val wifi = JSONArray()
        settings.networks.take(5).forEach { network ->
            wifi.put(JSONObject().put("ssid", network.ssid).put("password", network.password))
        }
        prefs.edit()
            .putString("colour_name", settings.colourName)
            .putInt("quiet_start", settings.quietStart)
            .putInt("quiet_end", settings.quietEnd)
            .putString("wifi", wifi.toString())
            .apply {
                settings.names.take(5).forEachIndexed { index, name -> putString("name_$index", name) }
            }
            .apply()
    }

    private companion object {
        val defaultNames = listOf("Sam", "Noor", "Alex", "Jo", "Ren")
    }
}

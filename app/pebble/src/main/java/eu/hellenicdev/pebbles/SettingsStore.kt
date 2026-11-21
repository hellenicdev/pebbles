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
        val names = loadNames()
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
        val people = settings.names.take(5)
        val editor = prefs.edit()
            .putString("colour_name", settings.colourName)
            .putInt("quiet_start", settings.quietStart)
            .putInt("quiet_end", settings.quietEnd)
            .putString("wifi", wifi.toString())
            .putInt("name_count", people.size)
        for (index in 0 until 5) {
            if (index < people.size) editor.putString("name_$index", people[index])
            else editor.remove("name_$index")
        }
        editor.apply()
    }

    private fun loadNames(): List<String> {
        if (prefs.contains("name_count")) {
            val count = prefs.getInt("name_count", 0).coerceIn(0, 5)
            return List(count) { index -> prefs.getString("name_$index", "") ?: "" }
        }
        val legacy = (0 until 5).map { index -> prefs.getString("name_$index", null) }
        if (legacy.all { it == null }) return emptyList()
        return legacy.map { it ?: "" }.filter { it.isNotBlank() }
    }

    fun introSeen(): Boolean = prefs.getBoolean("intro_seen", false)

    fun markIntroSeen() {
        prefs.edit().putBoolean("intro_seen", true).apply()
    }

    fun setupDone(): Boolean = prefs.getBoolean("setup_done", false)

    fun markSetupDone() {
        prefs.edit().putBoolean("setup_done", true).apply()
    }

    fun crashReportsEnabled(): Boolean = prefs.getBoolean(CRASH_REPORTS, false)

    fun setCrashReportsEnabled(enabled: Boolean): Boolean {
        return prefs.edit().putBoolean(CRASH_REPORTS, enabled).commit()
    }

    private companion object {
        const val CRASH_REPORTS = "crash_reports"
    }
}

package eu.hellenicdev.pebbles

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

/**
 * First APK. Settings match the fields the firmware will accept over BLE
 * (up to five Wi-Fi networks, a sender colour, quiet hours, a group of five).
 * Nothing is sent to a Pebble yet. [PreviewLink] only updates the on-screen pebble.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(this)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if (dark) pebbleDark() else pebbleLight()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PebbleScreen(store.load()) { store.save(it) }
                }
            }
        }
    }
}

private class PreviewLink {
    var status: String = "Tap for “thinking of you”. Hold for a hug."
        private set

    fun tap(name: String, quiet: Boolean) {
        status = if (quiet) {
            "Quiet hours. $name's Pebble stays still."
        } else {
            "Thinking of $name. About 38°C."
        }
    }

    fun hug(name: String, quiet: Boolean) {
        status = if (quiet) {
            "Quiet hours. $name's Pebble stays still."
        } else {
            "A hug for $name. Warmth is capped at 42°C."
        }
    }
}

@Composable
private fun PebbleScreen(initial: PebbleSettings, onSave: (PebbleSettings) -> Unit) {
    var settings by remember { mutableStateOf(initial) }
    var status by remember { mutableStateOf("No Pebble is connected. This preview stays on the phone.") }
    var ssid by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val link = remember { PreviewLink() }
    val scroll = rememberScrollState()
    val who = settings.names.firstOrNull { it.isNotBlank() } ?: "them"
    val quiet = inQuietHours(settings.quietStart, settings.quietEnd)

    fun update(next: PebbleSettings) {
        settings = next
        onSave(next)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pebbles", style = MaterialTheme.typography.headlineLarge)
        Text(status)
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            drawCircle(color = Color(settings.colour), radius = 70f, center = center)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                link.tap(who, quiet)
                status = link.status
            }) { Text("Tap") }
            Button(onClick = {
                link.hug(who, quiet)
                status = link.status
            }) { Text("Hold") }
        }

        Text("Your colour", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Colours.choices.forEach { (name, value) ->
                FilterChip(
                    selected = settings.colourName == name,
                    onClick = { update(settings.copy(colourName = name, colour = value)) },
                    label = { Text(name) },
                )
            }
        }

        Text("Group of five", style = MaterialTheme.typography.titleMedium)
        settings.names.forEachIndexed { index, name ->
            OutlinedTextField(
                value = name,
                onValueChange = { value ->
                    val names = settings.names.toMutableList()
                    names[index] = value
                    update(settings.copy(names = names))
                },
                label = { Text("Person ${index + 1}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        Text("Quiet hours, UTC", style = MaterialTheme.typography.titleMedium)
        Text(if (quiet) "Quiet hours are on. No buzz and no chime." else "Quiet hours are off.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HourField("From", settings.quietStart) { update(settings.copy(quietStart = it)) }
            HourField("Until", settings.quietEnd) { update(settings.copy(quietEnd = it)) }
        }

        Text("Wi-Fi, up to five", style = MaterialTheme.typography.titleMedium)
        Text("Saved on this phone only. Pairing will send them to the Pebble later.")
        settings.networks.forEachIndexed { index, network ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(network.ssid, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val networks = settings.networks.toMutableList()
                    networks.removeAt(index)
                    update(settings.copy(networks = networks))
                }) { Text("Remove") }
            }
        }
        if (settings.networks.size < 5) {
            OutlinedTextField(
                value = ssid,
                onValueChange = { ssid = it },
                label = { Text("Network name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Button(
                onClick = {
                    if (ssid.isNotBlank()) {
                        update(settings.copy(networks = settings.networks + WifiNetwork(ssid.trim(), password)))
                        ssid = ""
                        password = ""
                    }
                },
                enabled = ssid.isNotBlank(),
            ) { Text("Save network") }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun HourField(label: String, value: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()?.let { hour -> if (hour in 0..23) onChange(hour) }
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(0.45f),
        singleLine = true,
    )
}

private fun inQuietHours(start: Int, end: Int): Boolean {
    if (start == end) return false
    val hour = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).get(java.util.Calendar.HOUR_OF_DAY)
    return if (start < end) hour in start until end else hour >= start || hour < end
}

private fun pebbleLight() = lightColorScheme(
    primary = Color(0xFF7C3A24),
    background = Color(0xFFF4EFE8),
    surface = Color(0xFFFFFAF4),
)

private fun pebbleDark() = darkColorScheme(
    primary = Color(0xFFF0B08A),
    background = Color(0xFF141210),
    surface = Color(0xFF221E1B),
)

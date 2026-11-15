package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private enum class Place(val label: String) {
    Home("Home"),
    People("People"),
    Pebble("Pebble"),
    Wifi("Wi-Fi"),
}

@Composable
fun AppShell(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    onChange: (PebbleSettings) -> Unit,
    onPair: () -> Unit,
) {
    var place by rememberSaveable { mutableStateOf(Place.Home) }
    val colour = pebbleColor(settings.colour)
    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SoftBackdrop(colour, motion, Modifier.matchParentSize())
            AnimatedContent(
                targetState = place,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(350)) },
                label = "place",
            ) { current ->
                when (current) {
                    Place.Home -> HomePlace(settings, status, motion)
                    Place.People -> PeoplePlace(settings, onChange)
                    Place.Pebble -> PebblePlace(settings, status, motion, onChange, onPair)
                    Place.Wifi -> WifiPlace(settings, onChange)
                }
            }
        }
        Surface(tonalElevation = 2.dp) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Place.entries.forEach { item ->
                    PlaceTab(item.label, item == place) { place = item }
                }
            }
        }
    }
}

@Composable
private fun RowScope.PlaceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val color by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        tween(450),
        label = "tab-colour",
    )
    TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
        Text(label, color = color)
    }
}

@Composable
private fun HomePlace(settings: PebbleSettings, status: String, motion: Boolean) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Your Pebble", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        BreathingPebble(
            colour = pebbleColor(settings.colour),
            scene = if (inQuietHours(settings.quietStart, settings.quietEnd)) PebbleScene.Quiet else PebbleScene.Rest,
            motion = motion,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        )
        Text(settings.colourName, style = MaterialTheme.typography.headlineMedium)
        Text(groupLine(settings.names), textAlign = TextAlign.Center)
        Text(quietLine(settings.quietStart, settings.quietEnd), textAlign = TextAlign.Center)
        Text(status, textAlign = TextAlign.Center)
        Text(
            "Touches stay on the pebble. This phone only keeps the settings.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun PeoplePlace(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("People", style = MaterialTheme.typography.headlineSmall)
        Text("Up to five people in this group.")
        settings.names.forEachIndexed { index, name ->
            OutlinedTextField(
                value = name,
                onValueChange = { value ->
                    val names = settings.names.toMutableList()
                    names[index] = value
                    onChange(settings.copy(names = names))
                },
                label = { Text("Person ${index + 1}") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PebblePlace(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    onChange: (PebbleSettings) -> Unit,
    onPair: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Pebble", style = MaterialTheme.typography.headlineSmall)
        BreathingPebble(
            colour = pebbleColor(settings.colour),
            scene = PebbleScene.Rest,
            motion = motion,
            modifier = Modifier.fillMaxWidth().height(140.dp),
        )
        Text("Colour")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Colours.choices.forEach { (name, value) ->
                FilterChip(
                    selected = settings.colourName == name,
                    onClick = { onChange(settings.copy(colourName = name, colour = value)) },
                    label = { Text(name) },
                )
            }
        }
        Text("Quiet hours")
        Text(quietLine(settings.quietStart, settings.quietEnd))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HourField("From", settings.quietStart, { onChange(settings.copy(quietStart = it)) })
            HourField("Until", settings.quietEnd, { onChange(settings.copy(quietEnd = it)) })
        }
        Text(status)
        Button(onClick = onPair) { Text("Pair") }
    }
}

@Composable
private fun WifiPlace(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Wi-Fi", style = MaterialTheme.typography.headlineSmall)
        Text("Up to five networks. Pairing sends them to the pebble, which joins the first one.")
        settings.networks.forEachIndexed { index, network ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(network.ssid, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val networks = settings.networks.toMutableList()
                    networks.removeAt(index)
                    onChange(settings.copy(networks = networks))
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
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = {
                    if (ssid.isNotBlank()) {
                        onChange(settings.copy(networks = settings.networks + WifiNetwork(ssid.trim(), password)))
                        ssid = ""
                        password = ""
                    }
                },
                enabled = ssid.isNotBlank(),
            ) { Text("Save network") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

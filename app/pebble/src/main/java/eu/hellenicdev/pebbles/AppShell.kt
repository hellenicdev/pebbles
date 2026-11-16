package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
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
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SoftBackdrop(colour, motion, Modifier.matchParentSize())
            AnimatedContent(
                targetState = place,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = { fadeIn(tween(320, easing = SmoothOut)) togetherWith fadeOut(tween(200)) },
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
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Place.entries.forEach { item ->
                PlaceTab(item.label, item == place) { place = item }
            }
        }
    }
}

@Composable
private fun RowScope.PlaceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val brand = PebblesBrand.current
    val scale by animateFloatAsState(if (selected) 1f else 0f, tween(280, easing = SmoothOut), label = "nav")
    Column(
        Modifier
            .weight(1f)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = Type.nav, color = if (selected) brand.ink else brand.inkSoft)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .size(width = 22.dp, height = 2.dp)
                .graphicsLayer { scaleX = scale }
                .background(brand.accent, CircleShape),
        )
    }
}

@Composable
private fun HomePlace(settings: PebbleSettings, status: String, motion: Boolean) {
    val brand = PebblesBrand.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pebbleHeight = (maxHeight * 0.34f).coerceIn(120.dp, 210.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("YOUR PEBBLE", style = Type.eyebrow, color = brand.accent)
            BreathingPebble(
                colour = pebbleColor(settings.colour),
                scene = if (inQuietHours(settings.quietStart, settings.quietEnd)) PebbleScene.Quiet else PebbleScene.Rest,
                motion = motion,
                modifier = Modifier.fillMaxWidth().height(pebbleHeight),
            )
            Text(settings.colourName, style = Type.display, color = brand.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(groupLine(settings.names), style = Type.body, color = brand.inkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(quietLine(settings.quietStart, settings.quietEnd), style = Type.body, color = brand.inkSoft, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Text(status, style = Type.body, color = brand.ink, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Touches stay on the pebble. This phone only keeps the settings.",
                style = Type.body,
                color = brand.inkSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PeoplePlace(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text("PEOPLE", style = Type.eyebrow, color = brand.accent)
        Spacer(Modifier.height(6.dp))
        Text("Your group", style = Type.title, color = brand.ink)
        Spacer(Modifier.height(8.dp))
        Text("Add as many as you need, up to five.", style = Type.body, color = brand.inkSoft)
        Spacer(Modifier.height(16.dp))
        PeopleEditor(settings.names) { onChange(settings.copy(names = it)) }
    }
}

@Composable
private fun PebblePlace(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    onChange: (PebbleSettings) -> Unit,
    onPair: () -> Unit,
) {
    val brand = PebblesBrand.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text("PEBBLE", style = Type.eyebrow, color = brand.accent)
        Spacer(Modifier.height(6.dp))
        Text("Colour and quiet", style = Type.title, color = brand.ink)
        BreathingPebble(
            colour = pebbleColor(settings.colour),
            scene = PebbleScene.Rest,
            motion = motion,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        ColourChoices(settings.colourName) { name, value ->
            onChange(settings.copy(colourName = name, colour = value))
        }
        Spacer(Modifier.height(18.dp))
        Text(quietLine(settings.quietStart, settings.quietEnd), style = Type.body, color = brand.inkSoft)
        Spacer(Modifier.height(10.dp))
        HourFields(
            start = settings.quietStart,
            end = settings.quietEnd,
            onStart = { onChange(settings.copy(quietStart = it)) },
            onEnd = { onChange(settings.copy(quietEnd = it)) },
        )
        Spacer(Modifier.height(16.dp))
        Text(status, style = Type.body, color = brand.ink)
        Spacer(Modifier.height(10.dp))
        BrandButton("Pair", onClick = onPair, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun WifiPlace(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Text("WI-FI", style = Type.eyebrow, color = brand.accent)
        Spacer(Modifier.height(6.dp))
        Text("Networks", style = Type.title, color = brand.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Up to five. Pairing sends them to the pebble, which joins the first one.",
            style = Type.body,
            color = brand.inkSoft,
        )
        Spacer(Modifier.height(12.dp))
        settings.networks.forEachIndexed { index, network ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(network.ssid, style = Type.body, color = brand.ink, modifier = Modifier.weight(1f))
                BrandQuiet("Remove", onClick = {
                    val networks = settings.networks.toMutableList()
                    networks.removeAt(index)
                    onChange(settings.copy(networks = networks))
                })
            }
        }
        if (settings.networks.size < 5) {
            Spacer(Modifier.height(8.dp))
            BrandField(ssid, { ssid = it }, "Network name")
            Spacer(Modifier.height(10.dp))
            BrandField(password, { password = it }, "Password", visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(12.dp))
            BrandButton(
                "Save network",
                enabled = ssid.isNotBlank(),
                onClick = {
                    onChange(settings.copy(networks = settings.networks + WifiNetwork(ssid.trim(), password)))
                    ssid = ""
                    password = ""
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

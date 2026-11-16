package eu.hellenicdev.pebbles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private val setupTitles = listOf(
    "Your colour" to "This is the glow they see.",
    "Your group" to "Add the people in this group. One is enough. Five is the most.",
    "Quiet hours" to "UTC. Between these hours it stays still.",
    "Wi-Fi" to "Up to five networks. It joins the first one when you pair.",
    "Pair" to "Keep the pebble nearby. This sends the settings across.",
)

@Composable
fun SetupScreen(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    onChange: (PebbleSettings) -> Unit,
    onPair: () -> Unit,
    onDone: () -> Unit,
) {
    val brand = PebblesBrand.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }
    val colour = pebbleColor(settings.colour)
    val scene = when (step) {
        2 -> PebbleScene.Quiet
        4 -> PebbleScene.Tap
        else -> PebbleScene.Rest
    }
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    Box(Modifier.fillMaxSize()) {
        SoftBackdrop(colour, motion, Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)) {
            Text("SET UP", style = Type.eyebrow, color = brand.accent)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val pebbleHeight = if (keyboardOpen) 0.dp else (maxHeight * 0.22f).coerceIn(88.dp, 132.dp)
                Column(Modifier.fillMaxSize()) {
                    if (pebbleHeight > 0.dp) {
                        BreathingPebble(
                            colour = colour,
                            scene = scene,
                            motion = motion,
                            modifier = Modifier.fillMaxWidth().height(pebbleHeight),
                        )
                    }
                    AnimatedContent(
                        targetState = step,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = {
                            (fadeIn(tween(360, easing = SmoothOut)) togetherWith fadeOut(tween(200))) using
                                SizeTransform(clip = false) { _, _ -> snap() }
                        },
                        label = "setup-step",
                    ) { index ->
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(top = 8.dp, bottom = 12.dp),
                        ) {
                            Text(setupTitles[index].first, style = Type.title, color = brand.ink)
                            Spacer(Modifier.height(8.dp))
                            Text(setupTitles[index].second, style = Type.body, color = brand.inkSoft)
                            Spacer(Modifier.height(16.dp))
                            when (index) {
                                0 -> ColourChoices(settings.colourName) { name, value ->
                                    onChange(settings.copy(colourName = name, colour = value))
                                }
                                1 -> PeopleEditor(settings.names) { onChange(settings.copy(names = it)) }
                                2 -> QuietStep(settings, onChange)
                                3 -> WifiStep(settings, onChange)
                                else -> PairStep(status, onPair)
                            }
                        }
                    }
                }
            }
            StepBar(step, setupTitles.size)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) BrandQuiet("Back", onClick = { step -= 1 })
                Spacer(Modifier.weight(1f))
                BrandButton(
                    text = if (step < setupTitles.lastIndex) "Continue" else "Done",
                    onClick = { if (step < setupTitles.lastIndex) step += 1 else onDone() },
                )
            }
        }
    }
}

@Composable
private fun QuietStep(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    Text(
        if (inQuietHours(settings.quietStart, settings.quietEnd)) {
            "Quiet hours would be on at this UTC hour."
        } else {
            "Quiet hours would be off at this UTC hour."
        },
        style = Type.body,
        color = brand.ink,
    )
    Spacer(Modifier.height(12.dp))
    HourFields(
        start = settings.quietStart,
        end = settings.quietEnd,
        onStart = { onChange(settings.copy(quietStart = it)) },
        onEnd = { onChange(settings.copy(quietEnd = it)) },
    )
}

@Composable
private fun WifiStep(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    val brand = PebblesBrand.current
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
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
        )
    }
    Spacer(Modifier.height(8.dp))
    Text("You can leave this empty and add a network later.", style = Type.body, color = brand.inkSoft)
}

@Composable
private fun PairStep(status: String, onPair: () -> Unit) {
    val brand = PebblesBrand.current
    Text(status, style = Type.body, color = brand.ink)
    Spacer(Modifier.height(12.dp))
    BrandButton("Pair", onClick = onPair)
    Spacer(Modifier.height(8.dp))
    Text("You can finish now and pair later from the Pebble screen.", style = Type.body, color = brand.inkSoft)
}

package eu.hellenicdev.pebbles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private val setupTitles = listOf(
    "Your colour" to "This is the glow they see.",
    "Your group" to "Up to five people share one pebble group.",
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
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step -= 1 }
    val colour = pebbleColor(settings.colour)
    val scene = when (step) {
        2 -> PebbleScene.Quiet
        4 -> PebbleScene.Tap
        else -> PebbleScene.Rest
    }
    Box(Modifier.fillMaxSize()) {
        SoftBackdrop(colour, motion, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text("Set up your Pebble", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            BreathingPebble(
                colour = colour,
                scene = scene,
                motion = motion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp),
            )
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    val forward = targetState > initialState
                    val dir = if (forward) 1 else -1
                    (fadeIn(tween(650)) + slideInHorizontally(tween(650)) { dir * it / 6 }) togetherWith
                        (fadeOut(tween(420)) + slideOutHorizontally(tween(420)) { -dir * it / 8 })
                },
                label = "setup-step",
            ) { index ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(setupTitles[index].first, style = MaterialTheme.typography.headlineSmall)
                    Text(setupTitles[index].second, style = MaterialTheme.typography.bodyLarge)
                    when (index) {
                        0 -> ColourStep(settings, onChange)
                        1 -> PeopleStep(settings, motion, onChange)
                        2 -> QuietStep(settings, onChange)
                        3 -> WifiStep(settings, onChange)
                        else -> PairStep(status, onPair)
                    }
                }
            }
            PageDots(step, setupTitles.size, Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) {
                    TextButton(onClick = { step -= 1 }) { Text("Back") }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = { if (step < setupTitles.lastIndex) step += 1 else onDone() }) {
                    Text(if (step < setupTitles.lastIndex) "Continue" else "Done")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColourStep(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Colours.choices.forEach { (name, value) ->
            FilterChip(
                selected = settings.colourName == name,
                onClick = { onChange(settings.copy(colourName = name, colour = value)) },
                label = { Text(name) },
            )
        }
    }
    Text(
        settings.colourName,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PeopleStep(settings: PebbleSettings, motion: Boolean, onChange: (PebbleSettings) -> Unit) {
    val shown = remember { mutableStateListOf(false, false, false, false, false) }
    LaunchedEffect(Unit) {
        if (!motion) {
            for (i in shown.indices) shown[i] = true
            return@LaunchedEffect
        }
        for (i in shown.indices) {
            delay(140)
            shown[i] = true
        }
    }
    settings.names.forEachIndexed { index, name ->
        AnimatedVisibility(
            visible = shown[index],
            enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 3 },
        ) {
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

@Composable
private fun QuietStep(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    Text(
        if (inQuietHours(settings.quietStart, settings.quietEnd)) {
            "Quiet hours would be on at this UTC hour."
        } else {
            "Quiet hours would be off at this UTC hour."
        },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HourField("From", settings.quietStart, { onChange(settings.copy(quietStart = it)) })
        HourField("Until", settings.quietEnd, { onChange(settings.copy(quietEnd = it)) })
    }
}

@Composable
private fun WifiStep(settings: PebbleSettings, onChange: (PebbleSettings) -> Unit) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    settings.networks.forEachIndexed { index, network ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
    Text("You can leave this empty and add a network later.")
}

@Composable
private fun PairStep(status: String, onPair: () -> Unit) {
    Text(status)
    Button(onClick = onPair) { Text("Pair") }
    Text("You can finish now and pair later from the Pebble screen.")
}

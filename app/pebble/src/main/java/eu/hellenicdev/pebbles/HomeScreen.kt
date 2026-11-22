package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun HomeScreen(
    settings: PebbleSettings,
    status: String,
    motion: Boolean,
    occasions: Set<String>,
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
) {
    val brand = PebblesBrand.current
    var preview by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(preview, motion) {
        if (preview.isEmpty()) return@LaunchedEffect
        if (motion) delay(2200)
        preview = ""
    }
    val quiet = inQuietHours(settings.quietStart, settings.quietEnd) || Occasion.QuietDay.id in occasions
    val scene = when {
        preview == "tap" -> PebbleScene.Tap
        preview == "hug" -> PebbleScene.Hug
        quiet -> PebbleScene.Quiet
        Occasion.Anniversary.id in occasions -> PebbleScene.Reply
        Occasion.Birthday.id in occasions -> PebbleScene.Hug
        else -> PebbleScene.Rest
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pebbleHeight = (maxHeight * 0.34f).coerceIn(160.dp, 280.dp)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Home", style = Type.display, color = brand.ink)
            Spacer(Modifier.height(4.dp))
            Text(
                homeStatus(settings, status, quiet),
                style = Type.body,
                color = brand.inkSoft,
            )
            BreathingPebble(
                colour = pebbleColor(settings.colour),
                scene = scene,
                motion = motion,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pebbleHeight)
                    .then(pebbleShared(sharedScope, visibilityScope, motion)),
            )
            Text("People", style = Type.title, color = brand.ink)
            Spacer(Modifier.height(8.dp))
            val people = settings.names.map { it.trim() }.filter { it.isNotEmpty() }
            if (people.isEmpty()) {
                Surface(shape = RoundedCornerShape(24.dp), color = brand.elev, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No one in this group yet.",
                        style = Type.body,
                        color = brand.inkSoft,
                        modifier = Modifier.padding(18.dp),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    people.forEach { name ->
                        PersonRow(name, pebbleColor(settings.colour), personState(quiet, occasions))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Send a touch", style = Type.title, color = brand.ink)
            Spacer(Modifier.height(8.dp))
            Text(
                "The pebble here glows so you can see it. The touch still happens on the pebble.",
                style = Type.body,
                color = brand.inkSoft,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrandButton("A tap", onClick = { preview = "tap" }, modifier = Modifier.weight(1f))
                BrandButton("A hug", onClick = { preview = "hug" }, modifier = Modifier.weight(1f))
            }
            AnimatedContent(
                targetState = preview,
                transitionSpec = { fadeIn(tween(if (motion) 220 else 0)) togetherWith fadeOut(tween(if (motion) 160 else 0)) },
                label = "touch-caption",
            ) { current ->
                val line = when (current) {
                    "tap" -> "Thinking of you."
                    "hug" -> "A hug, warm in the hand."
                    else -> ""
                }
                if (line.isNotEmpty()) {
                    Text(line, style = Type.body, color = brand.ink, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun pebbleShared(
    sharedScope: SharedTransitionScope,
    visibilityScope: AnimatedVisibilityScope,
    motion: Boolean,
): Modifier {
    if (!motion) return Modifier
    return with(sharedScope) {
        Modifier.sharedElement(
            rememberSharedContentState("pebble"),
            animatedVisibilityScope = visibilityScope,
        )
    }
}

@Composable
private fun PersonRow(name: String, colour: Color, state: String) {
    val brand = PebblesBrand.current
    Surface(shape = RoundedCornerShape(24.dp), color = brand.elev, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colour),
            )
            Column(Modifier.weight(1f)) {
                Text(name, style = Type.body, color = brand.ink)
                Text(state, style = Type.body, color = brand.inkSoft)
            }
        }
    }
}

private fun homeStatus(settings: PebbleSettings, status: String, quiet: Boolean): String {
    val hours = if (quiet) "Quiet" else "Awake"
    return "${settings.colourName} · $hours · $status"
}

private fun personState(quiet: Boolean, occasions: Set<String>): String {
    val glow = when {
        Occasion.Birthday.id in occasions -> "Birthday glow"
        Occasion.Anniversary.id in occasions -> "Anniversary glow"
        Occasion.QuietDay.id in occasions -> "Kept quiet"
        else -> null
    }
    val base = if (quiet) "Quiet" else "Awake"
    return if (glow == null) base else "$base · $glow"
}

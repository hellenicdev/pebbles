package eu.hellenicdev.pebbles

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

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
    val quiet = inQuietHours(settings.quietStart, settings.quietEnd) || Occasion.QuietDay.id in occasions
    val scene = when {
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
            Text("Your pebble", style = Type.display, color = brand.ink)
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
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = brand.elev,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "No one in this group yet. Add people in Settings." },
                ) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No one in this group yet.", style = Type.body, color = brand.ink)
                        Text("Add people in Settings.", style = Type.body, color = brand.inkSoft)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    people.forEach { name ->
                        PersonRow(name, pebbleColor(settings.colour), personState(quiet, occasions))
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("Recent", style = Type.title, color = brand.ink)
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = brand.elev,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Nothing has arrived yet. Touch your pebble to send." },
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nothing has arrived yet.", style = Type.body, color = brand.ink)
                    Text("Touch your pebble to send.", style = Type.body, color = brand.inkSoft)
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
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = brand.elev,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "$name, $state" },
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(colour)
                    .clearAndSetSemantics { },
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

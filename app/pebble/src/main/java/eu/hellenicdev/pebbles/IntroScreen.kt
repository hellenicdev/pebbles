package eu.hellenicdev.pebbles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class IntroPage(val kicker: String, val line: String, val scene: PebbleScene)

private val introPages = listOf(
    IntroPage(
        "Pebbles",
        "A pocket pebble for the people you love.",
        PebbleScene.Rest,
    ),
    IntroPage(
        "A tap",
        "A tap means thinking of you. Theirs glows in your colour.",
        PebbleScene.Tap,
    ),
    IntroPage(
        "A hold",
        "A long hold is a hug. Warm in the hand, never past 42°C.",
        PebbleScene.Hug,
    ),
    IntroPage(
        "Got it",
        "They touch back. That means it arrived.",
        PebbleScene.Reply,
    ),
    IntroPage(
        "At night",
        "Quiet hours. No buzz, and no chime.",
        PebbleScene.Quiet,
    ),
    IntroPage(
        "This phone",
        "The app only keeps the settings. The touch stays on the pebble.",
        PebbleScene.Rest,
    ),
)

@Composable
fun IntroScreen(colour: Color, motion: Boolean, onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(page, motion) {
        if (!motion || page >= introPages.lastIndex) return@LaunchedEffect
        delay(6200)
        page += 1
    }
    BackHandler(enabled = page > 0) { page -= 1 }
    Box(Modifier.fillMaxSize()) {
        SoftBackdrop(colour, motion, Modifier.fillMaxSize())
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDone) { Text("Skip") }
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BreathingPebble(
                    colour = colour,
                    scene = introPages[page].scene,
                    motion = motion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp),
                )
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (fadeIn(tween(900)) + slideInVertically(tween(900)) { it / 8 }) togetherWith
                            (fadeOut(tween(700)) + slideOutVertically(tween(700)) { -it / 10 })
                    },
                    label = "intro-copy",
                ) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            introPages[index].kicker,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            introPages[index].line,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            PageDots(page, introPages.size)
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { if (page < introPages.lastIndex) page += 1 else onDone() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (page < introPages.lastIndex) "Next" else "Set it up")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

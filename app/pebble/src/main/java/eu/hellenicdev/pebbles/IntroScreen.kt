package eu.hellenicdev.pebbles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
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
    IntroPage("Pebbles", "A pocket pebble for the people you love.", PebbleScene.Rest),
    IntroPage("A tap", "A tap means thinking of you. Theirs glows in your colour.", PebbleScene.Tap),
    IntroPage("A hold", "A long hold is a hug. Warm in the hand, never past 42°C.", PebbleScene.Hug),
    IntroPage("Got it", "They touch back. That means it arrived.", PebbleScene.Reply),
    IntroPage("At night", "Quiet hours. No buzz, and no chime.", PebbleScene.Quiet),
    IntroPage("This phone", "The app only keeps the settings. The touch stays on the pebble.", PebbleScene.Rest),
)

@Composable
fun IntroScreen(colour: Color, motion: Boolean, onDone: () -> Unit) {
    val brand = PebblesBrand.current
    var page by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(page, motion) {
        if (!motion || page >= introPages.lastIndex) return@LaunchedEffect
        delay(6400)
        page += 1
    }
    BackHandler(enabled = page > 0) { page -= 1 }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 22.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End) {
            BrandQuiet("Skip", onClick = onDone)
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val pebbleHeight = (maxHeight * 0.5f).coerceIn(132.dp, 240.dp)
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                BreathingPebble(
                    colour = colour,
                    scene = introPages[page].scene,
                    motion = motion,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pebbleHeight),
                )
                AnimatedContent(
                    targetState = page,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        (fadeIn(tween(420, easing = SmoothOut)) togetherWith fadeOut(tween(240))) using
                            SizeTransform(clip = false) { _, _ -> snap() }
                    },
                    contentAlignment = Alignment.TopCenter,
                    label = "intro-copy",
                ) { index ->
                    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            introPages[index].kicker.uppercase(),
                            style = Type.eyebrow,
                            color = brand.accent,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            introPages[index].line,
                            style = Type.title,
                            color = brand.ink,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        StepBar(page, introPages.size)
        Spacer(Modifier.height(16.dp))
        BrandButton(
            text = if (page < introPages.lastIndex) "Next" else "Set it up",
            onClick = { if (page < introPages.lastIndex) page += 1 else onDone() },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
    }
}

package eu.hellenicdev.pebbles

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone

enum class PebbleScene { Rest, Tap, Hug, Reply, Quiet }

fun pebbleColor(value: Long): Color = Color(value.toInt())

fun quietLine(start: Int, end: Int): String {
    if (start == end) return "Quiet hours are off."
    return "Quiet from %02d:00 until %02d:00 UTC.".format(start, end)
}

fun groupLine(names: List<String>): String {
    val people = names.map { it.trim() }.filter { it.isNotEmpty() }
    return when (people.size) {
        0 -> "No one in this group yet."
        1 -> "Shared with ${people[0]}."
        else -> "Shared with ${people.dropLast(1).joinToString(", ")} and ${people.last()}."
    }
}

fun inQuietHours(start: Int, end: Int): Boolean {
    if (start == end) return false
    val hour = Calendar.getInstance(TimeZone.getTimeZone("UTC")).get(Calendar.HOUR_OF_DAY)
    return if (start < end) hour in start until end else hour >= start || hour < end
}

fun pebbleLight() = lightColorScheme(
    primary = Color(0xFF7C3A24),
    background = Color(0xFFF4EFE8),
    surface = Color(0xFFFFFAF4),
)

fun pebbleDark() = darkColorScheme(
    primary = Color(0xFFF0B08A),
    background = Color(0xFF141210),
    surface = Color(0xFF221E1B),
)

@Composable
fun SoftBackdrop(colour: Color, motion: Boolean, modifier: Modifier = Modifier) {
    val drift = drift(motion, 9000)
    Canvas(modifier) {
        drawCircle(
            colour.copy(alpha = 0.18f),
            radius = size.minDimension * (0.52f + drift * 0.08f),
            center = Offset(size.width * (0.18f + drift * 0.16f), size.height * 0.2f),
        )
        drawCircle(
            colour.copy(alpha = 0.09f),
            radius = size.minDimension * (0.62f + (1f - drift) * 0.06f),
            center = Offset(size.width * (0.86f - drift * 0.12f), size.height * 0.78f),
        )
    }
}

@Composable
fun BreathingPebble(
    colour: Color,
    scene: PebbleScene,
    motion: Boolean,
    modifier: Modifier = Modifier,
) {
    val shown by animateColorAsState(colour, tween(if (motion) 1100 else 0), label = "pebble-colour")
    val breath = drift(motion, 3800)
    val rings = drift(motion, 4600, restart = true)
    val tap = soften(scene == PebbleScene.Tap, motion, 800)
    val hug = soften(scene == PebbleScene.Hug, motion, 1200)
    val reply = soften(scene == PebbleScene.Reply, motion, 1200)
    val quiet = soften(scene == PebbleScene.Quiet, motion, 1200)
    Canvas(
        modifier.semantics { contentDescription = "A glowing pebble" },
    ) {
        val c = center
        val base = size.minDimension * 0.22f
        val radius = base * (1f + breath * 0.07f)
        val halo = radius * (1.45f + breath * 0.18f + hug * 0.7f)
        drawCircle(shown.copy(alpha = 0.07f + breath * 0.05f), radius = halo * 1.35f, center = c)
        drawCircle(
            shown.copy(alpha = 0.16f + breath * 0.08f + hug * 0.1f - quiet * 0.1f),
            radius = halo,
            center = c,
        )
        if (hug > 0.01f) {
            drawCircle(
                Color(0xFFFFE6C8).copy(alpha = 0.22f * hug),
                radius = radius * (1.7f + breath * 0.45f),
                center = c,
            )
        }
        if (tap > 0.01f) {
            repeat(3) { index ->
                val progress = (rings + index * 0.33f) % 1f
                drawCircle(
                    color = shown.copy(alpha = (1f - progress) * 0.4f * tap),
                    radius = radius * (1f + progress * 2.1f),
                    center = c,
                    style = Stroke(width = 2.5f),
                )
            }
        }
        val body = lerp(shown, shown.copy(alpha = 0.38f), quiet)
        drawCircle(body, radius = radius, center = c)
        drawOval(
            color = Color.White.copy(alpha = 0.3f * (1f - quiet * 0.65f)),
            topLeft = Offset(c.x - radius * 0.55f, c.y - radius * 0.75f),
            size = Size(radius * 0.72f, radius * 0.36f),
        )
        if (reply > 0.01f) {
            val other = Offset(c.x + radius * 1.85f, c.y + radius * 0.2f)
            val replyRadius = radius * (0.28f + reply * 0.22f)
            drawCircle(shown.copy(alpha = 0.16f * reply), radius = replyRadius * 1.8f, center = other)
            drawCircle(shown.copy(alpha = reply), radius = replyRadius, center = other)
        }
    }
}

@Composable
fun PageDots(index: Int, count: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        repeat(count) { dot ->
            val width by animateDpAsState(if (dot == index) 22.dp else 8.dp, tween(550), label = "dot")
            Box(
                Modifier
                    .size(width = width, height = 8.dp)
                    .background(
                        if (dot == index) primary else primary.copy(alpha = 0.28f),
                        CircleShape,
                    ),
            )
        }
    }
}

@Composable
fun HourField(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()?.let { hour -> if (hour in 0..23) onChange(hour) }
        },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(0.45f),
        singleLine = true,
    )
}

@Composable
private fun drift(motion: Boolean, duration: Int, restart: Boolean = false): Float {
    val transition = rememberInfiniteTransition(label = "drift-$duration")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(duration, easing = if (restart) LinearEasing else FastOutSlowInEasing),
            repeatMode = if (restart) RepeatMode.Restart else RepeatMode.Reverse,
        ),
        label = "drift-value",
    )
    return if (motion) animated else 0.35f
}

@Composable
private fun soften(on: Boolean, motion: Boolean, duration: Int): Float {
    val value by animateFloatAsState(if (on) 1f else 0f, tween(if (motion) duration else 0), label = "soften")
    return value
}

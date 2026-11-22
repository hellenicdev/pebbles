package eu.hellenicdev.pebbles

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import android.view.HapticFeedbackConstants
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.sin

val SmoothOut = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

private val Serif = FontFamily.Serif
private val Sans = FontFamily.SansSerif

object Type {
    val display = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.6).sp,
    )
    val title = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.4).sp,
    )
    val body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
    )
    val eyebrow = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.8.sp,
    )
    val button = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )
    val nav = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )
}

@Immutable
data class Brand(
    val bg: Color,
    val elev: Color,
    val ink: Color,
    val inkSoft: Color,
    val line: Color,
    val accent: Color,
    val button: Color,
    val buttonText: Color,
)

private val LightBrand = Brand(
    bg = Color(0xFFF4EFE8),
    elev = Color(0xFFFFFAF4),
    ink = Color(0xFF241F1B),
    inkSoft = Color(0xFF5C534C),
    line = Color(0xFFE3D6C8),
    accent = Color(0xFF7C3A24),
    button = Color(0xFF241F1B),
    buttonText = Color(0xFFFFFAF4),
)

private val DarkBrand = Brand(
    bg = Color(0xFF141210),
    elev = Color(0xFF221E1B),
    ink = Color(0xFFF6F1EA),
    inkSoft = Color(0xFFD4C6B8),
    line = Color(0xFF3C342E),
    accent = Color(0xFFF0B08A),
    button = Color(0xFFF6F1EA),
    buttonText = Color(0xFF241F1B),
)

private val LocalBrand = staticCompositionLocalOf { LightBrand }

object PebblesBrand {
    val current: Brand
        @Composable get() = LocalBrand.current
}

private val PebbleTypography = Typography(
    headlineLarge = Type.display,
    headlineMedium = Type.title,
    titleLarge = Type.title,
    bodyLarge = Type.body,
    bodyMedium = Type.body,
    labelLarge = Type.button,
    labelMedium = Type.eyebrow,
)

@Composable
fun BrandTheme(dark: Boolean, accent: Color = Color(0xFF7C3A24), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dynamic = if (Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }
    val seeded = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF241F1B),
            background = DarkBrand.bg,
            surface = DarkBrand.elev,
            onBackground = DarkBrand.ink,
            onSurface = DarkBrand.ink,
            onSurfaceVariant = DarkBrand.inkSoft,
            outlineVariant = DarkBrand.line,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFFFFFAF4),
            background = LightBrand.bg,
            surface = LightBrand.elev,
            onBackground = LightBrand.ink,
            onSurface = LightBrand.ink,
            onSurfaceVariant = LightBrand.inkSoft,
            outlineVariant = LightBrand.line,
        )
    }
    val scheme = (dynamic ?: seeded).copy(
        primary = accent,
        onPrimary = if (dark) Color(0xFF241F1B) else Color(0xFFFFFAF4),
        primaryContainer = accent.copy(alpha = if (dark) 0.28f else 0.18f),
    )
    val brand = Brand(
        bg = scheme.background,
        elev = scheme.surfaceContainerHigh,
        ink = scheme.onBackground,
        inkSoft = scheme.onSurfaceVariant,
        line = scheme.outlineVariant,
        accent = accent,
        button = scheme.primary,
        buttonText = scheme.onPrimary,
    )
    MaterialTheme(colorScheme = scheme, typography = PebbleTypography) {
        CompositionLocalProvider(LocalBrand provides brand, content = content)
    }
}

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

@Composable
fun SoftBackdrop(colour: Color, motion: Boolean, modifier: Modifier = Modifier) {
    val drift = drift(motion, 8400)
    Box(modifier) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(start = 12.dp, top = 28.dp)
                .size(240.dp)
                .graphicsLayer {
                    translationX = (drift - 0.5f) * 28.dp.toPx()
                    translationY = (0.5f - drift) * 18.dp.toPx()
                }
                .background(colour.copy(alpha = 0.16f), CircleShape),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 36.dp)
                .size(280.dp)
                .graphicsLayer {
                    translationX = (0.5f - drift) * 22.dp.toPx()
                    translationY = (drift - 0.5f) * 16.dp.toPx()
                }
                .background(colour.copy(alpha = 0.09f), CircleShape),
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
    val phase = drift(motion, 5200, restart = true)
    val breath = if (motion) sin(phase * 2.0 * PI).toFloat() else 0f
    val tap = soften(scene == PebbleScene.Tap, motion, 700)
    val hug = soften(scene == PebbleScene.Hug, motion, 900)
    val reply = soften(scene == PebbleScene.Reply, motion, 900)
    val quiet = soften(scene == PebbleScene.Quiet, motion, 900)
    Canvas(
        modifier
            .graphicsLayer {
                val scale = 1f + breath * 0.035f
                scaleX = scale
                scaleY = scale
            }
            .semantics { contentDescription = "A glowing pebble" },
    ) {
        val c = center
        val maxR = size.minDimension * 0.46f
        val radius = maxR * 0.42f
        val replyR = radius * 0.42f
        val gap = radius * 0.42f
        val shift = (radius * 0.72f) * reply
        val main = Offset(c.x - shift, c.y)
        val halo = (radius * (1.45f + hug * 0.28f)).coerceAtMost(maxR)
        drawCircle(colour.copy(alpha = 0.08f), radius = halo * 1.15f, center = main)
        drawCircle(colour.copy(alpha = 0.16f + hug * 0.08f - quiet * 0.08f), radius = halo, center = main)
        if (hug > 0.01f) {
            drawCircle(Color(0xFFFFE6C8).copy(alpha = 0.2f * hug), radius = radius * 1.25f, center = main)
        }
        if (tap > 0.01f) {
            repeat(3) { index ->
                val progress = (phase + index * 0.33f) % 1f
                drawCircle(
                    color = colour.copy(alpha = (1f - progress) * 0.35f * tap),
                    radius = (radius * (1f + progress * 1.35f)).coerceAtMost(maxR),
                    center = main,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        val body = lerp(colour, colour.copy(alpha = 0.45f), quiet)
        drawCircle(body, radius = radius, center = main)
        drawOval(
            color = Color.White.copy(alpha = 0.28f * (1f - quiet * 0.6f)),
            topLeft = Offset(main.x - radius * 0.48f, main.y - radius * 0.62f),
            size = Size(radius * 0.62f, radius * 0.3f),
        )
        if (reply > 0.01f) {
            val other = Offset(main.x + radius + gap, main.y + radius * 0.06f)
            drawCircle(colour.copy(alpha = 0.14f * reply), radius = replyR * 1.6f, center = other)
            drawCircle(colour.copy(alpha = reply), radius = replyR, center = other)
        }
    }
}

@Composable
fun StepBar(index: Int, count: Int, modifier: Modifier = Modifier) {
    val brand = PebblesBrand.current
    val progress by animateFloatAsState(
        (index + 1f) / count.coerceAtLeast(1),
        tween(420, easing = SmoothOut),
        label = "step",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(CircleShape)
            .background(brand.line),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = progress
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(brand.accent),
        )
    }
}

@Composable
fun BrandButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val view = LocalView.current
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(text, style = Type.button)
    }
}

@Composable
fun BrandQuiet(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, style = Type.button, color = PebblesBrand.current.accent)
    }
}

@Composable
fun BrandField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(18.dp),
    )
}

@Composable
fun ColourChoices(selected: String, onPick: (String, Long) -> Unit) {
    val brand = PebblesBrand.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Colours.choices.forEach { (name, value) ->
            val on = name == selected
            val scale by animateFloatAsState(if (on) 1f else 0.92f, tween(280, easing = SmoothOut), label = name)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(
                    remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onPick(name, value) },
                ),
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .border(if (on) 2.dp else 1.dp, if (on) brand.ink else brand.line, CircleShape)
                        .padding(4.dp)
                        .background(pebbleColor(value), CircleShape),
                )
                Box(Modifier.height(6.dp))
                Text(
                    name,
                    style = Type.nav,
                    color = if (on) brand.ink else brand.inkSoft,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
fun PeopleEditor(names: List<String>, onChange: (List<String>) -> Unit) {
    val brand = PebblesBrand.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        names.forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BrandField(
                    value = name,
                    onValueChange = { value ->
                        onChange(names.toMutableList().also { it[index] = value })
                    },
                    label = "Person ${index + 1}",
                    modifier = Modifier.weight(1f),
                )
                BrandQuiet("Remove", onClick = {
                    onChange(names.toMutableList().also { it.removeAt(index) })
                })
            }
        }
        if (names.size < 5) {
            BrandQuiet("Add someone", onClick = { onChange(names + "") })
        }
        Text(
            when (val count = names.count { it.isNotBlank() }) {
                0 -> "Add one person, or a few. Five is the most."
                else -> "$count of 5"
            },
            style = Type.body,
            color = brand.inkSoft,
        )
    }
}

@Composable
fun HourFields(start: Int, end: Int, onStart: (Int) -> Unit, onEnd: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandField(
            value = start.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { if (it in 0..23) onStart(it) } },
            label = "From",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
        )
        BrandField(
            value = end.toString(),
            onValueChange = { text -> text.toIntOrNull()?.let { if (it in 0..23) onEnd(it) } },
            label = "Until",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
        )
    }
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
    val value by animateFloatAsState(
        if (on) 1f else 0f,
        tween(if (motion) duration else 0, easing = SmoothOut),
        label = "soften",
    )
    return value
}

package dev.byteghst.permlens

import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Palette + theme
// ---------------------------------------------------------------------------

object Pal {
    val Bg = Color(0xFF0B0F1A)
    val Surface = Color(0xFF141A2B)
    val SurfaceHi = Color(0xFF1C2440)
    val Cyan = Color(0xFF6EE7F9)
    val Violet = Color(0xFF8B7CFF)
    val Pink = Color(0xFFF472B6)
    val Allow = Color(0xFF34D399)
    val Ignore = Color(0xFFFBBF24)
    val Deny = Color(0xFFF87171)
    val Foreground = Color(0xFF60A5FA)
    val Neutral = Color(0xFF94A3B8)
    val TextHi = Color(0xFFEAF0FF)
    val TextLo = Color(0xFF8A94B2)
}

private val Scheme = darkColorScheme(
    primary = Pal.Cyan,
    onPrimary = Pal.Bg,
    secondary = Pal.Violet,
    onSecondary = Pal.Bg,
    background = Pal.Bg,
    onBackground = Pal.TextHi,
    surface = Pal.Surface,
    onSurface = Pal.TextHi,
    surfaceVariant = Pal.SurfaceHi,
    onSurfaceVariant = Pal.TextLo,
    outline = Color.White.copy(alpha = 0.18f)
)

@Composable
fun PermLensTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

// ---------------------------------------------------------------------------
// Mode helpers
// ---------------------------------------------------------------------------

fun modeLabel(mode: Int) = when (mode) {
    0 -> "Allow"
    1 -> "Ignore"
    2 -> "Deny"
    3 -> "Default"
    4 -> "Foreground"
    else -> "Mode $mode"
}

fun modeHint(mode: Int) = when (mode) {
    0 -> "App can use this freely"
    1 -> "Calls silently do nothing"
    2 -> "App gets a security error"
    3 -> "Let the system decide"
    4 -> "Only while the app is visible"
    else -> ""
}

fun modeColor(mode: Int): Color = when (mode) {
    0 -> Pal.Allow
    1 -> Pal.Ignore
    2 -> Pal.Deny
    4 -> Pal.Foreground
    else -> Pal.Neutral
}

fun prettyOp(name: String): String =
    name.split('_').filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

// ---------------------------------------------------------------------------
// Background
// ---------------------------------------------------------------------------

/** Slowly drifting aurora blobs behind everything. */
@Composable
fun AuroraBackground(content: @Composable BoxScope.() -> Unit) {
    val t = rememberInfiniteTransition(label = "aurora")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p"
    )
    Box(Modifier.fillMaxSize().background(Pal.Bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.radialGradient(
                    listOf(Pal.Violet.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * (0.1f + 0.6f * p), h * (0.08f + 0.12f * p)),
                    radius = w * 1.0f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Pal.Cyan.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * (0.95f - 0.6f * p), h * (0.55f + 0.15f * p)),
                    radius = w * 0.9f
                )
            )
            drawRect(
                Brush.radialGradient(
                    listOf(Pal.Pink.copy(alpha = 0.13f), Color.Transparent),
                    center = Offset(w * (0.3f + 0.3f * p), h * (0.98f - 0.2f * p)),
                    radius = w * 0.8f
                )
            )
        }
        content()
    }
}

// ---------------------------------------------------------------------------
// Modifiers
// ---------------------------------------------------------------------------

/** Frosted-glass card look. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(20.dp)): Modifier = this
    .clip(shape)
    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.09f), Color.White.copy(alpha = 0.03f))))
    .border(
        1.dp,
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0.04f))),
        shape
    )

/** Springy press-down scale + a light haptic tick. */
fun Modifier.bouncyClick(onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val haptic = LocalHapticFeedback.current
    Modifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = src, indication = null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
}

/** Fade + slide-up entrance, staggered by list position for the first screenful. */
fun Modifier.entrance(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(if (index < 12) index * 35L else 0L)
        a.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Modifier.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 48f
    }
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

@Composable
fun GradientTitle(text: String, size: TextUnit = 34.sp) {
    Text(
        text,
        style = TextStyle(
            brush = Brush.horizontalGradient(listOf(Pal.Cyan, Pal.Violet, Pal.Pink)),
            fontSize = size,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .bouncyClick(onClick)
            .clip(shape)
            .then(
                if (filled) Modifier.background(Brush.horizontalGradient(listOf(Pal.Cyan, Pal.Violet)))
                else Modifier.background(Color.White.copy(alpha = 0.06f)).border(1.dp, Color.White.copy(alpha = 0.18f), shape)
            )
            .padding(horizontal = 26.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) Pal.Bg else Pal.TextHi, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Pill selector with a springy sliding indicator. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(4.dp)
    ) {
        val segW = maxWidth / options.size
        val x by animateDpAsState(
            targetValue = segW * selected,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
            label = "seg"
        )
        Box(
            Modifier.offset(x = x).width(segW).height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.horizontalGradient(listOf(Pal.Cyan.copy(alpha = 0.95f), Pal.Violet.copy(alpha = 0.95f))))
        )
        Row {
            options.forEachIndexed { i, t ->
                val c by animateColorAsState(if (i == selected) Pal.Bg else Pal.TextLo, label = "segc")
                Box(
                    Modifier.weight(1f).height(40.dp).clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (i != selected) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        }
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Text(t, color = c, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

private object IconCache {
    val cache = LruCache<String, ImageBitmap>(300)
}

@Composable
fun AppIcon(pkg: String, label: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = IconCache.cache.get(pkg), key1 = pkg) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    ctx.packageManager.getApplicationIcon(pkg).toBitmap(112, 112).asImageBitmap()
                }.getOrNull()?.also { IconCache.cache.put(pkg, it) }
            }
        }
    }
    val palette = listOf(Pal.Cyan, Pal.Violet, Pal.Pink, Pal.Allow, Pal.Ignore, Pal.Foreground)
    val tint = palette[(pkg.hashCode() and 0x7fffffff) % palette.size]
    val shape = RoundedCornerShape(size * 0.28f)
    Crossfade(targetState = bmp, modifier = Modifier.size(size).clip(shape), label = "icon") { b ->
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    color = Pal.TextHi,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
    }
}

@Composable
fun ModeChip(mode: Int) {
    val c by animateColorAsState(modeColor(mode), tween(400), label = "chip")
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.clip(shape)
            .background(c.copy(alpha = 0.16f))
            .border(1.dp, c.copy(alpha = 0.5f), shape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(modeLabel(mode), color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun Tag(text: String) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        Modifier.clip(shape)
            .background(Pal.Violet.copy(alpha = 0.18f))
            .border(1.dp, Pal.Violet.copy(alpha = 0.4f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, color = Pal.Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun PulseDot(color: Color, size: Dp = 8.dp) {
    val t = rememberInfiniteTransition(label = "pulse")
    val s by t.animateFloat(
        initialValue = 1f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Restart),
        label = "s"
    )
    Box(Modifier.size(size * 2.4f), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .graphicsLayer {
                    scaleX = s
                    scaleY = s
                    alpha = (2.4f - s) / 1.4f * 0.6f
                }
                .background(color, CircleShape)
        )
        Box(Modifier.size(size).background(color, CircleShape))
    }
}

@Composable
fun StatTile(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val v by animateIntAsState(target, tween(800, easing = FastOutSlowInEasing), label = "stat")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.25f), shape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$v", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Pal.TextLo, fontSize = 11.sp)
    }
}

/** Shimmering placeholder rows while data loads. */
@Composable
fun SkeletonList(rows: Int = 8, rowHeight: Dp = 70.dp) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "skx"
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            Box(
                Modifier.fillMaxWidth().height(rowHeight)
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind {
                        drawRect(
                            Brush.linearGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.04f),
                                    Color.White.copy(alpha = 0.13f),
                                    Color.White.copy(alpha = 0.04f)
                                ),
                                start = Offset(x * size.width, 0f),
                                end = Offset(x * size.width + size.width * 0.6f, 0f)
                            )
                        )
                    }
            )
        }
    }
}

/** Animated lens: expanding radar rings, a gradient lens ring and a rotating sweep. */
@Composable
fun LensLogo(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "lens")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "phase"
    )
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "spin"
    )
    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.26f
        for (k in 0..2) {
            val ph = (phase + k / 3f) % 1f
            drawCircle(
                color = Pal.Cyan.copy(alpha = (1f - ph) * 0.45f),
                radius = base * (1f + ph * 1.5f),
                center = c,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        drawCircle(
            brush = Brush.radialGradient(listOf(Pal.Violet.copy(alpha = 0.35f), Color.Transparent), center = c, radius = base * 1.3f),
            radius = base * 1.3f,
            center = c
        )
        drawCircle(
            brush = Brush.sweepGradient(listOf(Pal.Cyan, Pal.Violet, Pal.Cyan), center = c),
            radius = base,
            center = c,
            style = Stroke(width = 7.dp.toPx())
        )
        val a = Math.toRadians(45.0)
        val start = Offset(c.x + (base * cos(a)).toFloat(), c.y + (base * sin(a)).toFloat())
        val end = Offset(c.x + (base * 1.65f * cos(a)).toFloat(), c.y + (base * 1.65f * sin(a)).toFloat())
        drawLine(Pal.Violet, start, end, strokeWidth = 9.dp.toPx(), cap = StrokeCap.Round)
        drawArc(
            color = Pal.Cyan,
            startAngle = spin,
            sweepAngle = 70f,
            useCenter = false,
            topLeft = Offset(c.x - base * 0.62f, c.y - base * 0.62f),
            size = Size(base * 1.24f, base * 1.24f),
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

/** Bottom pill that springs in and out. */
@Composable
fun ToastPill(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(spring(dampingRatio = 0.65f, stiffness = 400f)) { it } +
            fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200))
    ) {
        val shape = RoundedCornerShape(18.dp)
        Box(
            Modifier.clip(shape)
                .background(Pal.SurfaceHi)
                .border(1.dp, Color.White.copy(alpha = 0.18f), shape)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(text, color = Pal.TextHi, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

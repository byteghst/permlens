@file:OptIn(ExperimentalMaterial3Api::class)

package dev.byteghst.permlens

import android.os.Build
import android.util.LruCache
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Theme: Material 3 Expressive look on the stable material3 1.3.0 stack.
// Dynamic color on Android 12+, hand-tuned static schemes as fallback (API 30).
// See DESIGN.md for the rules that keep this consistent.
// ---------------------------------------------------------------------------

private val LightScheme = lightColorScheme(
    primary = Color(0xFF4355B9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    inversePrimary = Color(0xFFBAC3FF),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171B2C),
    tertiary = Color(0xFF984061),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E2),
    onTertiaryContainer = Color(0xFF3E001D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC),
    onSurfaceVariant = Color(0xFF46464F),
    surfaceTint = Color(0xFF4355B9),
    inverseSurface = Color(0xFF303036),
    inverseOnSurface = Color(0xFFF2F0F7),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceDim = Color(0xFFDBD9E0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFEDF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE4E1E9)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = Color(0xFF08218A),
    primaryContainer = Color(0xFF293CA0),
    onPrimaryContainer = Color(0xFFDDE1FF),
    inversePrimary = Color(0xFF4355B9),
    secondary = Color(0xFFC2C5DD),
    onSecondary = Color(0xFF2B3042),
    secondaryContainer = Color(0xFF424659),
    onSecondaryContainer = Color(0xFFDFE1F9),
    tertiary = Color(0xFFFFB1C8),
    onTertiary = Color(0xFF5E1133),
    tertiaryContainer = Color(0xFF7B2949),
    onTertiaryContainer = Color(0xFFFFD9E2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE4E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE4E1E9),
    surfaceVariant = Color(0xFF46464F),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceTint = Color(0xFFBAC3FF),
    inverseSurface = Color(0xFFE4E1E9),
    inverseOnSurface = Color(0xFF303036),
    outline = Color(0xFF90909A),
    outlineVariant = Color(0xFF46464F),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF38393F),
    surfaceDim = Color(0xFF121318),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F25),
    surfaceContainerHigh = Color(0xFF292A2F),
    surfaceContainerHighest = Color(0xFF34343A)
)

/** Expressive shape scale: noticeably rounder than baseline M3. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

/** Expressive type: heavier headline and title weights. */
private val AppTypography: Typography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.copy(fontWeight = FontWeight.SemiBold)
    )
}

@Composable
fun PermLensTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else if (dark) {
        DarkScheme
    } else {
        LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

// ---------------------------------------------------------------------------
// Motion: M3 Expressive spring tokens
// ---------------------------------------------------------------------------

object Motion {
    /** Default spatial spring: moves, sizes, shape morphs. */
    fun <T> spatial(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)

    /** Fast spatial spring: small, snappy things like button corners. */
    fun <T> fastSpatial(): SpringSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)

    /** Effects spring: color and alpha. No overshoot. */
    fun <T> effects(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)
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

fun prettyOp(name: String): String =
    name.split('_').filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

/** Semantic colors for an op mode. [accent] is for dots and stripes, the others for tonal chips. */
@Immutable
class ModeColors(val accent: Color, val container: Color, val onContainer: Color)

@Composable
fun modeColors(mode: Int): ModeColors {
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    return when (mode) {
        0 -> {
            if (dark) ModeColors(Color(0xFF53D18A), Color(0xFF0F3D26), Color(0xFFA6F2C6))
            else ModeColors(Color(0xFF1FA463), Color(0xFFB8F2CF), Color(0xFF0B5B35))
        }
        1 -> {
            if (dark) ModeColors(Color(0xFFFFC53D), Color(0xFF4A3800), Color(0xFFFFE08A))
            else ModeColors(Color(0xFFD99A00), Color(0xFFFFE9A8), Color(0xFF5C4300))
        }
        2 -> ModeColors(cs.error, cs.errorContainer, cs.onErrorContainer)
        4 -> {
            if (dark) ModeColors(Color(0xFF7FB0FF), Color(0xFF0F3566), Color(0xFFD6E3FF))
            else ModeColors(Color(0xFF2F6FED), Color(0xFFD6E3FF), Color(0xFF0A3D86))
        }
        else -> ModeColors(cs.outline, cs.surfaceContainerHighest, cs.onSurfaceVariant)
    }
}

/** Corner pattern for a connected group: outer corners round, inner corners tight. */
fun groupShape(index: Int, count: Int, outer: Dp = 28.dp, inner: Dp = 10.dp): RoundedCornerShape {
    val s = if (index == 0) outer else inner
    val e = if (index == count - 1) outer else inner
    return RoundedCornerShape(topStart = s, topEnd = e, bottomEnd = e, bottomStart = s)
}

// ---------------------------------------------------------------------------
// Modifiers
// ---------------------------------------------------------------------------

/** Spring fade + slide-up entrance, staggered by list position for the first screenful. */
fun Modifier.entrance(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(if (index < 12) index * 40L else 0L)
        a.animateTo(1f, Motion.spatial())
    }
    Modifier.graphicsLayer {
        alpha = a.value.coerceIn(0f, 1f)
        translationY = (1f - a.value) * 56f
    }
}

// ---------------------------------------------------------------------------
// Frame: large collapsing top app bar + body. Every screen uses this.
// ---------------------------------------------------------------------------

@Composable
fun ScreenFrame(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    body: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Box(modifier.fillMaxSize().background(cs.surface)) {
        Column(Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection)) {
            LargeTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) {
                        FilledTonalIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = cs.surface,
                    scrolledContainerColor = cs.surfaceContainer
                ),
                scrollBehavior = scroll
            )
            body()
        }
        overlay()
    }
}

// ---------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------

/** Clickable tonal surface whose corners morph and which squishes slightly while pressed. */
@Composable
fun PressSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    restCorner: Dp = 24.dp,
    pressedCorner: Dp = 12.dp,
    content: @Composable () -> Unit
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) pressedCorner else restCorner, Motion.fastSpatial(), label = "corner")
    val scale by animateFloatAsState(if (pressed) 0.975f else 1f, Motion.spatial(), label = "scale")
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = color,
        interactionSource = src,
        content = content
    )
}

enum class BtnKind { Filled, Tonal, Danger }

/** Large pill button. The corners tighten while pressed. */
@Composable
fun ExpressiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: BtnKind = BtnKind.Filled,
    icon: ImageVector? = null
) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = when (kind) {
        BtnKind.Filled -> cs.primary to cs.onPrimary
        BtnKind.Tonal -> cs.secondaryContainer to cs.onSecondaryContainer
        BtnKind.Danger -> cs.errorContainer to cs.onErrorContainer
    }
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val corner by animateDpAsState(if (pressed) 14.dp else 28.dp, Motion.fastSpatial(), label = "btnCorner")
    val haptic = LocalHapticFeedback.current
    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(corner.coerceAtLeast(0.dp)),
        color = bg,
        contentColor = fg,
        interactionSource = src
    ) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.sp))
        }
    }
}

/** Connected button group: the selected button turns fully round, the others stay tight. */
@Composable
fun ConnectedGroup(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            val startC by animateDpAsState(if (sel || i == 0) 24.dp else 8.dp, Motion.spatial(), label = "gs")
            val endC by animateDpAsState(if (sel || i == options.lastIndex) 24.dp else 8.dp, Motion.spatial(), label = "ge")
            val bg by animateColorAsState(if (sel) cs.primary else cs.surfaceContainerHighest, Motion.effects(), label = "gb")
            val fg by animateColorAsState(if (sel) cs.onPrimary else cs.onSurfaceVariant, Motion.effects(), label = "gf")
            Surface(
                onClick = {
                    if (!sel) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSelect(i)
                    }
                },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(
                    topStart = startC.coerceAtLeast(0.dp),
                    topEnd = endC.coerceAtLeast(0.dp),
                    bottomEnd = endC.coerceAtLeast(0.dp),
                    bottomStart = startC.coerceAtLeast(0.dp)
                ),
                color = bg,
                contentColor = fg
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(hint) },
        singleLine = true,
        shape = CircleShape,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear")
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = cs.surfaceContainerHigh,
            unfocusedContainerColor = cs.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent
        ),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 8.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
fun ErrorCard(title: String, details: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = cs.errorContainer,
        contentColor = cs.onErrorContainer
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SelectionContainer(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                Text(details, style = MaterialTheme.typography.bodySmall)
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
                    ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap()
                }.getOrNull()?.also { IconCache.cache.put(pkg, it) }
            }
        }
    }
    val cs = MaterialTheme.colorScheme
    val pairs = listOf(
        cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer,
        cs.tertiaryContainer to cs.onTertiaryContainer
    )
    val (bg, fg) = pairs[(pkg.hashCode() and 0x7fffffff) % pairs.size]
    val shape = RoundedCornerShape(size * 0.32f)
    Crossfade(targetState = bmp, modifier = Modifier.size(size).clip(shape), label = "icon") { b ->
        if (b != null) {
            Image(bitmap = b, contentDescription = null, modifier = Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "?",
                    color = fg,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.4f).sp
                )
            }
        }
    }
}

@Composable
fun ModeChip(mode: Int) {
    val mc = modeColors(mode)
    val bg by animateColorAsState(mc.container, Motion.effects(), label = "chipBg")
    val fg by animateColorAsState(mc.onContainer, Motion.effects(), label = "chipFg")
    Surface(shape = CircleShape, color = bg, contentColor = fg) {
        Text(
            modeLabel(mode),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun Tag(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = CircleShape, color = cs.secondaryContainer, contentColor = cs.onSecondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
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

/** Big number tile with a count-up animation. */
@Composable
fun StatTile(
    value: Int,
    label: String,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp)
) {
    var target by remember { mutableStateOf(0) }
    LaunchedEffect(value) { target = value }
    val v by animateIntAsState(target, tween(700, easing = FastOutSlowInEasing), label = "stat")
    Column(
        modifier.clip(shape).background(container).padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("$v", color = content, style = MaterialTheme.typography.headlineMedium)
        Text(label, color = content.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
    }
}

/** Shimmering placeholder rows while data loads. */
@Composable
fun SkeletonList(rows: Int = 8, rowHeight: Dp = 70.dp) {
    val cs = MaterialTheme.colorScheme
    val base = cs.surfaceContainerHigh
    val hi = cs.surfaceContainerHighest
    val t = rememberInfiniteTransition(label = "skeleton")
    val x by t.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "skx"
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(rows) {
            Box(
                Modifier.fillMaxWidth().height(rowHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .drawBehind {
                        drawRect(
                            Brush.linearGradient(
                                listOf(base, hi, base),
                                start = Offset(x * size.width, 0f),
                                end = Offset(x * size.width + size.width * 0.6f, 0f)
                            )
                        )
                    }
            )
        }
    }
}

private fun blobPath(center: Offset, radius: Float, lobes: Int, amp: Float): Path {
    val path = Path()
    val steps = 144
    for (i in 0..steps) {
        val a = (i.toFloat() / steps) * (2f * Math.PI.toFloat())
        val r = radius * (1f + amp * cos(lobes * a))
        val x = center.x + r * cos(a)
        val y = center.y + r * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Hero mark: two slowly counter-rotating scalloped blobs with a lens on top. */
@Composable
fun LensLogo(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val big = cs.primaryContainer
    val small = cs.tertiaryContainer
    val ring = cs.onTertiaryContainer
    val accent = cs.primary
    val t = rememberInfiniteTransition(label = "lens")
    val spin by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing)),
        label = "spin"
    )
    val pulse by t.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(modifier) {
        val c = center
        val base = size.minDimension * 0.30f
        rotate(spin, c) {
            drawPath(blobPath(c, base * 1.55f * pulse, 12, 0.045f), big)
        }
        rotate(-spin * 1.5f, c) {
            drawPath(blobPath(c, base * 1.15f, 8, 0.07f), small)
        }
        val lensR = base * 0.55f
        drawCircle(ring, radius = lensR, center = c, style = Stroke(width = 8.dp.toPx()))
        val a = Math.toRadians(45.0)
        val start = Offset(c.x + (lensR * cos(a)).toFloat(), c.y + (lensR * sin(a)).toFloat())
        val end = Offset(c.x + (lensR * 1.9f * cos(a)).toFloat(), c.y + (lensR * 1.9f * sin(a)).toFloat())
        drawLine(ring, start, end, strokeWidth = 10.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(accent, radius = lensR * 0.22f, center = c)
    }
}

// ---------------------------------------------------------------------------
// Toast
// ---------------------------------------------------------------------------

class ToastState {
    var text by mutableStateOf("")
    var tick by mutableStateOf(0)
    var shown by mutableStateOf(false)
    fun show(t: String) {
        text = t
        tick++
        shown = true
    }
}

@Composable
fun rememberToast(): ToastState {
    val s = remember { ToastState() }
    LaunchedEffect(s.tick) {
        if (s.tick > 0) {
            delay(2600)
            s.shown = false
        }
    }
    return s
}

/** Bottom pill that springs in and out. */
@Composable
fun ToastPill(visible: Boolean, text: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(Motion.spatial<IntOffset>()) { it } +
            fadeIn(tween(200)) + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(tween(250)) { it } + fadeOut(tween(200))
    ) {
        val cs = MaterialTheme.colorScheme
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cs.inverseSurface,
            contentColor = cs.inverseOnSurface,
            shadowElevation = 6.dp
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Mode picker (shared by the per-app and per-op screens)
// ---------------------------------------------------------------------------

@Composable
fun ModeOptionRow(mode: Int, current: Boolean, index: Int, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val mc = modeColors(mode)
    PressSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().entrance(index),
        color = if (current) mc.container else cs.surfaceContainerHighest,
        restCorner = 22.dp,
        pressedCorner = 12.dp
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(mc.accent, CircleShape))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    modeLabel(mode),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (current) mc.onContainer else cs.onSurface
                )
                Text(
                    modeHint(mode),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current) mc.onContainer else cs.onSurfaceVariant
                )
            }
            if (current) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = mc.onContainer)
            }
        }
    }
}

@Composable
fun ModeSheet(
    title: String,
    subtitle: String,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = cs.surfaceContainerHigh
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            listOf(0, 1, 2, 4, 3).forEachIndexed { i, m ->
                ModeOptionRow(m, m == current, i) {
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                    onPick(m)
                }
            }
        }
    }
}

package org.idktech.lumen.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalTextStyle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import org.idktech.lumen.R
import kotlin.math.sin

/** A colour theme: the focus accent, a second accent, the "free" green, the aurora's sky and its four colour fields. */
data class Theme(val id: String, val name: String, val accent: Color, val violet: Color, val free: Color, val sky: List<Color>, val blobs: List<Color>)

val THEMES = listOf(
    Theme("aurora", "Aurora", Color(0xFF7FB4FF), Color(0xFFB388FF), Color(0xFF5CE0B1), listOf(Color(0xFF0B1230), Color(0xFF060914), Color(0xFF02030A)), listOf(Color(0xFF2F57FF), Color(0xFF8A3DFF), Color(0xFF12C2D9), Color(0xFFE04FB0))),
    Theme("ocean", "Ocean", Color(0xFF5FD4FF), Color(0xFF6FA8FF), Color(0xFF7CF2C4), listOf(Color(0xFF061C2A), Color(0xFF04101C), Color(0xFF02060C)), listOf(Color(0xFF0E7CFF), Color(0xFF00B7C2), Color(0xFF1E4DFF), Color(0xFF2EE6A6))),
    Theme("ember", "Ember", Color(0xFFFFB46B), Color(0xFFFF7AA2), Color(0xFFFFE26B), listOf(Color(0xFF2A0F12), Color(0xFF150709), Color(0xFF080304)), listOf(Color(0xFFFF5E3A), Color(0xFFD92C7A), Color(0xFFFFA62B), Color(0xFF7A1FA2))),
    Theme("forest", "Forest", Color(0xFF8BE39C), Color(0xFF5CE0B1), Color(0xFFE2F56B), listOf(Color(0xFF0A2016), Color(0xFF05120C), Color(0xFF020705)), listOf(Color(0xFF1E8C5A), Color(0xFF0FA3A3), Color(0xFF3F7F1F), Color(0xFF9BD12A))),
    Theme("mono", "Graphite", Color(0xFFE0E4EC), Color(0xFFB8BEC8), Color(0xFF9CE0C4), listOf(Color(0xFF15171C), Color(0xFF0C0D11), Color(0xFF050507)), listOf(Color(0xFF3A3F4A), Color(0xFF5A6070), Color(0xFF2A2E38), Color(0xFF4A4F5C))),
)

/**
 * Palette: near-black base, cool white glass, the theme's accents. `theme`, `motion` and `blur`
 * are Compose state set from Settings, so every surface recolours the moment they change.
 */
object Lumen {
    var theme by mutableStateOf(THEMES[0])
    var motion by mutableStateOf(true)
    var blur by mutableStateOf(true)
    val Base = Color(0xFF05070F)
    val Accent: Color get() = theme.accent
    val Violet: Color get() = theme.violet
    val Free: Color get() = theme.free
    val Text = Color.White
    val TextDim = Color.White.copy(alpha = 0.66f)
    val TextFaint = Color.White.copy(alpha = 0.42f)
}

/** Outfit (SIL OFL), one variable file; a geometric face that reads as "designed", not "default". */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val LumenFont = FontFamily(
    Font(R.font.outfit_variable, weight = FontWeight.Light, variationSettings = FontVariation.Settings(FontVariation.weight(300))),
    Font(R.font.outfit_variable, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.outfit_variable, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.outfit_variable, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.outfit_variable, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

@Composable
fun LumenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lumen.Accent, background = Lumen.Base, surface = Lumen.Base,
            onBackground = Lumen.Text, onSurface = Lumen.Text,
        ),
    ) {
        CompositionLocalProvider(LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = LumenFont), content = content)
    }
}

// ------------------------------------------------------------------ aurora

/**
 * The living backdrop: four soft colour fields drifting slowly on Lissajous paths, and a thin
 * field of stars drifting the other way. `t` is seconds, advanced by the screen's frame clock
 * at ~15 fps; `w`/`h` is the root size so a glass surface anywhere can redraw the same field.
 */
class AuroraState {
    var t by mutableFloatStateOf(0f)
    var w by mutableFloatStateOf(1920f)
    var h by mutableFloatStateOf(1080f)
}

val LocalAurora = staticCompositionLocalOf { AuroraState() }
val LocalHaze = staticCompositionLocalOf<HazeState?> { null }

private class Blob(val cx: Float, val cy: Float, val r: Float, val color: Color)

private fun blobs(t: Float, w: Float, h: Float): List<Blob> {
    fun drift(speed: Float, phase: Float) = sin(t * speed + phase)
    val c = Lumen.theme.blobs
    return listOf(
        Blob(w * (0.22f + 0.09f * drift(0.11f, 0.0f)), h * (0.26f + 0.12f * drift(0.09f, 1.3f)), w * 0.44f, c[0]),
        Blob(w * (0.80f + 0.08f * drift(0.08f, 2.1f)), h * (0.16f + 0.14f * drift(0.13f, 0.4f)), w * 0.38f, c[1]),
        Blob(w * (0.62f + 0.11f * drift(0.07f, 3.0f)), h * (0.94f + 0.07f * drift(0.10f, 2.2f)), w * 0.50f, c[2]),
        Blob(w * (0.08f + 0.07f * drift(0.12f, 4.1f)), h * (0.86f + 0.09f * drift(0.08f, 5.0f)), w * 0.32f, c[3]),
    )
}

/** Draws the aurora in root coordinates. Callers that sit elsewhere translate first. */
fun DrawScope.drawAurora(t: Float, w: Float, h: Float, intensity: Float = 1f, stars: Boolean = false) {
    val sky = Lumen.theme.sky
    drawRect(
        Brush.verticalGradient(0f to sky[0], 0.5f to sky[1], 1f to sky[2], startY = 0f, endY = h),
        topLeft = Offset.Zero, size = Size(w, h),
    )
    for (b in blobs(t, w, h)) {
        val c = Offset(b.cx, b.cy)
        drawCircle(
            Brush.radialGradient(
                0f to b.color.copy(alpha = 0.40f * intensity), 0.5f to b.color.copy(alpha = 0.13f * intensity), 1f to Color.Transparent,
                center = c, radius = b.r,
            ),
            radius = b.r, center = c,
        )
    }
    if (stars) {
        // 90 pinpoints on fixed pseudo-random seats, drifting left very slowly, each twinkling at its own pace.
        for (i in 0 until 90) {
            val seed = i * 7919
            val fx = ((seed * 1103515245 + 12345) ushr 8 and 0xFFFF) / 65535f
            val fy = ((seed * 22695477 + 1) ushr 8 and 0xFFFF) / 65535f
            val x = ((fx * w - t * (4f + (i % 5))) % w + w) % w
            val y = fy * h
            val a = 0.18f + 0.22f * (0.5f + 0.5f * sin(t * (0.6f + (i % 7) * 0.17f) + i))
            drawCircle(Color.White.copy(alpha = a), radius = if (i % 9 == 0) 2.2f else 1.3f, center = Offset(x, y))
        }
    }
}

@Composable
fun FluidWallpaper(modifier: Modifier = Modifier) {
    val aurora = LocalAurora.current
    Box(
        modifier
            .fillMaxSize()
            .onSizeChanged { aurora.w = it.width.toFloat(); aurora.h = it.height.toFloat() }
            .drawBehind { drawAurora(aurora.t, aurora.w, aurora.h, stars = true) },
    )
}

// ------------------------------------------------------------------ glass

/**
 * Frosted glass for surfaces that live INSIDE the scrolling content (tiles, cards): redraws the
 * aurora under itself, brightened, with a white film and a top sheen. No blur pass is needed:
 * the aurora has no edges, so "blurred aurora" and "aurora" are the same picture, and this
 * costs one tile-sized gradient fill per blob.
 */
fun Modifier.glassBackdrop(focused: Boolean = false, active: Boolean = false): Modifier = composed {
    val aurora = LocalAurora.current
    var pos by remember { mutableStateOf(Offset.Zero) }
    this
        .onGloballyPositioned { pos = it.positionInRoot() }
        .drawBehind {
            // Apple-style glass: clear rather than milky. The aurora shows through, lifted; a thin
            // frost; light gathered at the top edge; a little shade at the bottom for thickness.
            translate(-pos.x, -pos.y) { drawAurora(aurora.t, aurora.w, aurora.h, intensity = 1.35f) }
            drawRect(Color.White.copy(alpha = if (focused) 0.26f else if (active) 0.13f else 0.075f))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.28f to Color.White.copy(alpha = 0.03f), 1f to Color.Transparent, startY = 0f, endY = size.height))
            drawRect(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f), startY = 0f, endY = size.height))
        }
}

/** A hairline rim, brighter where the light comes from (top-left), the way Apple's glass is lit. */
fun Modifier.specularEdge(corner: Dp, focused: Boolean = false): Modifier = drawWithContent {
    drawContent()
    val stroke = if (focused) 2.5.dp.toPx() else 1.dp.toPx()
    val r = corner.toPx().coerceAtMost(size.minDimension / 2)
    // the rim: brightest where the light comes from (top-left), catching again at bottom-right
    val rim = if (focused) Brush.linearGradient(
        0f to Color.White, 0.45f to Color.White.copy(alpha = 0.92f), 1f to Color.White,
        start = Offset.Zero, end = Offset(size.width, size.height),
    ) else Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.55f), 0.45f to Color.White.copy(alpha = 0.08f), 1f to Color.White.copy(alpha = 0.28f),
        start = Offset.Zero, end = Offset(size.width, size.height),
    )
    drawRoundRect(brush = rim, cornerRadius = CornerRadius(r), style = Stroke(width = stroke))
    // the inner edge: light bent through the thickness of the glass, a hair inside the rim
    val inset = stroke + 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(0f to Color.White.copy(alpha = if (focused) 0.30f else 0.14f), 0.5f to Color.Transparent, 1f to Color.White.copy(alpha = 0.06f)),
        topLeft = Offset(inset, inset), size = androidx.compose.ui.geometry.Size(size.width - 2 * inset, size.height - 2 * inset),
        cornerRadius = CornerRadius((r - inset).coerceAtLeast(0f)), style = Stroke(width = 1.dp.toPx()),
    )
}

/**
 * A focusable glass tile. Focus: brighter film, white-to-accent edge, accent glow, scale-up,
 * and a band of light that sweeps across once, the way light crosses a pane that just moved.
 * Visuals are driven by onFocusChanged, not the Surface's interaction stream (which stops
 * reporting on this TV after an overlay restores focus programmatically).
 * `active` marks a selected-but-unfocused state (the current tab) with a brighter film.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GlassTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    corner: Dp = 20.dp,
    focusedScale: Float = 1.07f,
    active: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) focusedScale else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 420f), label = "tileScale")
    val glow by animateDpAsState(if (focused) 26.dp else 0.dp, animationSpec = tween(180), label = "tileGlow")
    // The focused tile throws a glow in the theme's accent, so the eye finds it from across the room.
    val glowColor = if (focused) Lumen.Accent else Color.Black.copy(alpha = 0.6f)
    val sweep = remember { Animatable(2f) }
    LaunchedEffect(focused) {
        if (focused) { sweep.snapTo(-0.7f); sweep.animateTo(1.7f, tween(700, easing = FastOutSlowInEasing)) }
    }
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier
            .onFocusChanged { focused = it.isFocused }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(elevation = glow, shape = shape, ambientColor = glowColor, spotColor = glowColor),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, pressedContainerColor = Color.Transparent,
            contentColor = Lumen.Text, focusedContentColor = Lumen.Text, pressedContentColor = Lumen.Text,
        ),
        border = ClickableSurfaceDefaults.border(border = Border.None, focusedBorder = Border.None, pressedBorder = Border.None),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f, pressedScale = 1f),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .glassBackdrop(focused, active)
                .drawWithContent {
                    drawContent()
                    val p = sweep.value
                    if (p in -0.7f..1.7f) {
                        val x = p * size.width
                        drawRect(
                            Brush.linearGradient(
                                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.18f), 1f to Color.Transparent,
                                start = Offset(x - size.width * 0.35f, 0f), end = Offset(x + size.width * 0.15f, size.height),
                            ),
                        )
                    }
                }
                .specularEdge(corner, focused),
        ) {
            content()
        }
    }
}

/**
 * A non-focusable glass panel. With `blur` and a HazeState in scope it is REAL frosted glass:
 * whatever scrolls beneath it (posters, banners, text) is blurred through it. Use that for
 * surfaces drawn above the content layer (top bar, menus, overlays). Inside the content layer
 * pass `blur = false` and it falls back to the aurora backdrop.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 28.dp,
    blur: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(corner)
    val haze = LocalHaze.current
    val surface = if (blur && Lumen.blur && haze != null) {
        Modifier.clip(shape).glassBackdrop(false).hazeEffect(
            state = haze,
            style = HazeStyle(
                backgroundColor = Color.Transparent,
                tints = listOf(HazeTint(Color.White.copy(alpha = 0.13f))),
                blurRadius = 34.dp,
                noiseFactor = 0.012f,
            ),
        ) {
            // Haze leaves blur OFF on API 31 (RenderEffect quirks); this TV is API 31 and renders it fine.
            blurEnabled = true
            inputScale = HazeInputScale.Fixed(0.5f)
        }.drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.09f), 1f to Color.Transparent, endY = size.height * 0.4f))
        }
    } else {
        Modifier.clip(shape).glassBackdrop(false)
    }
    Box(modifier.then(surface).specularEdge(corner), content = content)
}

/** A rounded glass pill with a label: tabs, actions, steppers. `selected` brightens the glass. */
@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: Dp = 44.dp,
    accent: Color? = null,
    fontSize: TextUnit = 16.sp,
    padding: Dp = 22.dp,
) {
    GlassTile(onClick = onClick, corner = 999.dp, focusedScale = 1.04f, active = selected, modifier = modifier.height(height).width(IntrinsicSize.Max)) {
        Text(
            label,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = padding),
            fontSize = fontSize,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = accent ?: if (selected) Lumen.Text else Lumen.TextDim,
            maxLines = 1,
        )
    }
}

/** See-through glass for cards over video: a light smoke tint for legibility, light along the top, the lit rim. */
fun Modifier.smokeGlass(corner: Dp): Modifier {
    val shape = RoundedCornerShape(corner)
    return this
        .clip(shape)
        .drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.22f), 1f to Color.Black.copy(alpha = 0.40f)))
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.10f), 0.35f to Color.Transparent, endY = size.height))
        }
        .specularEdge(corner)
}

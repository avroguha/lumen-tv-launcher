package org.idktech.lumen.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.idktech.lumen.data.Assistant
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * The voice screen for Lumen's own commands: three ribbons of light that move with the
 * microphone, what was heard, what Lumen answered. OK listens again; Back closes. In search
 * mode the words go to the search field instead. Anything Lumen does not understand is
 * handed to Google Assistant, and the pill at the bottom opens it directly.
 */
@Composable
fun JarvisScreen(assistant: Assistant, forSearch: Boolean, onListen: () -> Unit, onGoogle: () -> Unit) {
    val aurora = LocalAurora.current
    val fr = remember { FocusRequester() }
    val level by animateFloatAsState(assistant.level, animationSpec = tween(100), label = "mic")
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() }; onListen() }

    val phase = assistant.phase
    val phaseColor = when (phase) {
        Assistant.Phase.LISTENING -> Lumen.Accent
        Assistant.Phase.THINKING -> Lumen.Violet
        Assistant.Phase.SPEAKING -> Lumen.Free
        Assistant.Phase.ERROR -> Color(0xFFFF6B8A)
        Assistant.Phase.IDLE -> Lumen.TextDim
    }
    val phaseLabel = when (phase) {
        Assistant.Phase.LISTENING -> "Listening"
        Assistant.Phase.THINKING -> "Thinking"
        Assistant.Phase.SPEAKING -> if (forSearch) "Searching" else "Lumen"
        Assistant.Phase.ERROR -> "Hmm"
        Assistant.Phase.IDLE -> "Press OK to speak"
    }
    val amp by animateFloatAsState(
        when (phase) {
            Assistant.Phase.LISTENING -> 0.14f + 0.86f * level
            Assistant.Phase.THINKING -> 0.30f
            Assistant.Phase.SPEAKING -> 0.42f
            else -> 0.06f
        },
        animationSpec = tween(140), label = "amp",
    )
    val speed = when (phase) { Assistant.Phase.THINKING -> 5.5f; Assistant.Phase.SPEAKING -> 3.6f; else -> 2.2f }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(fr)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && (e.key == Key.DirectionCenter || e.key == Key.Enter)) { onListen(); true } else false
            },
    ) {
        Veil()
        Column(Modifier.fillMaxSize().padding(top = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.width(700.dp).height(170.dp)) {
                val w = size.width; val h = size.height; val cy = h / 2
                val t = aurora.t
                // halo
                drawRect(Brush.radialGradient(0f to phaseColor.copy(alpha = 0.22f + 0.25f * amp), 1f to Color.Transparent, center = Offset(w / 2, cy), radius = w * 0.42f))
                // three ribbons, each its own frequency and colour, windowed so they rest at the edges
                val ribbons = listOf(
                    Triple(Lumen.Accent, 1.0f, 0f), Triple(Lumen.Violet, 1.45f, 2.1f), Triple(Lumen.Free, 0.8f, 4.2f),
                )
                ribbons.forEachIndexed { k, (color, f, ph) ->
                    val path = Path()
                    var x = 0f
                    val step = 6f
                    while (x <= w) {
                        val u = x / w
                        val window = sin(u * PI).toFloat().pow(1.6f)
                        val a = h * 0.40f * amp * window * (0.75f + 0.25f * sin(t * 1.3f + k))
                        val y = cy + a * sin(2f * PI.toFloat() * f * u * 2.2f + ph + t * speed * (1f + k * 0.15f)) +
                            a * 0.35f * sin(2f * PI.toFloat() * f * u * 5.1f - t * speed * 0.7f + ph)
                        if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                        x += step
                    }
                    drawPath(path, color.copy(alpha = 0.16f), style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path, color.copy(alpha = 0.92f), style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                drawLine(Color.White.copy(alpha = 0.10f), Offset(w * 0.08f, cy), Offset(w * 0.92f, cy), strokeWidth = 1f)
            }
            Spacer(Modifier.height(6.dp))
            Text(phaseLabel, fontSize = 13.sp, letterSpacing = 4.sp, color = phaseColor)
            Spacer(Modifier.height(16.dp))
            Text(
                if (assistant.heard.isBlank()) (if (phase == Assistant.Phase.LISTENING) "…" else "") else "“${assistant.heard}”",
                fontSize = 28.sp, fontWeight = FontWeight.Light, color = Lumen.Text, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
            Spacer(Modifier.height(12.dp))
            if (assistant.reply.isNotBlank()) {
                Text(assistant.reply, fontSize = 19.sp, color = Lumen.Text.copy(alpha = 0.9f), textAlign = TextAlign.Center, lineHeight = 26.sp, modifier = Modifier.fillMaxWidth(0.62f))
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val hints = if (forSearch) listOf("“Inception”", "“Mirzapur”", "“Netflix”")
                else listOf("“Open Netflix”", "“Switch to HDMI 1”", "“Play Mirzapur”", "“Weather tomorrow”", "“Volume up”")
                hints.forEach { OfferBadge(it, false) }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 30.dp)) {
                Text("Anything else goes to", fontSize = 12.sp, color = Lumen.TextFaint)
                Spacer(Modifier.width(10.dp))
                Pill("Google Assistant", onClick = onGoogle, height = 34.dp, fontSize = 13.sp, padding = 14.dp)
            }
        }
    }
}

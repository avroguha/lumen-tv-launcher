package org.idktech.lumen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Line-art weather icons drawn on a canvas, so no icon font or emoji is needed on the TV. */
@Composable
fun WeatherGlyph(code: Int, isDay: Boolean, size: Dp = 28.dp, color: Color = Color.White) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
        when (code) {
            0, 1 -> if (isDay) sun(s, color, stroke) else moon(s, color, stroke)
            2 -> { if (isDay) sun(s * 0.6f, color, stroke, Offset(s * 0.62f, s * 0.36f)) else moon(s * 0.6f, color, stroke, Offset(s * 0.62f, s * 0.36f)); cloud(s, color, stroke, Offset(0f, s * 0.12f)) }
            3, 45, 48 -> cloud(s, color, stroke)
            51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> { cloud(s, color, stroke, Offset(0f, -s * 0.06f)); drops(s, color, stroke) }
            71, 73, 75, 77, 85, 86 -> { cloud(s, color, stroke, Offset(0f, -s * 0.06f)); flakes(s, color, stroke) }
            95, 96, 99 -> { cloud(s, color, stroke, Offset(0f, -s * 0.06f)); bolt(s, color) }
            else -> cloud(s, color, stroke)
        }
    }
}

private fun DrawScope.sun(s: Float, color: Color, stroke: Stroke, center: Offset = Offset(s / 2, s / 2)) {
    drawCircle(color, radius = s * 0.2f, center = center, style = stroke)
    for (i in 0 until 8) {
        val a = i * (Math.PI / 4).toFloat()
        val r1 = s * 0.3f; val r2 = s * 0.42f
        drawLine(color, Offset(center.x + cos(a) * r1, center.y + sin(a) * r1), Offset(center.x + cos(a) * r2, center.y + sin(a) * r2), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.moon(s: Float, color: Color, stroke: Stroke, center: Offset = Offset(s / 2, s / 2)) {
    drawCircle(color, radius = s * 0.32f, center = center, style = stroke)
    drawCircle(Color.Transparent, radius = s * 0.26f, center = Offset(center.x + s * 0.16f, center.y - s * 0.12f))
    drawCircle(color.copy(alpha = 0.35f), radius = s * 0.1f, center = Offset(center.x - s * 0.08f, center.y + s * 0.06f))
}

private fun DrawScope.cloud(s: Float, color: Color, stroke: Stroke, offset: Offset = Offset.Zero) {
    val base = s * 0.66f + offset.y
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(s * 0.22f + offset.x, base)
        lineTo(s * 0.78f + offset.x, base)
        arcTo(androidx.compose.ui.geometry.Rect(Offset(s * 0.62f + offset.x, s * 0.36f + offset.y), androidx.compose.ui.geometry.Size(s * 0.32f, s * 0.30f)), 90f, -180f, false)
        arcTo(androidx.compose.ui.geometry.Rect(Offset(s * 0.34f + offset.x, s * 0.22f + offset.y), androidx.compose.ui.geometry.Size(s * 0.40f, s * 0.40f)), 20f, -170f, false)
        arcTo(androidx.compose.ui.geometry.Rect(Offset(s * 0.08f + offset.x, s * 0.38f + offset.y), androidx.compose.ui.geometry.Size(s * 0.30f, s * 0.28f)), -90f, -180f, false)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drops(s: Float, color: Color, stroke: Stroke) {
    for (i in 0 until 3) {
        val x = s * (0.3f + i * 0.2f)
        drawLine(color, Offset(x, s * 0.72f), Offset(x - s * 0.06f, s * 0.9f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

private fun DrawScope.flakes(s: Float, color: Color, stroke: Stroke) {
    for (i in 0 until 3) drawCircle(color, radius = s * 0.045f, center = Offset(s * (0.3f + i * 0.2f), s * 0.82f))
}

private fun DrawScope.bolt(s: Float, color: Color) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(s * 0.52f, s * 0.62f); lineTo(s * 0.40f, s * 0.80f); lineTo(s * 0.50f, s * 0.80f); lineTo(s * 0.44f, s * 0.96f)
        lineTo(s * 0.62f, s * 0.76f); lineTo(s * 0.52f, s * 0.76f); lineTo(s * 0.58f, s * 0.62f); close()
    }
    drawPath(p, color)
}

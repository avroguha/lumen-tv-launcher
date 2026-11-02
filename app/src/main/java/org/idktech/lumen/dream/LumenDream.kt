package org.idktech.lumen.dream

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.service.dreams.DreamService
import android.view.View
import androidx.core.content.res.ResourcesCompat
import androidx.compose.ui.graphics.toArgb
import org.idktech.lumen.R
import org.idktech.lumen.data.SettingsStore
import org.idktech.lumen.overlay.ClockOverlayService
import org.idktech.lumen.ui.THEMES
import org.idktech.lumen.data.SubscriptionStore
import org.idktech.lumen.data.WeatherRepo
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZonedDateTime
import java.util.Date
import java.util.Locale
import kotlin.math.sin

/**
 * The screensaver: the same aurora, a large clock, the date, today's weather and the next
 * renewal. Plain Android View drawing (a DreamService has no Compose lifecycle to lean on).
 * Select it in Settings -> System -> Screen saver, or over ADB:
 *   settings put secure screensaver_components org.idktech.lumen/.dream.LumenDream
 */
class LumenDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(DreamView(this))
    }

    // The screensaver has its own large clock; the floating one would sit on top of it.
    override fun onDreamingStarted() { super.onDreamingStarted(); ClockOverlayService.sync(this, ClockOverlayService.ACTION_HIDE) }
    override fun onDreamingStopped() { ClockOverlayService.sync(this, ClockOverlayService.ACTION_SHOW); super.onDreamingStopped() }
}

class DreamView(ctx: Context) : View(ctx) {
    private val start = System.nanoTime()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = ResourcesCompat.getFont(ctx, R.font.outfit_variable)
    }
    private val prefs = SettingsStore.load(ctx)
    private val theme = THEMES.firstOrNull { it.id == prefs.theme } ?: THEMES[0]
    private val sky = theme.sky.map { it.toArgb() }
    private val blobs = listOf(
        floatArrayOf(0.22f, 0.26f, 0.44f, 0.11f, 0.09f, 0.0f, 1.3f) to theme.blobs[0].toArgb(),
        floatArrayOf(0.80f, 0.16f, 0.38f, 0.08f, 0.13f, 2.1f, 0.4f) to theme.blobs[1].toArgb(),
        floatArrayOf(0.62f, 0.94f, 0.50f, 0.07f, 0.10f, 3.0f, 2.2f) to theme.blobs[2].toArgb(),
        floatArrayOf(0.08f, 0.86f, 0.32f, 0.12f, 0.08f, 4.1f, 5.0f) to theme.blobs[3].toArgb(),
    )
    private val accent = theme.accent.toArgb()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.nanoTime() - start) / 1_000_000_000f
        fill.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(sky[0], sky[1], sky[2]), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, fill)
        for ((p, col) in blobs) {
            val cx = w * (p[0] + 0.09f * sin(t * p[3] + p[5])); val cy = h * (p[1] + 0.12f * sin(t * p[4] + p[6])); val r = w * p[2]
            fill.shader = RadialGradient(cx, cy, r, intArrayOf(withAlpha(col, 0.34f), withAlpha(col, 0.11f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(cx, cy, r, fill)
        }
        fill.shader = null
        fill.color = Color.WHITE
        for (i in 0 until 70) {
            val seed = i * 7919
            val fx = ((seed * 1103515245 + 12345) ushr 8 and 0xFFFF) / 65535f
            val fy = ((seed * 22695477 + 1) ushr 8 and 0xFFFF) / 65535f
            val x = ((fx * w - t * (3f + (i % 5))) % w + w) % w
            fill.alpha = (255 * (0.15f + 0.2f * (0.5f + 0.5f * sin(t * (0.6f + (i % 7) * 0.17f) + i)))).toInt()
            c.drawCircle(x, fy * h, if (i % 9 == 0) 2.2f else 1.3f, fill)
        }
        fill.alpha = 255

        // Content drifts slowly (a few pixels a minute) so nothing sits on the same pixels for hours.
        val dx = w * 0.03f * sin(t * 0.012f); val dy = h * 0.025f * sin(t * 0.010f + 1f)
        val now = Date()
        val zdt = ZonedDateTime.now()
        val (hhmm, suffix) = prefs.timeParts(context, zdt)
        val cx = w * 0.40f + dx; val cy = h * 0.46f + dy
        text.textAlign = Paint.Align.CENTER
        text.textSize = h * 0.26f; text.alpha = 255
        c.drawText(hhmm, cx, cy, text)
        if (suffix != null) {
            text.textAlign = Paint.Align.LEFT
            text.textSize = h * 0.07f; text.alpha = 190
            val tw = run { text.textSize = h * 0.26f; val m = text.measureText(hhmm); text.textSize = h * 0.07f; m }
            c.drawText(suffix, cx + tw / 2 + h * 0.02f, cy, text)
            text.textAlign = Paint.Align.CENTER
        }
        text.textSize = h * 0.045f; text.alpha = 200
        c.drawText(SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now), cx, cy + h * 0.075f, text)
        drawCalendar(c, w * 0.80f + dx, h * 0.30f + dy, h * 0.040f, zdt.toLocalDate())
        val wx = WeatherRepo.cached(context)
        if (wx != null) {
            text.textSize = h * 0.04f; text.alpha = 170
            c.drawText("${wx.label} · ${prefs.temp(wx.tempC)}  ·  H ${prefs.temp(wx.hiC)} L ${prefs.temp(wx.loC)}  ·  ${wx.city}", cx, cy + h * 0.14f, text)
        }
        val subs = runCatching { SubscriptionStore.load(context) }.getOrDefault(emptyList())
        val next = subs.mapNotNull { s -> s.nextRenewal()?.let { s to it } }.minByOrNull { it.second }
        if (next != null) {
            text.textSize = h * 0.028f; text.alpha = 120
            c.drawText("${next.first.name} renews ${next.second.dayOfMonth} ${next.second.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}", cx, cy + h * 0.20f, text)
        }
        postInvalidateDelayed(33)
    }

    /** A month grid: weekday initials, the days, today on an accent disc. `cell` is the cell size in px. */
    private fun drawCalendar(c: Canvas, cx: Float, top: Float, cell: Float, today: LocalDate) {
        val ym = YearMonth.from(today)
        val firstDow = ym.atDay(1).dayOfWeek.value % 7
        val left = cx - cell * 3.5f
        text.textAlign = Paint.Align.CENTER
        text.textSize = cell * 0.55f; text.alpha = 210
        c.drawText(ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) + " " + ym.year, cx, top, text)
        text.textSize = cell * 0.36f; text.alpha = 120
        val days = listOf("S", "M", "T", "W", "T", "F", "S")
        for (i in 0 until 7) c.drawText(days[i], left + cell * (i + 0.5f), top + cell * 0.9f, text)
        text.textSize = cell * 0.42f
        for (d in 1..ym.lengthOfMonth()) {
            val idx = firstDow + d - 1
            val x = left + cell * (idx % 7 + 0.5f); val y = top + cell * (1.75f + idx / 7)
            if (d == today.dayOfMonth) {
                fill.color = accent; fill.alpha = 255
                c.drawCircle(x, y - cell * 0.15f, cell * 0.36f, fill)
                text.color = Color.BLACK; text.alpha = 255
                c.drawText("$d", x, y, text)
                text.color = Color.WHITE
            } else {
                text.alpha = 190
                c.drawText("$d", x, y, text)
            }
        }
        fill.color = Color.WHITE
    }

    private fun withAlpha(color: Int, a: Float) = (color and 0x00FFFFFF) or ((a * 255).toInt() shl 24)
}

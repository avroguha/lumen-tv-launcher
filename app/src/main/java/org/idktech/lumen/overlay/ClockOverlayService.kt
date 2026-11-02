package org.idktech.lumen.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.content.res.ResourcesCompat
import org.idktech.lumen.R
import org.idktech.lumen.data.ClockStore
import org.idktech.lumen.data.Prefs
import org.idktech.lumen.data.SettingsStore
import java.time.ZoneId
import java.time.ZonedDateTime

private const val TAG = "Lumen.Clock"

/**
 * The small floating clock drawn over other apps and over video: a glass pill in a corner,
 * the time and, if one is set, the second city. It is a foreground service holding one
 * TYPE_APPLICATION_OVERLAY window (needs the "draw over other apps" permission, granted from
 * Lumen's Settings). Lumen hides it while Lumen itself is in front (the bar has its own clock)
 * and while the screensaver runs.
 */
class ClockOverlayService : Service() {
    private var wm: WindowManager? = null
    private var view: ClockView? = null
    private var hiddenByLumen = false
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            view?.invalidate()
            handler.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { view?.invalidate() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, notification())
        wm = getSystemService(WindowManager::class.java)
        registerReceiver(timeChanged, IntentFilter().apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) })
        handler.post(tick)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = SettingsStore.load(this)
        if (prefs.floatingClock == "off" || !Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        when (intent?.action) {
            ACTION_HIDE -> hiddenByLumen = true
            ACTION_SHOW -> hiddenByLumen = false
        }
        attach(prefs)
        view?.visibility = if (hiddenByLumen) View.GONE else View.VISIBLE
        return START_STICKY
    }

    private fun attach(prefs: Prefs) {
        val w = wm ?: return
        view?.let { runCatching { w.removeView(it) } }
        val v = ClockView(this, prefs)
        val size = if (prefs.floatingSize == "m") 1.4f else 1f
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = when (prefs.floatingClock) {
                "tl" -> Gravity.TOP or Gravity.START
                "bl" -> Gravity.BOTTOM or Gravity.START
                "br" -> Gravity.BOTTOM or Gravity.END
                else -> Gravity.TOP or Gravity.END
            }
            val m = (resources.displayMetrics.density * 26 * size).toInt()
            x = m; y = m
        }
        runCatching { w.addView(v, lp) }
            .onFailure { Log.w(TAG, "overlay refused", it); stopSelf(); return }
        view = v
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        runCatching { unregisterReceiver(timeChanged) }
        view?.let { v -> runCatching { wm?.removeView(v) } }
        view = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Floating clock", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        }
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setSmallIcon(R.drawable.ic_launcher).setContentTitle("Lumen clock").setContentText("Floating clock is on").setOngoing(true).build()
    }

    companion object {
        private const val CHANNEL = "clock"
        const val ACTION_SHOW = "org.idktech.lumen.clock.SHOW"
        const val ACTION_HIDE = "org.idktech.lumen.clock.HIDE"

        /** Applies the current settings: starts, re-lays out, or stops the overlay. */
        fun sync(ctx: Context, action: String? = null) {
            val prefs = SettingsStore.load(ctx)
            val i = Intent(ctx, ClockOverlayService::class.java).apply { if (action != null) setAction(action) }
            if (prefs.floatingClock == "off" || !Settings.canDrawOverlays(ctx)) { runCatching { ctx.stopService(i) }; return }
            runCatching { if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i) }
                .onFailure { Log.w(TAG, "cannot start clock overlay", it) }
        }
    }
}

/** The pill itself: translucent dark glass with a hairline, the time, the second city beneath. */
class ClockView(ctx: Context, private val prefs: Prefs) : View(ctx) {
    private val density = resources.displayMetrics.density
    private val scale = if (prefs.floatingSize == "m") 1.4f else 1f
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(140, 8, 10, 20) }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(90, 255, 255, 255); style = Paint.Style.STROKE; strokeWidth = density }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = ResourcesCompat.getFont(ctx, R.font.outfit_variable); textSize = 20 * density * scale }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(170, 255, 255, 255); typeface = big.typeface; textSize = 11 * density * scale }
    private val second: String? = ClockStore.secondZone(ctx)
    private val ampm = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 255, 255, 255); typeface = big.typeface; textSize = big.textSize * 0.42f }
    private val padX = 14 * density * scale
    private val padY = 8 * density * scale

    private data class Lines(val time: String, val suffix: String?, val second: String?)

    private fun lines(): Lines {
        val now = ZonedDateTime.now()
        val (t, suffix) = prefs.timeParts(context, now)
        val l2 = second?.let { z ->
            val (t2, s2) = prefs.timeParts(context, now.withZoneSameInstant(ZoneId.of(z)))
            "$t2${s2?.let { " $it" } ?: ""}  ${ClockStore.ZONES.firstOrNull { it.first == z }?.second ?: z}"
        }
        return Lines(t, suffix, l2)
    }

    private fun firstLineWidth(l: Lines) = big.measureText(l.time) + (l.suffix?.let { ampm.measureText(it) + 5 * density * scale } ?: 0f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val l = lines()
        val w = maxOf(firstLineWidth(l), l.second?.let { small.measureText(it) } ?: 0f) + padX * 2
        val h = padY * 2 + big.textSize * 1.05f + (if (l.second != null) small.textSize * 1.35f else 0f)
        setMeasuredDimension(w.toInt() + 1, h.toInt() + 1)
    }

    override fun onDraw(c: Canvas) {
        val l = lines()
        val r = RectF(density / 2, density / 2, width - density / 2, height - density / 2)
        val corner = (height / 2f).coerceAtMost(22 * density * scale)
        c.drawRoundRect(r, corner, corner, fill)
        c.drawRoundRect(r, corner, corner, edge)
        var y = padY + big.textSize * 0.92f
        c.drawText(l.time, padX, y, big)
        l.suffix?.let { c.drawText(it, padX + big.measureText(l.time) + 5 * density * scale, y, ampm) }
        if (l.second != null) { y += small.textSize * 1.3f; c.drawText(l.second, padX, y, small) }
    }
}

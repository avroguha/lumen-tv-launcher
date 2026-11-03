package org.idktech.lumen.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.tv.material3.Text
import org.idktech.lumen.MainActivity
import org.idktech.lumen.data.Catalog
import org.idktech.lumen.data.LANGUAGES
import org.idktech.lumen.data.Title
import org.idktech.lumen.ui.GlassPanel
import org.idktech.lumen.ui.Lumen
import org.idktech.lumen.ui.LumenTheme
import org.idktech.lumen.ui.OfferBadge
import org.idktech.lumen.ui.metaLine
import org.idktech.lumen.ui.glassBackdrop
import org.idktech.lumen.ui.specularEdge
import org.idktech.lumen.ui.smokeGlass
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A trailer playing in the YouTube app, run by the Home-button accessibility service:
 *  - the YouTube app plays it (the TV's web view decodes video but never shows the picture);
 *  - Lumen's details float over the video in an accessibility-overlay window, fading after a
 *    few seconds and coming back on any key;
 *  - Back ends it in one press and brings Lumen back where you were;
 *  - leaving YouTube any other way ends it quietly.
 * Works only while the service is bound; `start` says so, and the caller falls back.
 */
object TrailerSession {
    private const val YOUTUBE = "com.google.android.youtube.tv"
    private const val SHOW_MS = 9_000L

    @Volatile private var service: AccessibilityService? = null
    private var title: Title? = null
    private var started = 0L
    private var root: View? = null
    private var owner: OverlayOwner? = null
    private val visible = mutableStateOf(true)
    /** The highlighted choice on the panel: an app to watch it on, or the last one, "Back to Lumen". */
    private val selected = mutableStateOf(0)
    val selectedIndex: Int get() = selected.value
    private val main = Handler(Looper.getMainLooper())
    private val fade = Runnable { visible.value = false }

    val active: Boolean get() = title != null

    private fun log(m: String) = android.util.Log.i("Lumen.Overlay", m)
    // ------------------------------------------------------------ screen off over a playing app (hold Home)
    // The YouTube TV app pauses the moment another screen comes in front, so "screen off" over
    // it must be a window of this service, with YouTube still in front. Hold Back ~1 s while
    // YouTube (or YouTube Music) is in front to black the screen; any key brings it back.
    private val MEDIA_APPS = setOf(YOUTUBE, "com.google.android.youtube.tvmusic")
    @Volatile var frontApp: String = ""
    private var black: View? = null
    private var backDownAt = 0L
    private var backHeldFired = false
    private val holdBack = Runnable { backHeldFired = true; blackout() }

    private fun blackout() {
        val s = service ?: return
        if (black != null) return
        val v = View(s).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE,
        ).apply { screenBrightness = 0f }
        if (runCatching { s.getSystemService(WindowManager::class.java).addView(v, lp) }.isSuccess) { black = v; log("screen off over $frontApp") }
    }

    private fun wake() {
        val v = black ?: return
        log("screen on")
        runCatching { service?.getSystemService(WindowManager::class.java)?.removeView(v) }
        black = null
    }

    /** Keys for screen-off: true = taken. Runs before the trailer handling. */
    private fun screenOffKey(e: KeyEvent): Boolean {
        if (black != null) {
            // The release of the Back that was held to black the screen is not a wake-up press.
            if (e.keyCode == KeyEvent.KEYCODE_HOME && e.action == KeyEvent.ACTION_UP && backHeldFired) { backHeldFired = false; log("held Home released, staying dark"); return true }
            if (e.action == KeyEvent.ACTION_DOWN) log("wake on key ${e.keyCode}")
            if (e.action == KeyEvent.ACTION_UP) main.post { wake() }
            return true
        }
        // Hold Home (anywhere) = screen off; a short press is still Home. This replaces TCL's
        // long-press-Home settings shortcut.
        if (e.keyCode != KeyEvent.KEYCODE_HOME) return false
        val s = service ?: return false
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) log("home down, front=$frontApp")
        if (e.action == KeyEvent.ACTION_DOWN) {
            if (e.repeatCount == 0) { backDownAt = android.os.SystemClock.elapsedRealtime(); backHeldFired = false; main.postDelayed(holdBack, 1000) }
            return true
        }
        main.removeCallbacks(holdBack)
        // a short press is an ordinary Home
        if (!backHeldFired) main.post { s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        return true
    }

    fun attach(s: AccessibilityService) { service = s; log("service attached") }
    fun detach(s: AccessibilityService) { if (service === s) { end(bringLumen = false); service = null } }

    /** Plays `videoId` for `t` in YouTube with the details on top. False if the service is not running. */
    fun start(t: Title, videoId: String): Boolean {
        val s = service ?: run { log("start: no service"); return false }
        log("start ${t.title} $videoId")
        end(bringLumen = false)
        title = t; started = android.os.SystemClock.elapsedRealtime(); selected.value = 0
        Catalog.openYouTube(s, "https://www.youtube.com/watch?v=$videoId")
        showPanel(s, t)
        return true
    }

    /** The service's key filter: true = Lumen took the key. */
    /**
     * While the panel shows, the remote drives the panel: LEFT / RIGHT pick, OK acts. When it
     * has faded, the remote drives YouTube again, and the first press only brings the panel
     * back. Back always returns to Lumen. Media keys always go to YouTube.
     */
    fun onKey(e: KeyEvent): Boolean {
        if (screenOffKey(e)) return true
        val t = title ?: return false
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (!down) main.post { end(bringLumen = true) }
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE -> return false
        }
        if (!visible.value) {
            if (down && e.repeatCount == 0) main.post { peek() }
            return true
        }
        val count = t.offers.take(3).size + 1
        if (down) when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> main.post { selected.value = (selected.value - 1).coerceAtLeast(0); peek() }
            KeyEvent.KEYCODE_DPAD_RIGHT -> main.post { selected.value = (selected.value + 1).coerceAtMost(count - 1); peek() }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> main.post { peek() }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {}
            else -> return false
        } else if (e.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || e.keyCode == KeyEvent.KEYCODE_ENTER || e.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            main.post { choose(t, selected.value) }
        }
        return true
    }

    private fun choose(t: Title, i: Int) {
        val s = service ?: return
        val offer = t.offers.take(3).getOrNull(i)
        if (offer == null) { end(bringLumen = true); return }
        end(bringLumen = false)
        runCatching { org.idktech.lumen.data.HistoryStore.add(s, t) }
        Catalog.openOffer(s, offer.deeplink, offer.packageName, offer.name)
    }

    /** Window changes: a different app in front (not YouTube, not Lumen) ends the session. */
    private var clockShown: Boolean? = null

    fun onWindow(pkg: String) {
        if (!pkg.startsWith("com.android.systemui")) frontApp = pkg
        // The floating clock shows over other apps and hides over Lumen's own screens (home,
        // trailer, screensaver). System UI and Google's voice screen are passing overlays, not apps.
        if (!pkg.startsWith("com.android.systemui") && pkg != "com.google.android.katniss") {
            val show = !pkg.startsWith("org.idktech.lumen")
            if (show != clockShown) {
                clockShown = show
                service?.let { ClockOverlayService.sync(it, if (show) ClockOverlayService.ACTION_SHOW else ClockOverlayService.ACTION_HIDE) }
            }
        }
        if (black != null && pkg !in MEDIA_APPS && !pkg.startsWith("org.idktech.lumen")) main.post { wake() }
        if (active) log("window $pkg")
        if (!active || pkg == YOUTUBE || pkg.startsWith("org.idktech.lumen")) return
        if (android.os.SystemClock.elapsedRealtime() - started < 4_000) return   // YouTube is still opening
        main.post { end(bringLumen = false) }
    }

    fun end(bringLumen: Boolean) {
        if (title != null) log("end bringLumen=$bringLumen")
        val s = service
        main.removeCallbacks(fade)
        root?.let { v -> runCatching { s?.getSystemService(WindowManager::class.java)?.removeView(v) } }
        root = null; owner?.destroy(); owner = null
        val was = title; title = null
        if (bringLumen && was != null && s != null) runCatching {
            s.startActivity(Intent(s, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
    }

    private fun peek() { visible.value = true; main.removeCallbacks(fade); main.postDelayed(fade, SHOW_MS) }

    private fun showPanel(s: AccessibilityService, t: Title) {
        val wm = s.getSystemService(WindowManager::class.java)
        val o = OverlayOwner()
        val view = ComposeView(s).apply {
            setViewTreeLifecycleOwner(o)
            setViewTreeSavedStateRegistryOwner(o)
            setContent {
                LumenTheme {
                    AnimatedVisibility(visible = visible.value, enter = fadeIn(tween(250)), exit = fadeOut(tween(600))) {
                        Box(Modifier.fillMaxSize()) { Panel(t) }
                    }
                }
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val r = runCatching { wm.addView(view, lp) }
        if (r.isFailure) { log("panel refused: ${r.exceptionOrNull()}"); o.destroy(); return }
        log("panel shown")
        o.resume(); root = view; owner = o
        peek()
    }
}

@androidx.compose.runtime.Composable
private fun androidx.compose.foundation.layout.BoxScope.Panel(t: Title) {
    Box(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 34.dp).width(560.dp).smokeGlass(26.dp)) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Text(t.title, fontSize = 28.sp, fontWeight = FontWeight.Light, color = Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(metaLine(t) + (if (t.languages.isNotEmpty()) "   ·   " + t.languages.mapNotNull { c -> LANGUAGES.firstOrNull { it.first == c }?.second }.joinToString(", ") else ""),
                fontSize = 13.sp, color = Lumen.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Text(t.overview, fontSize = 14.sp, color = Lumen.Text.copy(alpha = 0.9f), maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Text("Watch on", fontSize = 12.sp, color = Lumen.TextFaint)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sel = selectedChoice()
                t.offers.take(3).forEachIndexed { i, o -> Choice(if (o.isFree) "${o.name} · Free" else o.name, i == sel, if (o.isFree) Lumen.Free else Lumen.Text); Spacer(Modifier.width(8.dp)) }
                Choice("Back to Lumen", sel == t.offers.take(3).size, Lumen.TextDim)
            }
            Spacer(Modifier.height(8.dp))
            Text("LEFT / RIGHT choose · OK open · Back return", fontSize = 11.sp, color = Lumen.TextFaint)
        }
    }
}

private fun selectedChoice(): Int = TrailerSession.selectedIndex

/** A choice on the panel, lit like a focused pill when it is the selected one. */
@androidx.compose.runtime.Composable
private fun Choice(label: String, on: Boolean, color: androidx.compose.ui.graphics.Color) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(999.dp)
    Box(
        Modifier
            .graphicsLayer(scaleX = if (on) 1.06f else 1f, scaleY = if (on) 1.06f else 1f)
            .clip(shape)
            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = if (on) 0.24f else 0.08f))
            .specularEdge(999.dp, on)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 14.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) color else color.copy(alpha = 0.7f), maxLines = 1)
    }
}

/** A lifecycle for a Compose tree living in a window of its own, outside any Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    init { saved.performRestore(null); registry.currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() { registry.currentState = Lifecycle.State.DESTROYED }
}

package org.idktech.lumen.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import org.idktech.lumen.data.SettingsStore
import org.idktech.lumen.ui.Lumen
import java.io.ByteArrayInputStream

/**
 * A free streaming website, full screen, driven by the remote like a TV app.
 *
 *  - Arrows move a highlight between the page's links, buttons and cards (assets/web/tvnav.js);
 *    OK presses the highlighted one with a real touch, so players get a genuine user gesture.
 *  - Hold OK for a pointer, for the odd page the highlight cannot reach (a player inside a
 *    cross-site frame, a canvas). Arrows move it, OK clicks, hold OK again to leave.
 *  - While a video fills the screen: OK plays / pauses, LEFT / RIGHT skip 10 seconds.
 *  - Back goes back a page; hold Back to leave the site.
 *  - Ad and tracker hosts are refused before they load (AdBlocker), pop-up windows never open,
 *    and a page can only open a new window onto its own site, and only when you pressed something.
 *
 * Runs in its own task, so Home returns to Lumen and the site waits where you left it.
 */
class WebPlayerActivity : Activity() {
    companion object {
        const val EXTRA_URL = "lumen.url"
        const val EXTRA_NAME = "lumen.name"
        const val EXTRA_DESKTOP = "lumen.desktop"
        private const val TAG = "Lumen.Web"
        private const val LONG_PRESS_MS = 550L
        private const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/157.0.0.0 Safari/537.36"
        private const val COSMETIC = "ins.adsbygoogle,iframe[src*=\"doubleclick.net\"],iframe[id^=\"google_ads_iframe\"]," +
            "div[id^=\"div-gpt-ad\"],div[id^=\"google_ads\"],[data-google-query-id]{display:none !important}"
    }

    private lateinit var root: FrameLayout
    private lateinit var web: WebView
    private lateinit var pointer: PointerView
    private lateinit var hint: TextView
    private lateinit var osd: OsdView
    private var filledFilm = false
    private val main = Handler(Looper.getMainLooper())
    private var navJs = ""
    private var adBlock = true
    private var siteName = ""
    private var defaultUa = ""

    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    private var pointerOn = false
    private var okHeld = false
    private var backHeld = false
    private var lastMove = 0L

    private val okLong = Runnable { okHeld = true; setPointer(!pointerOn) }
    private val backLong = Runnable { backHeld = true; Log.i(TAG, "hold Back: leaving $siteName"); finish() }
    private val hideHint = Runnable { hint.animate().alpha(0f).setDuration(400).start() }
    private val hideOsd = Runnable { osd.animate().alpha(0f).setDuration(400).start() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AdBlocker.start(this)
        adBlock = SettingsStore.load(this).adBlock
        val accent = String.format("#%06X", 0xFFFFFF and Lumen.Accent.toArgb())
        navJs = assets.open("web/tvnav.js").bufferedReader().use { it.readText() }
            .replace("__ACCENT__", accent)
            .replace("__COSMETIC__", if (adBlock) COSMETIC else "")

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this)
        defaultUa = web.settings.userAgentString
        configure(web)
        root.addView(web, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        pointer = PointerView(this).apply { visibility = View.GONE }
        root.addView(pointer, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        hint = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 15f
            setPadding(dp(22), dp(10), dp(22), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(0xD0101418.toInt()); setStroke(dp(1), 0x40FFFFFF) }
        }
        osd = OsdView(this).apply { alpha = 0f }
        root.addView(osd, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM))
        root.addView(hint, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28) })
        setContentView(root)
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(i: Intent) {
        val url = i.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        siteName = i.getStringExtra(EXTRA_NAME) ?: (Uri.parse(url).host ?: url)
        web.settings.userAgentString = if (i.getBooleanExtra(EXTRA_DESKTOP, true)) DESKTOP_UA else defaultUa
        AdBlocker.blocked.set(0)
        setPointer(false, quiet = true)
        Log.i(TAG, "open $siteName: $url (ad blocker ${if (adBlock) "on" else "off"}, ${AdBlocker.listSize} hosts listed)")
        web.loadUrl(url)
        say("$siteName  ·  arrows move  ·  hold OK for a pointer  ·  hold Back to leave", 6000)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure(w: WebView) {
        // Inspectable over adb (chrome://inspect) for fixing a site that misbehaves; adb is already trusted.
        WebView.setWebContentsDebuggingEnabled(true)
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        w.setBackgroundColor(Color.BLACK)
        w.addJavascriptInterface(Bridge(), "LumenWeb")

        w.webViewClient = object : WebViewClient() {
            private val empty = ByteArray(0)

            override fun shouldInterceptRequest(view: WebView, req: WebResourceRequest): WebResourceResponse? {
                if (!adBlock || !AdBlocker.isBlocked(req.url)) return null
                val n = AdBlocker.blocked.incrementAndGet()
                if (n <= 20 || n % 50 == 0) Log.d(TAG, "blocked #$n ${req.url.host}${req.url.path?.take(40) ?: ""}")
                return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(empty))
            }

            override fun shouldOverrideUrlLoading(view: WebView, req: WebResourceRequest): Boolean {
                val u = req.url
                if (u.scheme != "http" && u.scheme != "https") { Log.i(TAG, "refused ${u.scheme}: link"); return true }
                if (adBlock && req.isForMainFrame && AdBlocker.isBlocked(u.host)) { Log.i(TAG, "refused navigation to ad host ${u.host}"); return true }
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { Log.i(TAG, "page $url") }
            override fun onPageCommitVisible(view: WebView, url: String?) = inject()
            override fun onPageFinished(view: WebView, url: String?) {
                inject()
                Log.i(TAG, "loaded $url, ${AdBlocker.blocked.get()} ad requests blocked so far")
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                Log.w(TAG, "certificate error on ${error.url}: refused"); handler.cancel()
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullView?.let { root.removeView(it) }
                fullView = view; fullCallback = callback
                root.addView(view, 1, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                Log.i(TAG, "full screen")
            }
            override fun onHideCustomView() {
                fullView?.let { root.removeView(it) }
                fullView = null; fullCallback = null
                Log.i(TAG, "full screen ended")
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                // Protected (DRM) video only; never the camera or the microphone.
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID in request.resources) request.grant(arrayOf(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID))
                else request.deny()
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                if (!isUserGesture) { Log.i(TAG, "pop-up blocked (no key press)"); return false }
                // Learn where the new window wants to go, then decide: same site opens here, the rest never opens.
                val probe = WebView(this@WebPlayerActivity)
                probe.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        val target = r.url
                        if (sameSite(target.host, Uri.parse(web.url ?: "").host) && !AdBlocker.isBlocked(target.host)) {
                            Log.i(TAG, "new window opened here: $target"); web.loadUrl(target.toString())
                        } else Log.i(TAG, "pop-up blocked: ${target.host}")
                        main.post { v.destroy() }
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = probe
                resultMsg.sendToTarget()
                return true
            }
            override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private fun sameSite(a: String?, b: String?): Boolean {
        fun base(h: String?): String? = h?.lowercase()?.split('.')?.let { p ->
            if (p.size >= 3 && p[p.size - 2].length <= 3 && p.last().length == 2) p.takeLast(3).joinToString(".") else p.takeLast(2).joinToString(".")
        }
        return a != null && b != null && base(a) == base(b)
    }

    private fun inject() = web.evaluateJavascript(navJs, null)

    private inner class Bridge {
        @JavascriptInterface fun playing(on: Boolean) = main.post {
            if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }.let { }
        @JavascriptInterface fun filled(on: Boolean) = main.post {
            filledFilm = on
            Log.i(TAG, if (on) "film fills the screen" else "film left full screen")
            if (on) { refreshOsd(); say("OK  play / pause  ·  ◀ ▶  10 s  ·  ▼  sound  ·  Back  stop", 5000) } else showOsd(null)
        }.let { }
    }

    // ------------------------------------------------------------------ keys

    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (down && e.repeatCount == 0) { backHeld = false; main.postDelayed(backLong, LONG_PRESS_MS) }
                if (!down) { main.removeCallbacks(backLong); if (!backHeld) back() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (down && e.repeatCount == 0) { okHeld = false; main.postDelayed(okLong, LONG_PRESS_MS) }
                if (!down) { main.removeCallbacks(okLong); if (!okHeld) ok() }
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (!down) return true
                if (pointerOn) { movePointer(e.keyCode, e.repeatCount); return true }
                val now = SystemClock.uptimeMillis()
                if (e.repeatCount > 0 && now - lastMove < 110) return true   // held arrow: a steady glide, not a flood
                lastMove = now
                nav(dirOf(e.keyCode), e)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (down && e.repeatCount == 0) {
                    val cmd = when (e.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> "play"; KeyEvent.KEYCODE_MEDIA_PAUSE -> "pause"
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "ff"; KeyEvent.KEYCODE_MEDIA_REWIND -> "rew"; else -> "toggle"
                    }
                    web.evaluateJavascript("window.lumenNav ? lumenNav.media('$cmd') : ''") { showOsd(it?.trim('"')) }
                }
                return true
            }
        }
        return super.dispatchKeyEvent(e)
    }

    private fun dirOf(code: Int) = when (code) {
        KeyEvent.KEYCODE_DPAD_UP -> "up"; KeyEvent.KEYCODE_DPAD_DOWN -> "down"
        KeyEvent.KEYCODE_DPAD_LEFT -> "left"; else -> "right"
    }

    private fun nav(dir: String, e: KeyEvent?, retried: Boolean = false) {
        web.evaluateJavascript("window.lumenNav ? lumenNav.key('$dir') : 'missing'") { raw ->
            val r = raw?.trim('"') ?: "null"
            when {
                r == "missing" && !retried -> { inject(); main.postDelayed({ nav(dir, e, true) }, 60) }
                r == "pass" -> e?.let { passKey(it.keyCode) }
                r == "edit" -> showKeyboard()
                r.startsWith("osd:") -> showOsd(r)
                r.startsWith("tap:") -> r.removePrefix("tap:").split(',').let { p -> tap(p[0].toFloat() * web.width, p[1].toFloat() * web.height) }
                r.startsWith("error") -> Log.w(TAG, "nav $dir: $r")
            }
        }
    }

    private fun ok() {
        if (pointerOn) { tap(pointer.x0, pointer.y0); return }
        nav("enter", KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
    }

    private fun back() {
        when {
            fullCallback != null -> fullCallback?.onCustomViewHidden()
            pointerOn -> setPointer(false)
            else -> web.evaluateJavascript("window.lumenNav ? lumenNav.back() : 'none'") { r ->
                if (r?.trim('"') == "ok") return@evaluateJavascript   // the film stopped and left full screen
                if (web.canGoBack()) web.goBack() else finish()
            }
        }
    }

    // ------------------------------------------------------------------ the film's bar

    private val tick = object : Runnable {
        override fun run() { if (filledFilm && osd.alpha > 0f) { refreshOsd(); main.postDelayed(this, 1000) } }
    }

    private fun refreshOsd() = web.evaluateJavascript("window.lumenNav ? lumenNav.osd() : ''") { r -> r?.trim('"')?.takeIf { it.startsWith("osd:") }?.let { osd.set(it) } }

    /** `osd:time,duration,paused,muted` from the page: show the bar for a few seconds (longer while paused). */
    private fun showOsd(r: String?) {
        if (r == null || !r.startsWith("osd:")) { main.removeCallbacks(hideOsd); osd.animate().alpha(0f).setDuration(200).start(); return }
        osd.set(r)
        osd.animate().cancel(); osd.alpha = 1f
        main.removeCallbacks(hideOsd); main.postDelayed(hideOsd, if (osd.paused) 8000 else 3500)
        main.removeCallbacks(tick); main.postDelayed(tick, 1000)
    }

    private class OsdView(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        var paused = false; private var muted = false; private var t = 0f; private var dur = -1f
        private val shade = Paint()
        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
        private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Lumen.Accent.toArgb() }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18 * d }
        private val icon = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        fun set(r: String) {
            val p = r.removePrefix("osd:").split(',')
            t = p.getOrNull(0)?.toFloatOrNull() ?: 0f; dur = p.getOrNull(1)?.toFloatOrNull() ?: -1f
            paused = p.getOrNull(2) == "1"; muted = p.getOrNull(3) == "1"
            invalidate()
        }
        private fun fmt(s: Float): String { val x = s.toInt(); return if (x >= 3600) "%d:%02d:%02d".format(x / 3600, x / 60 % 60, x % 60) else "%d:%02d".format(x / 60, x % 60) }
        override fun onDraw(c: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            shade.shader = android.graphics.LinearGradient(0f, 0f, 0f, h, 0x00000000, 0xCC000000.toInt(), android.graphics.Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, shade)
            val left = 56 * d; val right = w - 56 * d; val y = h - 34 * d
            // play / pause glyph
            val gx = left; val gy = y - 34 * d; val gs = 18 * d
            if (paused) {
                val path = android.graphics.Path().apply { moveTo(gx, gy - gs / 2); lineTo(gx + gs * 0.9f, gy); lineTo(gx, gy + gs / 2); close() }
                c.drawPath(path, icon)
            } else { c.drawRect(gx, gy - gs / 2, gx + gs * 0.32f, gy + gs / 2, icon); c.drawRect(gx + gs * 0.6f, gy - gs / 2, gx + gs * 0.92f, gy + gs / 2, icon) }
            val label = (if (dur > 0) fmt(t) + "  /  " + fmt(dur) else fmt(t)) + (if (muted) "     sound off  ·  ▼ to turn on" else "")
            c.drawText(label, gx + 34 * d, gy + 7 * d, text)
            c.drawRoundRect(left, y - 3 * d, right, y + 3 * d, 3 * d, 3 * d, track)
            if (dur > 0) c.drawRoundRect(left, y - 3 * d, left + (right - left) * (t / dur).coerceIn(0f, 1f), y + 3 * d, 3 * d, 3 * d, fillP)
        }
    }

    private fun passKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        web.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        web.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    private fun showKeyboard() {
        web.requestFocus()
        main.postDelayed({ (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(web, 0) }, 80)
    }

    /** A real touch on the page: what a finger on a touch screen would do. */
    private fun tap(x: Float, y: Float) {
        val t = SystemClock.uptimeMillis()
        val target: View = fullView ?: web
        for ((action, at) in listOf(MotionEvent.ACTION_DOWN to t, MotionEvent.ACTION_UP to t + 40)) {
            val ev = MotionEvent.obtain(t, at, action, x, y, 0)
            ev.source = InputDevice.SOURCE_TOUCHSCREEN
            target.dispatchTouchEvent(ev)
            ev.recycle()
        }
    }

    // ------------------------------------------------------------------ pointer

    private fun setPointer(on: Boolean, quiet: Boolean = false) {
        pointerOn = on
        pointer.visibility = if (on) View.VISIBLE else View.GONE
        if (on) {
            if (pointer.x0 == 0f) { pointer.x0 = root.width / 2f; pointer.y0 = root.height / 2f }
            pointer.invalidate(); hover()
            say("Pointer  ·  arrows move  ·  OK clicks  ·  hold OK or Back to stop", 4000)
        } else if (!quiet) say("Highlight  ·  arrows move between links", 2500)
    }

    private fun movePointer(code: Int, repeat: Int) {
        val step = dp(14) * (1f + (repeat.coerceAtMost(12) / 3f))
        var dx = 0f; var dy = 0f
        when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step; KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step; else -> dx = step
        }
        val w = root.width.toFloat(); val h = root.height.toFloat()
        val nx = (pointer.x0 + dx).coerceIn(0f, w - 1); val ny = (pointer.y0 + dy).coerceIn(0f, h - 1)
        // Pushing past the top or bottom edge scrolls whatever is under the pointer.
        if (dy != 0f && (ny <= dp(8) || ny >= h - dp(8))) {
            web.evaluateJavascript("window.lumenNav && lumenNav.scrollAt(${nx / w}, ${ny / h}, ${if (dy > 0) 0.35 else -0.35})", null)
        }
        pointer.x0 = nx; pointer.y0 = ny
        pointer.invalidate()
        hover()
    }

    /** Hover where the pointer is, so menus that open on hover open. */
    private fun hover() {
        val t = SystemClock.uptimeMillis()
        val ev = MotionEvent.obtain(t, t, MotionEvent.ACTION_HOVER_MOVE, pointer.x0, pointer.y0, 0)
        ev.source = InputDevice.SOURCE_MOUSE
        web.dispatchGenericMotionEvent(ev)
        ev.recycle()
    }

    private class PointerView(ctx: Context) : View(ctx) {
        var x0 = 0f; var y0 = 0f
        private val d = ctx.resources.displayMetrics.density
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.WHITE }
        private val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7 * d; color = 0x99000000.toInt() }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Lumen.Accent.toArgb() }
        override fun onDraw(c: Canvas) {
            c.drawCircle(x0, y0, 15 * d, shade)
            c.drawCircle(x0, y0, 15 * d, ring)
            c.drawCircle(x0, y0, 4 * d, dot)
        }
    }

    // ------------------------------------------------------------------ the rest

    private fun say(text: String, ms: Long) {
        hint.text = text
        hint.animate().cancel(); hint.alpha = 1f
        main.removeCallbacks(hideHint); main.postDelayed(hideHint, ms)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onResume() { super.onResume(); web.onResume() }

    override fun onStop() {
        // Leaving the site (Home, another app): the film stops rather than playing on unseen.
        web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(v){v.pause()})", null)
        web.onPause()
        super.onStop()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        runCatching { (web.parent as? ViewGroup)?.removeView(web); web.destroy() }
        super.onDestroy()
    }
}

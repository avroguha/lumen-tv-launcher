package org.idktech.lumen.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * The apps opened since the TV last started, for the Running panel.
 *
 * Android gives a launcher no way to see which other apps are still alive (their processes
 * and tasks are private since Android 5). What Lumen can see is every app that comes to the
 * front, through its Home-button accessibility service, so the panel lists those, most recent
 * first. A TV restart clears the list (keyed on the boot count).
 */
object RunningApps {
    private const val PREFS = "running"
    private const val TAG = "Lumen.Running"
    /** Never listed or closed: Lumen itself, Google TV home, the TV's own system screens. */
    private val NEVER = setOf(
        "org.idktech.lumen", "com.google.android.apps.tv.launcherx", "com.android.tv.settings", "com.android.systemui",
        "com.google.android.tvrecommendations", "com.google.android.katniss", "com.tcl.tv", "com.android.vending",
        "com.google.android.apps.tv.dreamx", "com.google.android.tungsten.setupwraith",
    )
    /** Bumped on every change, so an open panel redraws. */
    val version = mutableIntStateOf(0)

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun boot(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1)

    private fun fresh(ctx: Context) {
        val s = p(ctx)
        if (s.getInt("boot", -2) != boot(ctx)) s.edit().clear().putInt("boot", boot(ctx)).apply()
    }

    /** An app came to the front (from HomeRedirectService). */
    fun seen(ctx: Context, pkg: String) {
        if (pkg in NEVER || pkg.startsWith("org.idktech.lumen")) return
        fresh(ctx)
        p(ctx).edit().putLong("app:$pkg", System.currentTimeMillis()).apply()
        version.intValue++
    }

    fun forget(ctx: Context, pkg: String) {
        p(ctx).edit().remove("app:$pkg").apply()
        version.intValue++
    }

    /** Opened apps that Lumen can show (launchable ones), most recent first, with when they were last in front. */
    fun list(ctx: Context, apps: List<TvApp>): List<Pair<TvApp, Long>> {
        fresh(ctx)
        val byPkg = apps.associateBy { it.packageName }
        return p(ctx).all.mapNotNull { (k, v) ->
            if (!k.startsWith("app:") || v !is Long) return@mapNotNull null
            val app = byPkg[k.removePrefix("app:")] ?: return@mapNotNull null
            app to v
        }.sortedByDescending { it.second }
    }
}

/**
 * Closes apps the only way Android allows a non-system app to: the TV's own App info page and
 * its Force stop button, pressed by Lumen's accessibility service. Force stop ends the app's
 * process and its saved screens, so a stuck app starts from scratch next time.
 *
 * One app at a time: open App info for it, check the page is that app's, press Force stop
 * (greyed out = it was not running), press OK, next. Then back to Lumen with the panel open.
 */
object AppCloser {
    private const val TAG = "Lumen.Running"
    private const val SETTINGS = "com.android.tv.settings"
    private const val STEP_MS = 250L
    private const val GIVE_UP_MS = 7_000L

    @Volatile var service: AccessibilityService? = null
    /** "Closing YouTube…", then the outcome; shown on the panel. */
    val state = mutableStateOf<String?>(null)
    val busy = mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Pair<String, String>>()
    private var results = mutableListOf<String>()
    private var ctx: Context? = null

    fun available() = service != null

    fun close(context: Context, apps: List<TvApp>) {
        if (busy.value || apps.isEmpty()) return
        if (service == null) { state.value = "Turn on Lumen's Home-button service (Settings > Accessibility) to close apps"; return }
        ctx = context.applicationContext
        queue.clear(); results = mutableListOf()
        apps.forEach { queue.add(it.packageName to it.label) }
        busy.value = true
        next()
    }

    private fun next() {
        val c = ctx ?: return finish()
        val (pkg, label) = queue.removeFirstOrNull() ?: return finish()
        state.value = "Closing $label…"
        Log.i(TAG, "closing $pkg")
        runCatching {
            c.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    // A fresh Settings screen each time: otherwise the previous app's page stays up
                    // and the request for the next app is swallowed.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS),
            )
        }.onFailure { results += "$label: no App info page"; next(); return }
        val start = SystemClock.uptimeMillis()
        var pressedStop = false
        val tick = object : Runnable {
            override fun run() {
                val root = service?.rootInActiveWindow
                if (SystemClock.uptimeMillis() - start > GIVE_UP_MS) {
                    results += "$label: could not be closed"
                    Log.w(TAG, "$pkg: gave up (front window ${root?.packageName}, pressed Force stop: $pressedStop)")
                    next(); return
                }
                if (root == null || root.packageName != SETTINGS) { main.postDelayed(this, STEP_MS); return }
                if (!pressedStop) {
                    // The page must be this app's: its package name is shown under the title.
                    val ours = root.findAccessibilityNodeInfosByViewId("android:id/summary").any { it.text?.toString() == pkg }
                    val stop = root.findAccessibilityNodeInfosByText("Force stop").firstOrNull { it.viewIdResourceName == "android:id/title" }
                    if (!ours || stop == null) { main.postDelayed(this, STEP_MS); return }
                    val row = clickable(stop)
                    if (row == null || !row.isEnabled || !stop.isEnabled) {
                        results += "$label was not running"; Log.i(TAG, "$pkg: Force stop greyed out (not running)")
                        RunningApps.forget(ctx!!, pkg); next(); return
                    }
                    pressedStop = row.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    main.postDelayed(this, STEP_MS); return
                }
                // The confirmation: "Force stop" as the guidance title, OK and Cancel beside it.
                val asking = root.findAccessibilityNodeInfosByViewId("$SETTINGS:id/guidance_title").any { it.text?.toString() == "Force stop" }
                val ok = root.findAccessibilityNodeInfosByViewId("$SETTINGS:id/guidedactions_item_title").firstOrNull { it.text?.toString() == "OK" }
                if (!asking || ok == null) { main.postDelayed(this, STEP_MS); return }
                if (clickable(ok)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) {
                    results += "$label closed"; Log.i(TAG, "$pkg: force-stopped")
                    RunningApps.forget(ctx!!, pkg)
                    main.postDelayed({ next() }, 500)
                } else main.postDelayed(this, STEP_MS)
            }
        }
        main.postDelayed(tick, 400)
    }

    private fun clickable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var x = n
        while (x != null && !x.isClickable) x = x.parent
        return x
    }

    private fun finish() {
        busy.value = false
        state.value = results.joinToString("  ·  ").ifEmpty { null }
        Log.i(TAG, "done: ${state.value}")
        ctx?.let { c ->
            c.startActivity(
                Intent(c, org.idktech.lumen.MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    .putExtra("lumen.open", "running"),
            )
        }
    }
}

package org.idktech.lumen

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import org.idktech.lumen.overlay.TrailerSession

/**
 * Makes the Home button land on Lumen without disabling Google TV.
 *
 * On this TCL build the stock launcher and the setup app's recovery home both declare Home
 * filters with system-only priority, so a sideloaded launcher can never be resolved for the
 * Home key, and disabling the stock launcher hands Home to the recovery screen instead.
 * This service watches window changes and, the moment the stock home comes to the front,
 * brings Lumen forward. Enabled from Settings → Accessibility (or over ADB; see README).
 */
class HomeRedirectService : AccessibilityService() {
    private var last = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Key filtering must be asked for at runtime as well; the XML flag alone left onKeyEvent silent.
        serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS }
        TrailerSession.attach(this)
    }
    override fun onDestroy() { TrailerSession.detach(this); super.onDestroy() }

    /** Back during a trailer goes straight back to Lumen (see TrailerSession). */
    override fun onKeyEvent(event: android.view.KeyEvent): Boolean {
        val taken = TrailerSession.onKey(event)
        if (event.action == android.view.KeyEvent.ACTION_DOWN && event.repeatCount == 0) Log.i(TAG, "key ${event.keyCode} (${android.view.KeyEvent.keyCodeToString(event.keyCode)}) taken=$taken")
        return taken
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: ""
        Log.i(TAG, "window $pkg $cls")
        // Lumen's own floating clock and trailer card are windows of this package too; only a
        // screen (an Activity, or the screensaver) counts as Lumen being in front.
        if (pkg.startsWith("org.idktech.lumen") && !cls.endsWith("Activity")) return
        TrailerSession.onWindow(pkg)
        if (pkg != STOCK) return
        if (!cls.contains("HomeActivity")) return
        val now = SystemClock.elapsedRealtime()
        if (now < passUntil) { passUntil = 0L; Log.i(TAG, "stock home opened on purpose; not redirecting"); return }
        if (now - last < 800) return
        last = now
        TrailerSession.end(bringLumen = false)
        Log.i(TAG, "stock home came forward ($cls); bringing Lumen up")
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        )
    }

    override fun onInterrupt() {}

    companion object {
        private const val TAG = "Lumen.Home"
        const val STOCK = "com.google.android.apps.tv.launcherx"

        @Volatile private var passUntil = 0L

        /** Lumen itself is about to open Google TV home: let its next appearance through (for 20 s). */
        fun letStockHomeThrough() { passUntil = SystemClock.elapsedRealtime() + 20_000 }
    }
}

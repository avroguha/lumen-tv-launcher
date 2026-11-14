package org.idktech.lumen.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Process-wide caches, so coming back to Lumen (Home button, Back from an app) shows what
 * was already there instead of loading it again. Apps and inputs are re-read only when a
 * package is installed, updated or removed, or after 30 minutes; the catalog rows live in
 * one DiscoverModel for the life of the process and refresh themselves every 6 hours.
 */
object AppCache {
    /** For work that should outlive any one screen (catalog refresh). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var apps: List<TvApp>? = null
    private var inputs: List<TvInput>? = null
    private var loadedAt = 0L
    @Volatile private var dirty = true
    private var watching = false

    /** An HDMI device came or went: the next read must not serve the cached list. */
    fun invalidateInputs() { dirty = true }

    suspend fun appsAndInputs(ctx: Context): Pair<List<TvApp>, List<TvInput>> {
        watch(ctx)
        val a = apps; val i = inputs
        if (!dirty && a != null && i != null && SystemClock.elapsedRealtime() - loadedAt < 30 * 60_000L) return a to i
        val fresh = withContext(Dispatchers.IO) { Catalog.loadApps(ctx) to Catalog.loadInputs(ctx) }
        apps = fresh.first; inputs = fresh.second; loadedAt = SystemClock.elapsedRealtime(); dirty = false
        return fresh
    }

    private fun watch(ctx: Context) {
        if (watching) return
        watching = true
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ctx.applicationContext.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { dirty = true }
        }, f)
    }

    /**
     * Keeps the Home-button service switched on. Android turns it off after some updates and
     * reboots; with WRITE_SECURE_SETTINGS (granted once over adb) Lumen turns it back on itself.
     */
    fun ensureHomeService(ctx: Context) {
        val me = "${ctx.packageName}/${ctx.packageName}.HomeRedirectService"
        val cr = ctx.contentResolver
        val now = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val short = "${ctx.packageName}/.HomeRedirectService"
        val entries = now.split(':').filter { it.isNotBlank() }
        // Exactly one entry for Lumen: an earlier version appended a copy on every start.
        val clean = (entries.filter { it != me && it != short } + me).joinToString(":")
        if (clean == now && Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1) return
        runCatching {
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, clean)
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            Log.i("Lumen.Home", "Home-button service switched back on")
        }.onFailure { Log.w("Lumen.Home", "cannot re-enable the Home-button service (WRITE_SECURE_SETTINGS not granted)", it) }
    }
}

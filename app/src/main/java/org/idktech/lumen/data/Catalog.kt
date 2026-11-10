package org.idktech.lumen.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.tv.TvContract
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.idktech.lumen.HomeRedirectService

private const val TAG = "Lumen"

/** An app the TV can launch from a Leanback launcher. */
data class TvApp(
    val packageName: String,
    val label: String,
    val banner: ImageBitmap?,   // 16:9 TV banner when the app ships one
    val icon: ImageBitmap?,     // square icon fallback
    val isSystem: Boolean,
)

/** A physical input (HDMI etc.) exposed by the TV input framework. */
data class TvInput(
    val inputId: String,
    val label: String,
    val state: Int,            // TvInputManager.INPUT_STATE_CONNECTED / _CONNECTED_STANDBY / _DISCONNECTED
    val port: String = label,  // the port's own name ("HDMI 3"), when `label` also names the device on it
) {
    val connected: Boolean get() = state == TvInputManager.INPUT_STATE_CONNECTED
}

/** The name a CEC device has for its first second or two, before it reports its real one. */
private val CEC_PLACEHOLDER = Regex("(playback|recorder|tuner|audio[ _]?system)[ _]?\\d*", RegexOption.IGNORE_CASE)

object Catalog {

    /** Everything that declares a LEANBACK_LAUNCHER activity, except ourselves. Sorted by label. */
    fun loadApps(ctx: Context): List<TvApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                val ai = ri.activityInfo
                val banner = runCatching { ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) }.getOrNull()
                val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
                TvApp(
                    packageName = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    banner = banner?.let { runCatching { it.toBitmap(320, 180).asImageBitmap() }.getOrNull() },
                    icon = icon?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() },
                    isSystem = ai.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .sortedWith(compareBy({ rank(it) }, { it.label.lowercase() }))
    }

    /** The apps the TV is actually used for come first, then other installed apps, then TCL/Google system apps. */
    private val PRIORITY = listOf(
        "com.netflix.ninja", "com.amazon.amazonvideo.livingroom", "in.startv.hotstar",
        "com.google.android.youtube.tv", "com.jio.media.stb.ondemand", "com.sonyliv", "com.graymatrix.did",
        "com.apple.atve.androidtv.appletv", "org.jellyfin.androidtv", "com.mxtech.videoplayer.television",
        "com.google.android.videos", "com.google.android.youtube.tvmusic", "com.android.vending",
    )

    private fun rank(app: TvApp): Int {
        val p = PRIORITY.indexOf(app.packageName)
        return when {
            p >= 0 -> p
            !app.isSystem -> 100
            else -> 200
        }
    }

    /** HDMI and other pass-through inputs, as the TV labels them ("HDMI 1", ...). */
    fun loadInputs(ctx: Context): List<TvInput> {
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return emptyList()
        return runCatching {
            // A device that speaks HDMI-CEC appears as its own input, a child of the port it is on.
            // One row per port: named after the device (remembered while the device is off), and
            // switching always goes to the port, which is the input this TV is known to honour.
            val all = tim.tvInputList.filter { it.isPassthroughInput }
            val children = all.filter { it.parentId != null }
            val names = ctx.getSharedPreferences("inputs", Context.MODE_PRIVATE)
            all.filter { it.parentId == null }
                .map { p ->
                    val port = (p.loadCustomLabel(ctx) ?: p.loadLabel(ctx))?.toString() ?: p.id
                    val live = children.firstOrNull { it.parentId == p.id }?.let { c -> (c.loadCustomLabel(ctx) ?: c.loadLabel(ctx))?.toString() }
                        ?.takeUnless { CEC_PLACEHOLDER.matches(it) }
                    if (!live.isNullOrBlank()) names.edit().putString(p.id, live).apply()
                    val device = (live ?: names.getString(p.id, null))?.takeIf { it.isNotBlank() && !it.equals(port, ignoreCase = true) && !CEC_PLACEHOLDER.matches(it) }
                    val state = runCatching { tim.getInputState(p.id) }.getOrDefault(TvInputManager.INPUT_STATE_DISCONNECTED)
                    TvInput(p.id, if (device != null) "$device · $port" else port, state, port)
                }
                .sortedBy { it.port }
        }.getOrElse {
            Log.w(TAG, "tv inputs unavailable", it); emptyList()
        }
    }

    fun launchApp(ctx: Context, app: TvApp) {
        val pm = ctx.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(ctx, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show(); return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure { Toast.makeText(ctx, "Cannot open ${app.label}", Toast.LENGTH_SHORT).show() }
    }

    /** Switch the TV to a physical input. The TV's own Live TV app owns the pass-through channel URI. */
    fun switchToInput(ctx: Context, input: TvInput) {
        val uri: Uri = TvContract.buildChannelUriForPassthroughInput(input.inputId)
        // Explicit MIME type: resolved by data alone, this TCL routes content:// URIs to its browser.
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, TvContract.Channels.CONTENT_ITEM_TYPE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
            .onFailure {
                Log.w(TAG, "input switch failed for ${input.inputId}", it)
                Toast.makeText(ctx, "Cannot switch to ${input.label}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Games: apps flagged as games by their manifest (category or the legacy flag). */
    fun games(apps: List<TvApp>, ctx: Context): List<TvApp> {
        val pm = ctx.packageManager
        return apps.filter { app ->
            runCatching {
                val ai = pm.getApplicationInfo(app.packageName, 0)
                ai.category == ApplicationInfo.CATEGORY_GAME || (ai.flags and @Suppress("DEPRECATION") ApplicationInfo.FLAG_IS_GAME) != 0
            }.getOrDefault(false)
        }
    }

    /**
     * Opens a title inside its app. `deeplink` is JustWatch's Android TV intent:// URI; if the
     * TV cannot resolve it, the app itself is opened instead.
     */
    fun openOffer(ctx: Context, deeplink: String?, packageName: String, appName: String) {
        val pm = ctx.packageManager
        if (deeplink != null) {
            val intent = runCatching { Intent.parseUri(deeplink, Intent.URI_INTENT_SCHEME) }.getOrNull()
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(pm) != null && runCatching { ctx.startActivity(intent) }.isSuccess) return
                Log.w(TAG, "deeplink unresolved: $deeplink")
            }
        }
        val launch = pm.getLeanbackLaunchIntentForPackage(packageName) ?: pm.getLaunchIntentForPackage(packageName)
        if (launch == null) {
            Toast.makeText(ctx, "$appName is not installed", Toast.LENGTH_SHORT).show(); return
        }
        runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /** YouTube's own pages inside the YouTube TV app (its free-with-ads movie store, searches). */
    fun openYouTube(ctx: Context, url: String) {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube.tv").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(pm) != null && runCatching { ctx.startActivity(intent) }.isSuccess) return
        val launch = pm.getLeanbackLaunchIntentForPackage("com.google.android.youtube.tv")
        if (launch == null) { Toast.makeText(ctx, "YouTube is not installed", Toast.LENGTH_SHORT).show(); return }
        runCatching { ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openSystemSettings(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Opens the stock Google TV home. Its activity answers HOME, not LEANBACK_LAUNCHER, so it is
     * started by component; the Home-button service is told to let this one through.
     */
    fun openStockHome(ctx: Context) {
        HomeRedirectService.letStockHomeThrough()
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(HomeRedirectService.STOCK, "${HomeRedirectService.STOCK}.home.HomeActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(intent) }.isFailure) {
            val pm: PackageManager = ctx.packageManager
            val stock = listOf(HomeRedirectService.STOCK, "com.google.android.tvlauncher")
                .firstNotNullOfOrNull { pm.getLeanbackLaunchIntentForPackage(it) ?: pm.getLaunchIntentForPackage(it) }
            if (stock == null) { Toast.makeText(ctx, "Google TV home not found", Toast.LENGTH_SHORT).show(); return }
            runCatching { ctx.startActivity(stock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Starts the screensaver now, through SystemUI's Somnambulator (the only exported way in). */
    fun startScreensaver(ctx: Context): Boolean {
        val i = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }

    /** The "draw over other apps" page for Lumen, needed once by the floating clock. */
    fun openOverlayPermission(ctx: Context): Boolean {
        val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return true
        return runCatching { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    /** Google Assistant (or whatever answers ASSIST on this TV), as the remote's own button would. */
    fun openAssistant(ctx: Context): Boolean =
        runCatching { ctx.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { android.util.Log.w("Lumen.Catalog", "assistant did not open", it) }
            .onSuccess { android.util.Log.i("Lumen.Catalog", "assistant opened (ASSIST)") }
            .isSuccess

    fun hasAssistant(ctx: Context): Boolean = Intent(Intent.ACTION_ASSIST).resolveActivity(ctx.packageManager) != null

    fun isInstalled(ctx: Context, pkg: String): Boolean = runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess

    fun launchPackage(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg) ?: return false
        return runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }
}

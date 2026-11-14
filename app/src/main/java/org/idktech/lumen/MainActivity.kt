package org.idktech.lumen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import org.idktech.lumen.data.Trailers
import org.idktech.lumen.overlay.ClockOverlayService
import org.idktech.lumen.ui.HomeScreen
import org.idktech.lumen.ui.LumenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // adb shell am start -n org.idktech.lumen/.MainActivity --es lumen.tmdb_key <key>
        intent?.getStringExtra("lumen.tmdb_key")?.let { Trailers.setTmdbKey(this, it) }
        intent?.getStringExtra("lumen.open")?.let { openRequest.value = it }
        intent?.getStringExtra("lumen.site")?.let { openSiteFromIntent(it) }
        setContent {
            LumenTheme {
                HomeScreen()
            }
        }
    }

    // adb shell am start -n org.idktech.lumen/.MainActivity --es lumen.open voice   (or jarvis)
    private var inFront = false
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent)
        intent.getStringExtra("lumen.open")?.let { openRequest.value = it }
        intent.getStringExtra("lumen.site")?.let { openSiteFromIntent(it) }
        if (inFront && intent.hasCategory(android.content.Intent.CATEGORY_HOME)) openRequest.value = "top"
    }

    // adb shell am start -n org.idktech.lumen/.MainActivity --es lumen.site fawesome   (a Free TV site's id, or an https address)
    private fun openSiteFromIntent(v: String) {
        val known = org.idktech.lumen.web.FreeSiteStore.all(this).firstOrNull { it.id == v }
        val site = known ?: if (v.startsWith("https://")) org.idktech.lumen.web.FreeSite("adhoc", android.net.Uri.parse(v).host ?: v, v, "", 0) else null
        site?.let { org.idktech.lumen.web.openSite(this, it) }
    }

    companion object {
        /** A screen to open, asked for from outside (a test hook); HomeScreen consumes it. */
        val openRequest = androidx.compose.runtime.mutableStateOf<String?>(null)
    }

    // The floating clock is for OTHER apps: the bar has its own clock while Lumen is in front.
    override fun onResume() { super.onResume(); inFront = true; ClockOverlayService.sync(this, ClockOverlayService.ACTION_HIDE); org.idktech.lumen.data.AppCache.ensureHomeService(this); org.idktech.lumen.overlay.TrailerSession.end(bringLumen = false) }
    override fun onPause() { super.onPause(); inFront = false; ClockOverlayService.sync(this, ClockOverlayService.ACTION_SHOW) }
}

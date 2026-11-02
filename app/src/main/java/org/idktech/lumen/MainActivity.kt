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
        setContent {
            LumenTheme {
                HomeScreen()
            }
        }
    }

    // The floating clock is for OTHER apps: the bar has its own clock while Lumen is in front.
    override fun onResume() { super.onResume(); ClockOverlayService.sync(this, ClockOverlayService.ACTION_HIDE); org.idktech.lumen.data.AppCache.ensureHomeService(this); org.idktech.lumen.overlay.TrailerSession.end(bringLumen = false) }
    override fun onPause() { super.onPause(); ClockOverlayService.sync(this, ClockOverlayService.ACTION_SHOW) }
}

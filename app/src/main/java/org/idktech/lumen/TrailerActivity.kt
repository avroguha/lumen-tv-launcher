package org.idktech.lumen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import org.idktech.lumen.data.HistoryStore
import org.idktech.lumen.data.Title
import org.idktech.lumen.ui.LumenTheme
import org.idktech.lumen.ui.TitleDetail
import org.json.JSONObject

/**
 * The title screen with its trailer, in a see-through window of its own. The TV draws web
 * video on a hardware layer just BEHIND the window that shows it, through a hole in that
 * window; inside the launcher's own window the aurora and the page cover that hole, so the
 * trailer had sound and no picture. Here nothing is painted under the video. Back returns
 * to Lumen exactly where you were.
 */
class TrailerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This TV puts web video on a hardware plane UNDER every window: the only visible window
        // must be see-through there. The activity is opaque (Lumen behind it is not drawn) and its
        // surface carries alpha, so clear pixels reach the plane.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
        val t = runCatching { Title.fromJson(JSONObject(intent.getStringExtra(EXTRA) ?: "")) }.getOrNull() ?: run { finish(); return }
        setContent {
            LumenTheme {
                TitleDetail(
                    t,
                    onClose = { finish() },
                    onWatched = { HistoryStore.add(this, it) },
                    onSearch = { q -> pendingSearch.value = q; finish() },
                )
            }
        }
    }

    override fun onResume() { super.onResume(); org.idktech.lumen.overlay.ClockOverlayService.sync(this, org.idktech.lumen.overlay.ClockOverlayService.ACTION_HIDE) }

    companion object {
        private const val EXTRA = "lumen.title"
        /** A search asked for from this screen; Home picks it up when it is in front again. */
        val pendingSearch = mutableStateOf<String?>(null)

        fun start(ctx: Context, t: Title) {
            ctx.startActivity(Intent(ctx, TrailerActivity::class.java).putExtra(EXTRA, t.toJson().toString()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK.takeIf { ctx !is android.app.Activity } ?: 0))
        }
    }
}

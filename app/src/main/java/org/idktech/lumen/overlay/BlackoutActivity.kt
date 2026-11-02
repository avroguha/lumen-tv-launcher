package org.idktech.lumen.overlay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.idktech.lumen.media.NowPlaying
import org.idktech.lumen.ui.LumenTheme

/**
 * "Screen off" for a TV that gives apps no way to switch the panel off: a pure black window
 * with the brightness forced to its minimum, kept awake so the music playing underneath is
 * never interrupted by the screensaver or standby. Any key wakes it (and is swallowed).
 * A faint line shows the track for a few seconds, then nothing.
 */
class BlackoutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply { screenBrightness = 0f }
        setContent { LumenTheme { Blackout() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) finish()
        return true
    }

    companion object {
        fun start(ctx: Context) {
            ctx.startActivity(Intent(ctx, BlackoutActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        }
    }
}

@Composable
private fun Blackout() {
    var shown by remember { mutableStateOf(true) }
    val alpha by animateFloatAsState(if (shown) 0.35f else 0f, animationSpec = tween(1500), label = "hint")
    LaunchedEffect(Unit) { delay(6000); shown = false }
    val np = NowPlaying.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.align(Alignment.BottomStart).padding(40.dp).alpha(alpha)) {
            if (np != null) Text("${np.title}${if (np.artist.isNotBlank()) " · " + np.artist else ""}  ·  ${np.app}", fontSize = 14.sp, color = Color.White)
            Text("Screen off · press any key to wake", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

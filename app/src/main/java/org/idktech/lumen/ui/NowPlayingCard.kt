package org.idktech.lumen.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.idktech.lumen.data.Catalog
import org.idktech.lumen.media.NowPlaying
import org.idktech.lumen.media.Playing
import org.idktech.lumen.overlay.BlackoutActivity

/**
 * What is playing elsewhere on the TV (YouTube, YouTube Music, …) while Lumen is on screen:
 * art, title, artist, the app, and the controls — pause / play, next, open the app, stop,
 * and Screen off, which blacks the panel while the music carries on.
 */
@Composable
fun NowPlayingCard(p: Playing, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    GlassPanel(modifier.width(470.dp).height(96.dp), blur = false, corner = 24.dp) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).glassBackdrop(false).specularEdge(14.dp)) {
                if (p.art != null) Image(p.art, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                else Text("♪", fontSize = 26.sp, color = Lumen.Text, modifier = Modifier.align(Alignment.Center))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (p.playing) "Now playing · ${p.app}" else "Paused · ${p.app}", fontSize = 11.sp, color = if (p.playing) Lumen.Free else Lumen.TextFaint, maxLines = 1)
                Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.artist.isNotBlank()) Text(p.artist, fontSize = 12.sp, color = Lumen.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Pill(if (p.playing) "Pause" else "Play", onClick = { NowPlaying.toggle() }, height = 26.dp, fontSize = 11.sp, padding = 10.dp)
                    Pill("Next", onClick = { NowPlaying.next() }, height = 26.dp, fontSize = 11.sp, padding = 10.dp)
                    Pill("Open", onClick = { Catalog.launchPackage(ctx, p.packageName) }, height = 26.dp, fontSize = 11.sp, padding = 10.dp)
                    Pill("Stop", onClick = { NowPlaying.stopPlayback() }, height = 26.dp, fontSize = 11.sp, padding = 10.dp)
                    Pill("Screen off", onClick = { BlackoutActivity.start(ctx) }, height = 26.dp, fontSize = 11.sp, padding = 10.dp, accent = Lumen.Accent)
                }
            }
        }
    }
}

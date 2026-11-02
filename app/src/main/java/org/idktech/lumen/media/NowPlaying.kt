package org.idktech.lumen.media

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.app.NotificationManagerCompat

private const val TAG = "Lumen.NowPlaying"

/**
 * Reading other apps' media sessions needs notification-listener access, so this (empty)
 * listener exists only to be granted it: Settings → Apps → Notification access → Lumen.
 */
class NowPlayingListener : NotificationListenerService()

/** What is playing somewhere on the TV right now (YouTube, YouTube Music, Spotify, …). */
data class Playing(
    val packageName: String,
    val app: String,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val controller: MediaController,
)

/**
 * The TV's active media sessions, as Compose state. Picks the session that is playing (or,
 * failing that, paused) and follows its metadata, so the Home card and the screen-off mode
 * show the track as it changes.
 */
object NowPlaying {
    var current by mutableStateOf<Playing?>(null)
        private set
    var hasAccess by mutableStateOf(false)
        private set

    private var appCtx: Context? = null
    private var manager: MediaSessionManager? = null
    private var watched: MediaController? = null
    private var registered = false
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { refresh() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { refresh() }
        override fun onSessionDestroyed() { refresh() }
    }
    private val changed = MediaSessionManager.OnActiveSessionsChangedListener { refresh(it) }

    fun accessGranted(ctx: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    /** Call on the main thread; safe to call again (on resume) to re-check access. */
    fun start(ctx: Context) {
        val c = ctx.applicationContext; appCtx = c
        val m = manager ?: c.getSystemService(MediaSessionManager::class.java).also { manager = it }
        val cn = ComponentName(c, NowPlayingListener::class.java)
        hasAccess = accessGranted(c)
        if (!hasAccess) { current = null; return }
        if (!registered) runCatching { m.addOnActiveSessionsChangedListener(changed, cn); registered = true }.onFailure { Log.w(TAG, "no session access", it) }
        refresh()
    }

    fun stop() {
        runCatching { manager?.removeOnActiveSessionsChangedListener(changed) }; registered = false
        watched?.unregisterCallback(callback); watched = null
    }

    private fun refresh(list: List<MediaController>? = null) {
        val m = manager ?: return; val c = appCtx ?: return
        val sessions = list ?: runCatching { m.getActiveSessions(ComponentName(c, NowPlayingListener::class.java)) }.getOrElse { hasAccess = false; current = null; return }
        val pick = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull { it.playbackState?.state in setOf(PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING) }
        if (watched?.sessionToken != pick?.sessionToken) {
            watched?.unregisterCallback(callback)
            watched = pick
            pick?.registerCallback(callback)
        }
        current = pick?.let { toPlaying(c, it) }
    }

    private fun toPlaying(c: Context, mc: MediaController): Playing {
        val md = mc.metadata
        val pm = c.packageManager
        val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(mc.packageName, 0)).toString() }.getOrDefault(mc.packageName)
        val title = md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)?.ifBlank { null } ?: md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.ifBlank { null } ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE).orEmpty()
        val bmp = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return Playing(mc.packageName, app, title.ifBlank { app }, artist, bmp?.let { runCatching { it.asImageBitmap() }.getOrNull() }, mc.playbackState?.state == PlaybackState.STATE_PLAYING, mc)
    }

    fun toggle() { val c = current ?: return; if (c.playing) c.controller.transportControls.pause() else c.controller.transportControls.play() }
    fun next() { current?.controller?.transportControls?.skipToNext() }
    fun previous() { current?.controller?.transportControls?.skipToPrevious() }
    fun stopPlayback() { current?.controller?.transportControls?.stop(); current = null }
}

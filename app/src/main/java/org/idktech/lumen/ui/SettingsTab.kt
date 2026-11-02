package org.idktech.lumen.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import org.idktech.lumen.data.COUNTRIES
import org.idktech.lumen.data.Catalog
import org.idktech.lumen.data.ClockStore
import org.idktech.lumen.data.Prefs
import org.idktech.lumen.data.Subscription
import org.idktech.lumen.data.Trailers
import org.idktech.lumen.media.NowPlaying
import org.idktech.lumen.overlay.BlackoutActivity
import java.util.TimeZone

private val SECTIONS = listOf("Subscriptions", "Appearance", "Home", "Clock", "Weather", "Voice", "Catalog", "System", "About")

/**
 * Settings: a section list on the left, the options of the chosen section on the right. Every
 * option is a row of glass pills; the current value is lit. Changes apply at once.
 */
@Composable
fun SettingsTab(
    prefs: Prefs,
    onPrefs: (Prefs) -> Unit,
    subs: List<Subscription>,
    onSubs: (List<Subscription>) -> Unit,
    secondZone: String?,
    onSecondZone: (String?) -> Unit,
    onWeatherReset: () -> Unit,
    onClearCache: () -> Unit,
) {
    val ctx = LocalContext.current
    var section by remember { mutableIntStateOf(0) }
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canOverlay = Settings.canDrawOverlays(ctx); resumed++ }

    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 40.dp, top = 6.dp)) {
        Column(Modifier.width(210.dp).fillMaxHeight()) {
            Text("Settings", fontSize = 36.sp, fontWeight = FontWeight.Light, color = Lumen.Text)
            Spacer(Modifier.height(14.dp))
            SECTIONS.forEachIndexed { i, name ->
                Pill(
                    name, onClick = { section = i }, selected = i == section, height = 36.dp, fontSize = 14.sp, padding = 16.dp,
                    modifier = Modifier.padding(bottom = 5.dp).onFocusChanged { if (it.isFocused) section = i },
                )
            }
        }
        Spacer(Modifier.width(26.dp))
        if (SECTIONS[section] == "Subscriptions") {
            Box(Modifier.weight(1f).fillMaxHeight()) { SubscriptionsTab(subs, onChange = onSubs, embedded = true) }
            return@Row
        }
        LazyColumn(Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(top = 52.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (SECTIONS[section]) {
                "Appearance" -> {
                    item {
                        Choice("Theme", "The aurora's colours and the focus accent.", THEMES.map { it.id to it.name }, prefs.theme) { onPrefs(prefs.copy(theme = it)) }
                    }
                    item { Toggle("Motion", "The aurora drifts and the stars twinkle. Off freezes the backdrop (lighter on the TV).", prefs.motion) { onPrefs(prefs.copy(motion = it)) } }
                    item { Toggle("Backdrop blur", "The bar and the panels blur what scrolls beneath them. Off uses plain glass.", prefs.blur) { onPrefs(prefs.copy(blur = it)) } }
                }
                "Home" -> {
                    item { Choice("App tiles", "Size of the tiles in the Apps row.", listOf("s" to "Small", "m" to "Medium", "l" to "Large"), prefs.tileSize) { onPrefs(prefs.copy(tileSize = it)) } }
                    item { Toggle("Status line", "Connection, latency, RAM, storage and CPU under the greeting.", prefs.statusLine) { onPrefs(prefs.copy(statusLine = it)) } }
                    item { Toggle("Title backdrop", "The focused title's picture behind the page.", prefs.backdrop) { onPrefs(prefs.copy(backdrop = it)) } }
                    item { Toggle("YouTube free films row", "YouTube's own free-with-ads store and free-film searches.", prefs.youtubeRow) { onPrefs(prefs.copy(youtubeRow = it)) } }
                    item { Toggle("Film rows", "Trending films and the film genres.", prefs.filmRows) { onPrefs(prefs.copy(filmRows = it)) } }
                    item { Toggle("Series rows", "Trending series and the series genres.", prefs.seriesRows) { onPrefs(prefs.copy(seriesRows = it)) } }
                }
                "Clock" -> {
                    item { Choice("Time format", "Used by the bar, the floating clock and the screensaver.", listOf("auto" to "Follow the TV", "12" to "12-hour", "24" to "24-hour"), prefs.clock) { onPrefs(prefs.copy(clock = it)) } }
                    item {
                        val local = TimeZone.getDefault().id
                        val zones = listOf("" to "None") + ClockStore.ZONES.filter { it.first != local }
                        Choice("Second clock", "Another city, shown next to the time in the bar and on the floating clock.", zones, secondZone ?: "") { onSecondZone(it.ifEmpty { null }) }
                    }
                    item {
                        Choice(
                            "Floating clock", "A small clock drawn over other apps and over video. Hidden while Lumen itself is on screen.",
                            listOf("off" to "Off", "tr" to "Top right", "tl" to "Top left", "br" to "Bottom right", "bl" to "Bottom left"), prefs.floatingClock,
                        ) { onPrefs(prefs.copy(floatingClock = it)) }
                    }
                    item { Choice("Floating clock size", "", listOf("s" to "Small", "m" to "Medium"), prefs.floatingSize) { onPrefs(prefs.copy(floatingSize = it)) } }
                    if (prefs.floatingClock != "off" && !canOverlay) {
                        item {
                            Actions(
                                "Permission needed", "The floating clock needs “Display over other apps” for Lumen. Turn it on, then come back.",
                                listOf("Allow in Android settings" to { Catalog.openOverlayPermission(ctx) }),
                                accent = Color(0xFFFF6B8A),
                            )
                        }
                    }
                }
                "Weather" -> {
                    item { Choice("Temperature", "", listOf("c" to "°C", "f" to "°F"), prefs.tempUnit) { onPrefs(prefs.copy(tempUnit = it)) } }
                    item { Actions("Location", "The city comes from the TV's public address. Re-detect after moving the TV or changing provider.", listOf("Re-detect location" to onWeatherReset)) }
                }
                "Voice" -> {
                    item {
                        val options = buildList {
                            if (Catalog.hasAssistant(ctx)) add("google" to "Google Assistant")
                            add("lumen" to "Lumen commands")
                            if (Catalog.isInstalled(ctx, "com.anthropic.claude")) add("com.anthropic.claude" to "Claude")
                            if (Catalog.isInstalled(ctx, "com.google.android.apps.bard")) add("com.google.android.apps.bard" to "Gemini")
                        }
                        Choice(
                            "Mic button", "Google Assistant is this TV's own conversational assistant. Lumen commands work offline: open apps, switch inputs, find titles, weather, time, volume; anything else goes to Lumen's search. An installed Claude or Gemini app appears here too.",
                            options, prefs.voice,
                        ) { onPrefs(prefs.copy(voice = it)) }
                    }
                }
                "Catalog" -> {
                    item { Choice("Country", "Which catalogs, prices and free offers JustWatch reports. Changing it reloads every row.", COUNTRIES, prefs.country) { onPrefs(prefs.copy(country = it)) } }
                    item {
                        var key by remember { mutableStateOf("") }
                        var saved by remember { mutableStateOf(Trailers.tmdbKey(ctx)) }
                        GlassPanel(Modifier.fillMaxWidth(), blur = false, corner = 22.dp) {
                            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                                Text("Trailers \u00b7 TMDB", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lumen.Text)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    if (saved != null) "TMDB key set (ends \u2026${saved!!.takeLast(4)}): trailers come from The Movie Database first, YouTube search second."
                                    else "Without a key, trailers come from a YouTube search of the title. A free TMDB key (themoviedb.org \u203a Settings \u203a API) gives the official trailer directly. Type it here or push it over ADB: am start -n org.idktech.lumen/.MainActivity --es lumen.tmdb_key <key>",
                                    fontSize = 12.sp, color = Lumen.TextDim, lineHeight = 16.sp,
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    GlassTextField(key, { key = it }, placeholder = "TMDB API key or read-access token", onDone = { Trailers.setTmdbKey(ctx, key); saved = Trailers.tmdbKey(ctx); key = "" }, modifier = Modifier.width(520.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Pill("Save", onClick = { Trailers.setTmdbKey(ctx, key); saved = Trailers.tmdbKey(ctx); key = "" }, height = 36.dp, fontSize = 13.sp, padding = 14.dp, accent = Lumen.Free)
                                    if (saved != null) { Spacer(Modifier.width(8.dp)); Pill("Forget", onClick = { Trailers.setTmdbKey(ctx, null); saved = null }, height = 36.dp, fontSize = 13.sp, padding = 14.dp) }
                                }
                            }
                        }
                    }
                    item { Actions("Cache", "Rows are kept on the TV and refresh themselves every 6 hours in the background. Clear to refetch now.", listOf("Clear catalog cache" to onClearCache)) }
                }
                "System" -> {
                    item {
                        @Suppress("UNUSED_VARIABLE") val tick = resumed
                        val canWrite = Settings.System.canWrite(ctx)
                        val current = runCatching { Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT) / 60_000 }.getOrDefault(0)
                        Choice(
                            "Screensaver after", if (canWrite) "The TV's own idle timer (it never fires while a video is playing). Currently $current min."
                            else "The TV's own idle timer. Lumen needs the \u201cmodify system settings\u201d permission to change it: adb shell appops set org.idktech.lumen WRITE_SETTINGS allow. Currently $current min.",
                            listOf("1" to "1 min", "2" to "2 min", "5" to "5 min", "10" to "10 min", "15" to "15 min", "30" to "30 min"), current.toString(),
                        ) { m -> if (canWrite) runCatching { Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, m.toInt() * 60_000) }; resumed++ }
                    }
                    item {
                        Actions(
                            "Screensaver", "Lumen's screensaver shows the aurora, a large clock, a calendar, the weather and the next renewal. Choose it under Device Preferences › Screen saver.",
                            listOf("Start screensaver now" to { Catalog.startScreensaver(ctx) }, "Screensaver settings" to { Catalog.openSystemSettings(ctx) }),
                        )
                    }
                    item {
                        @Suppress("UNUSED_VARIABLE") val tick = resumed
                        val enabled = (Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(ctx.packageName)
                        Actions(
                            if (enabled) "Home button → Lumen: on" else "Home button → Lumen: OFF",
                            if (enabled) "The Home button lands on Lumen; Google TV stays installed." else "The Home button goes to Google TV right now. Turn on Accessibility › Lumen Home button (or ask for it over ADB).",
                            listOf("Accessibility settings" to { runCatching { ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
                            accent = if (enabled) null else Color(0xFFFFB46B),
                        )
                    }
                    item {
                        @Suppress("UNUSED_VARIABLE") val tick = resumed
                        val granted = NowPlaying.accessGranted(ctx)
                        Actions(
                            "Now playing", if (granted) "Notification access is on: whatever plays on the TV (YouTube, YouTube Music, …) shows on Home with its controls."
                            else "To show what is playing on the TV, Lumen needs notification access: Apps › Special app access › Notification access › Lumen now playing.",
                            listOf("Notification access" to { runCatching { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }),
                            accent = if (granted) null else Color(0xFFFFB46B),
                        )
                    }
                    item {
                        Actions(
                            "Screen off", "This TV gives apps no switch for the panel, so Lumen blacks the screen at minimum brightness and keeps it awake: music carries on, any key wakes it. Also at the bottom of the Input menu.",
                            listOf("Screen off now" to { BlackoutActivity.start(ctx) }),
                        )
                    }
                    item {
                        Actions(
                            "Android", "", listOf("Android settings" to { Catalog.openSystemSettings(ctx) }, "Google TV home" to { Catalog.openStockHome(ctx) }),
                        )
                    }
                }
                else -> {
                    item {
                        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
                        GlassPanel(Modifier.fillMaxWidth(), blur = false) {
                            Column(Modifier.padding(22.dp)) {
                                Text("Lumen $version", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Lumen.Text)
                                Spacer(Modifier.height(6.dp))
                                Text("${Build.MANUFACTURER} ${Build.MODEL}  ·  Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})  ·  ${Build.DISPLAY}", fontSize = 14.sp, color = Lumen.Text)
                                Spacer(Modifier.height(6.dp))
                                Text("Catalog by JustWatch · trailers from YouTube · weather by Open-Meteo · everything runs on this TV, nothing of yours leaves it.", fontSize = 12.sp, color = Lumen.TextDim)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Choice(label: String, desc: String, options: List<Pair<String, String>>, value: String, onValue: (String) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), blur = false, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lumen.Text)
            if (desc.isNotEmpty()) { Spacer(Modifier.height(2.dp)); Text(desc, fontSize = 12.sp, color = Lumen.TextDim, lineHeight = 16.sp) }
            Spacer(Modifier.height(10.dp))
            Wrap(options.map { (v, name) -> name to v }, value, onValue)
        }
    }
}

/** Pills in rows, wrapping by hand (no FlowRow in this tv-material). */
@Composable
private fun Wrap(options: List<Pair<String, String>>, value: String, onValue: (String) -> Unit) {
    options.chunked(6).forEach { line ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            line.forEach { (name, v) ->
                Pill(name, onClick = { onValue(v) }, selected = v == value, height = 34.dp, fontSize = 13.sp, padding = 14.dp, accent = if (v == value) Lumen.Accent else null)
            }
        }
    }
}

@Composable
private fun Toggle(label: String, desc: String, value: Boolean, onValue: (Boolean) -> Unit) =
    Choice(label, desc, listOf("on" to "On", "off" to "Off"), if (value) "on" else "off") { onValue(it == "on") }

@Composable
private fun Actions(label: String, desc: String, actions: List<Pair<String, () -> Unit>>, accent: Color? = null) {
    GlassPanel(Modifier.fillMaxWidth(), blur = false, corner = 22.dp) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = accent ?: Lumen.Text)
            if (desc.isNotEmpty()) { Spacer(Modifier.height(2.dp)); Text(desc, fontSize = 12.sp, color = Lumen.TextDim, lineHeight = 16.sp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { (name, run) -> Pill(name, onClick = { run() }, height = 36.dp, fontSize = 13.sp, padding = 16.dp) }
            }
        }
    }
}

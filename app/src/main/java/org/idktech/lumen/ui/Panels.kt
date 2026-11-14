package org.idktech.lumen.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeInputScale
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import org.idktech.lumen.data.Catalog
import org.idktech.lumen.data.ClockStore
import org.idktech.lumen.data.LocalPrefs
import org.idktech.lumen.data.Profile
import org.idktech.lumen.data.ProfileStore
import org.idktech.lumen.data.Status
import org.idktech.lumen.data.TvApp
import org.idktech.lumen.data.Weather
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Dark blurred veil over the whole content layer: what every overlay sits on. */
@Composable
fun Veil() {
    val haze = LocalHaze.current
    val m = if (haze != null) {
        Modifier.hazeEffect(
            state = haze,
            style = HazeStyle(backgroundColor = Color.Transparent, tints = listOf(HazeTint(Color.Black.copy(alpha = 0.55f))), blurRadius = 30.dp, noiseFactor = 0.02f),
        ) { blurEnabled = true; inputScale = HazeInputScale.Fixed(0.4f) }
    } else {
        Modifier.background(Color.Black.copy(alpha = 0.7f))
    }
    Box(Modifier.fillMaxSize().then(m))
}

/** A glass panel hung just below the chip that opened it (`anchor` = that chip's bounds in root pixels). */
@Composable
fun Anchored(anchor: Rect?, width: Dp, alignEnd: Boolean = false, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val wPx = with(density) { width.toPx() }
    val x = (anchor?.let { if (alignEnd) it.right - wPx else it.left } ?: 80f).coerceAtLeast(16f)
    val y = (anchor?.bottom ?: 150f) + with(density) { 10.dp.toPx() }
    Box(Modifier.fillMaxSize()) {
        Veil()
        Box(Modifier.offset { IntOffset(x.toInt(), y.toInt()) }.width(width)) {
            GlassPanel(Modifier.fillMaxWidth(), corner = 24.dp) { content() }
        }
    }
}

// ---------------------------------------------------------------- weather, 7 days

@Composable
fun WeatherPanel(w: Weather?, anchor: Rect?) {
    val prefs = LocalPrefs.current
    Anchored(anchor, width = 640.dp) {
        Column(Modifier.padding(22.dp)) {
            if (w == null) { Text("No weather yet", color = Lumen.Text, fontSize = 18.sp); return@Column }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherGlyph(w.code, w.isDay, size = 52.dp)
                Spacer(Modifier.width(16.dp))
                Text(prefs.temp(w.tempC), fontSize = 46.sp, fontWeight = FontWeight.Light, color = Lumen.Text)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("${w.label} · ${w.city}", fontSize = 16.sp, color = Lumen.Text)
                    Text("Feels like ${prefs.temp(w.feelsC)} · Humidity ${w.humidity}% · Wind ${w.windKmh} km/h", fontSize = 13.sp, color = Lumen.TextDim)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                w.days.take(7).forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp)) {
                        Text(if (i == 0) "Today" else d.date.dayOfWeek.getDisplayName(JTextStyle.SHORT, Locale.getDefault()), fontSize = 13.sp, color = if (i == 0) Lumen.Text else Lumen.TextDim)
                        Spacer(Modifier.height(6.dp))
                        WeatherGlyph(d.code, true, size = 26.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(prefs.temp(d.hiC), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Lumen.Text)
                        Text(prefs.temp(d.loC), fontSize = 13.sp, color = Lumen.TextDim)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Open-Meteo · Back to close", fontSize = 11.sp, color = Lumen.TextFaint)
        }
    }
}

// ---------------------------------------------------------------- clock, calendar, second clock

/** "10:14" with a small "AM" beside it in 12-hour mode; just "22:14" otherwise. */
@Composable
fun TimeText(parts: Pair<String, String?>, size: androidx.compose.ui.unit.TextUnit, color: Color = Lumen.Text, weight: FontWeight = FontWeight.Light, dim: Color = Lumen.TextDim) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(parts.first, fontSize = size, fontWeight = weight, color = color, maxLines = 1, softWrap = false, modifier = Modifier.alignByBaseline())
        if (parts.second != null) {
            Spacer(Modifier.width((size.value * 0.18f).dp))
            Text(parts.second!!, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Medium, color = dim, maxLines = 1, softWrap = false, letterSpacing = 1.sp, modifier = Modifier.alignByBaseline())
        }
    }
}

@Composable
fun ClockPanel(anchor: Rect?, secondZone: String?, onSecondZone: (String?) -> Unit) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    val first = remember { FocusRequester() }
    val zones = remember { ClockStore.ZONES.filter { it.first != java.util.TimeZone.getDefault().id } }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Anchored(anchor, width = 640.dp, alignEnd = true) {
        Row(Modifier.padding(22.dp)) {
            Column(Modifier.width(250.dp)) {
                val now = ZonedDateTime.now()
                TimeText(prefs.timeParts(ctx, now), 44.sp)
                Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")), fontSize = 14.sp, color = Lumen.TextDim)
                Spacer(Modifier.height(18.dp))
                Text("Second clock", fontSize = 12.sp, color = Lumen.TextFaint)
                Spacer(Modifier.height(6.dp))
                if (secondZone != null) {
                    val z = ZonedDateTime.now(ZoneId.of(secondZone))
                    TimeText(prefs.timeParts(ctx, z), 28.sp)
                    Text("${ClockStore.ZONES.firstOrNull { it.first == secondZone }?.second ?: secondZone} · ${z.format(DateTimeFormatter.ofPattern("EEE d MMM"))}", fontSize = 13.sp, color = Lumen.TextDim)
                } else {
                    Text("None", fontSize = 20.sp, color = Lumen.TextDim)
                }
                Spacer(Modifier.height(10.dp))
                val idx = zones.indexOfFirst { it.first == secondZone }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        if (secondZone == null) "Add a city" else "Next city ›",
                        onClick = { onSecondZone(zones[(idx + 1) % zones.size].first) },
                        height = 36.dp, fontSize = 13.sp, padding = 14.dp, modifier = Modifier.focusRequester(first),
                    )
                    if (secondZone != null) Pill("Remove", onClick = { onSecondZone(null) }, height = 36.dp, fontSize = 13.sp, padding = 14.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text("All cities: Settings › Clock", fontSize = 11.sp, color = Lumen.TextFaint)
            }
            Spacer(Modifier.width(22.dp))
            Calendar()
        }
    }
}

@Composable
private fun Calendar() {
    val today = LocalDate.now()
    val ym = YearMonth.from(today)
    val firstDow = ym.atDay(1).dayOfWeek.value % 7   // Sunday = 0
    val cells = (0 until 42).map { i -> val d = i - firstDow + 1; if (d in 1..ym.lengthOfMonth()) d else null }
    Column {
        Text(ym.month.getDisplayName(JTextStyle.FULL, Locale.getDefault()) + " " + ym.year, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Lumen.Text)
        Spacer(Modifier.height(8.dp))
        Row { listOf("S", "M", "T", "W", "T", "F", "S").forEach { Text(it, Modifier.width(44.dp), fontSize = 11.sp, color = Lumen.TextFaint) } }
        cells.chunked(7).forEach { week ->
            if (week.all { it == null }) return@forEach
            Row {
                week.forEach { d ->
                    Box(Modifier.size(44.dp, 34.dp), contentAlignment = Alignment.CenterStart) {
                        if (d != null) {
                            val isToday = d == today.dayOfMonth
                            Box(
                                Modifier.size(28.dp).clip(RoundedCornerShape(999.dp)).then(if (isToday) Modifier.background(Lumen.Accent) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text("$d", fontSize = 13.sp, fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal, color = if (isToday) Lumen.Base else Lumen.Text)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- profiles

@Composable
fun ProfilePanel(anchor: Rect?, profiles: List<Profile>, current: Profile, onSelect: (Profile) -> Unit, onAdd: (String) -> Unit, onRemove: (Profile) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Anchored(anchor, width = 420.dp) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Profiles", fontSize = 13.sp, color = Lumen.TextFaint, modifier = Modifier.padding(start = 6.dp))
            profiles.forEachIndexed { i, p ->
                GlassTile(onClick = { onSelect(p) }, corner = 16.dp, focusedScale = 1.02f, active = p.id == current.id,
                    modifier = Modifier.fillMaxWidth().height(48.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(p.name, fontSize = 16.sp, color = Lumen.Text)
                        Spacer(Modifier.weight(1f))
                        if (p.id == current.id) Text("current", fontSize = 12.sp, color = Lumen.TextFaint)
                    }
                }
            }
            if (adding) {
                GlassTextField(name, { name = it }, placeholder = "Profile name", onDone = { if (name.isNotBlank()) { onAdd(name); name = ""; adding = false } }, requestFocus = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Create", onClick = { if (name.isNotBlank()) { onAdd(name); name = ""; adding = false } }, height = 38.dp, fontSize = 14.sp, padding = 14.dp, accent = Lumen.Free)
                    Pill("Cancel", onClick = { adding = false }, height = 38.dp, fontSize = 14.sp, padding = 14.dp)
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("New profile", onClick = { adding = true }, height = 38.dp, fontSize = 14.sp, padding = 14.dp)
                    if (profiles.size > 1) Pill("Remove ${current.name}", onClick = { onRemove(current) }, height = 38.dp, fontSize = 14.sp, padding = 14.dp)
                }
            }
            Text("Launcher profiles: app order, subscriptions, language and clocks. The streaming apps keep their own profiles.", fontSize = 11.sp, color = Lumen.TextFaint, modifier = Modifier.padding(start = 6.dp, top = 4.dp))
        }
    }
}

@Composable
fun Avatar(p: Profile, size: Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(999.dp)).background(Color(ProfileStore.COLORS[p.color % ProfileStore.COLORS.size].toInt())), contentAlignment = Alignment.Center) {
        Text(p.initial, fontSize = (size.value * 0.5f).sp, fontWeight = FontWeight.SemiBold, color = Lumen.Base)
    }
}

/**
 * A glass text field for the TV keyboard. Until OK is pressed it is an ordinary focusable pill
 * (so the D-pad can pass over it without the keyboard popping up); OK turns it into a real
 * text field with the keyboard; Done or Back hands focus back to the pill.
 */
@Composable
fun GlassTextField(value: String, onValue: (String) -> Unit, placeholder: String, onDone: () -> Unit, modifier: Modifier = Modifier, requestFocus: Boolean = false) {
    var editing by remember { mutableStateOf(false) }
    val pillFr = remember { FocusRequester() }
    val fieldFr = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestFocus) { if (requestFocus) runCatching { pillFr.requestFocus() } }
    LaunchedEffect(editing) { if (editing) { runCatching { fieldFr.requestFocus() }; keyboard?.show() } }
    if (!editing) {
        GlassTile(onClick = { editing = true }, corner = 999.dp, focusedScale = 1.01f, modifier = modifier.focusRequester(pillFr).height(46.dp)) {
            Box(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
                Text(value.ifEmpty { placeholder }, fontSize = 16.sp, color = if (value.isEmpty()) Lumen.TextFaint else Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    } else {
        BasicTextField(
            value = value, onValueChange = onValue, singleLine = true,
            textStyle = TextStyle(color = Lumen.Text, fontSize = 17.sp, fontFamily = LumenFont),
            cursorBrush = SolidColor(Lumen.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); editing = false; onDone(); runCatching { pillFr.requestFocus() } }),
            modifier = modifier
                .focusRequester(fieldFr)
                .onPreviewKeyEvent { e ->
                    if (e.type == KeyEventType.KeyDown && (e.key == Key.Back || e.key == Key.Escape)) { keyboard?.hide(); editing = false; true } else false
                },
            decorationBox = { inner ->
                Box(
                    Modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(999.dp)).glassBackdrop(true).specularEdge(999.dp, true).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.isEmpty()) Text(placeholder, fontSize = 16.sp, color = Lumen.TextFaint)
                    inner()
                }
            },
        )
    }
}

// ---------------------------------------------------------------- status strip, youtube, app tile

/**
 * One line under the greeting: network (name, address, latency), the remote's microphone link,
 * memory, storage and a CPU sparkline. The remote-mic item is the one that matters: when that
 * Bluetooth link is down, voice on the whole TV silently hears nothing.
 */
@Composable
fun StatusStrip(s: Status?) {
    if (s == null) return
    val warn = Color(0xFFFF6B8A)
    val dot = "   ·   "
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(999.dp)).background(if (s.online) Lumen.Free else warn))
        Spacer(Modifier.width(8.dp))
        val name = listOfNotNull(s.network, s.ssid, s.ip).joinToString(" ")
        val net = if (s.online) "$name · ${s.latencyMs} ms" else "$name · no internet"
        Text(net, fontSize = 12.sp, color = Lumen.TextDim, maxLines = 1)
        Text(dot, fontSize = 12.sp, color = Lumen.TextDim)
        val r = s.remote
        val remoteText = when (r.state) {
            org.idktech.lumen.data.RemoteMic.State.CONNECTED -> "Remote mic connected"
            org.idktech.lumen.data.RemoteMic.State.DISCONNECTED -> "Remote mic not connected · pair it in Remotes & accessories"
            org.idktech.lumen.data.RemoteMic.State.NO_PERMISSION -> "Remote mic: allow Bluetooth in Settings › System"
            org.idktech.lumen.data.RemoteMic.State.UNAVAILABLE -> "No Bluetooth"
        }
        val remoteColor = when (r.state) {
            org.idktech.lumen.data.RemoteMic.State.CONNECTED -> Lumen.TextDim
            org.idktech.lumen.data.RemoteMic.State.DISCONNECTED -> warn
            else -> Lumen.TextFaint
        }
        Text(remoteText, fontSize = 12.sp, color = remoteColor, maxLines = 1)
        Text(dot, fontSize = 12.sp, color = Lumen.TextDim)
        val ram = "RAM ${"%.1f".format(s.ramUsedMb / 1024f)} / ${"%.1f".format(s.ramTotalMb / 1024f)} GB"
        Text("$ram$dot${"%.0f".format(s.storageFreeGb)} GB free$dot", fontSize = 12.sp, color = Lumen.TextDim, maxLines = 1)
        CpuSparkline()
        s.cpuPct?.let { Spacer(Modifier.width(6.dp)); Text(if (org.idktech.lumen.data.SystemStatus.cpuSource == "clock") "CPU clock $it%" else "CPU $it%", fontSize = 12.sp, color = Lumen.TextDim, maxLines = 1) }
    }
}

/** The last ~80 seconds of CPU, a tiny line in the accent colour. */
@Composable
private fun CpuSparkline() {
    val samples = org.idktech.lumen.data.SystemStatus.cpuHistory
    val color = Lumen.Accent
    val faint = Lumen.TextFaint
    Canvas(Modifier.width(72.dp).height(14.dp)) {
        drawLine(faint.copy(alpha = 0.5f), Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        if (samples.size < 2) return@Canvas
        val n = 40
        val step = size.width / (n - 1)
        val start = n - samples.size
        var prev: Offset? = null
        samples.forEachIndexed { i, v ->
            val p = Offset((start + i) * step, size.height - v / 100f * (size.height - 1f))
            prev?.let { drawLine(color, it, p, strokeWidth = 2f) }
            prev = p
        }
    }
}

private data class YtLink(val label: String, val sub: String, val url: String)

private val YT_LINKS = listOf(
    // YouTube keeps its own history and resume points; the app opens them by link, Lumen cannot read them.
    YtLink("Continue watching", "your YouTube history", "https://www.youtube.com/feed/history"),
    YtLink("Watch later", "your saved videos", "https://www.youtube.com/playlist?list=WL"),
    YtLink("Free movies", "free with ads", "https://www.youtube.com/feed/storefront"),
    // Studios' own channels that post full films (from github.com/SuperAB123/Free-Official-Youtube-Content).
    YtLink("Goldmines", "Bollywood, full films", "https://www.youtube.com/@GoldminesBollywood"),
    YtLink("Shemaroo Movies", "Hindi, full films", "https://www.youtube.com/@shemaroomovies"),
    YtLink("Rajshri", "classic Bollywood", "https://www.youtube.com/@rajshri"),
    YtLink("Pen Multiplex", "Bollywood, full films", "https://www.youtube.com/@PenMultiplex"),
    YtLink("Goldmines Telefilms", "South films in Hindi", "https://www.youtube.com/@GoldminesTelefilms"),
    YtLink("Hindi films", "search: free full movies", "https://www.youtube.com/results?search_query=hindi+full+movie+free"),
    YtLink("Hollywood", "free full movies", "https://www.youtube.com/results?search_query=hollywood+full+movie+free+english"),
    YtLink("South Indian", "free full movies", "https://www.youtube.com/results?search_query=south+indian+full+movie+free"),
    YtLink("Documentaries", "free on YouTube", "https://www.youtube.com/results?search_query=full+documentary"),
)

/** YouTube's own free film pages, opened inside the YouTube TV app (it has no purchase-free API). */
@Composable
fun YouTubeRow(firstFocus: FocusRequester? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier) {
        Text("YouTube", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFFF6B6B), modifier = Modifier.padding(start = 80.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 14.dp), modifier = Modifier.stayAtEnds()) {
            items(YT_LINKS.size) { i ->
                val l = YT_LINKS[i]
                GlassTile(onClick = { Catalog.openYouTube(ctx, l.url) }, corner = 18.dp, modifier = Modifier.width(190.dp).height(84.dp).then(if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.size(30.dp)) {
                            drawRoundRect(Color(0xFFFF0000), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
                            val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.4f, size.height * 0.3f); lineTo(size.width * 0.72f, size.height * 0.5f); lineTo(size.width * 0.4f, size.height * 0.7f); close() }
                            drawPath(p, Color.White)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(l.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Lumen.Text, maxLines = 1)
                            Text(l.sub, fontSize = 11.sp, color = Lumen.TextDim, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

/**
 * An app tile. Long-press OK starts move mode (the Apps row then handles LEFT / RIGHT, and
 * OK or Back finishes). Visuals in move mode: accent film and an arrow hint under the label.
 */
@Composable
fun AppTile(
    app: TvApp,
    modifier: Modifier = Modifier,
    moving: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onMoveDone: (() -> Unit)? = null,
    width: Dp = 200.dp,
) {
    val ctx = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassTile(
            onClick = { if (moving) onMoveDone?.invoke() else Catalog.launchApp(ctx, app) },
            onLongClick = onLongClick,
            active = moving,
            modifier = modifier
                .width(width).height(width * 0.53f)
                .onFocusChanged { focused = it.isFocused },
        ) {
            when {
                app.banner != null -> Image(app.banner, contentDescription = app.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                app.icon != null -> Image(app.icon, contentDescription = app.label, modifier = Modifier.size(64.dp).align(Alignment.Center))
                else -> Text(app.label, modifier = Modifier.align(Alignment.Center), color = Lumen.Text)
            }
            if (moving) {
                Box(Modifier.fillMaxSize().background(Lumen.Accent.copy(alpha = 0.25f)))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (moving) "‹  move  ›" else app.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (moving) Lumen.Accent else if (focused) Lumen.Text else Lumen.TextDim,
        )
    }
}

/** A microphone, drawn. */
@Composable
fun MicGlyph(size: Dp, color: Color = Lumen.Text) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width; val h = this.size.height
        drawRoundRect(color, topLeft = Offset(w * 0.36f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.46f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f))
        drawArc(color, 0f, 180f, false, topLeft = Offset(w * 0.22f, h * 0.22f), size = androidx.compose.ui.geometry.Size(w * 0.56f, h * 0.56f), style = Stroke(width = w * 0.07f, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.78f), Offset(w * 0.5f, h * 0.9f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.9f), Offset(w * 0.64f, h * 0.9f), strokeWidth = w * 0.07f, cap = StrokeCap.Round)
    }
}

@Suppress("unused")
private fun keep(b: ImageBitmap?) = b

package org.idktech.lumen.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.idktech.lumen.data.Action
import org.idktech.lumen.data.AppCache
import org.idktech.lumen.data.AppOrderStore
import org.idktech.lumen.data.Assistant
import org.idktech.lumen.data.Catalog
import org.idktech.lumen.data.ClockStore
import org.idktech.lumen.data.DISCOVER_ROWS
import org.idktech.lumen.data.HistoryStore
import org.idktech.lumen.data.Kind
import org.idktech.lumen.data.LANGUAGES
import org.idktech.lumen.data.LanguageStore
import org.idktech.lumen.data.LocalPrefs
import org.idktech.lumen.data.Prefs
import org.idktech.lumen.data.Profile
import org.idktech.lumen.data.ProfileStore
import org.idktech.lumen.data.SettingsStore
import org.idktech.lumen.data.Status
import org.idktech.lumen.data.Subscription
import org.idktech.lumen.data.SubscriptionStore
import org.idktech.lumen.data.SystemStatus
import org.idktech.lumen.data.Title
import org.idktech.lumen.data.Trailers
import org.idktech.lumen.data.TvApp
import org.idktech.lumen.data.TvInput
import org.idktech.lumen.data.Weather
import org.idktech.lumen.data.WeatherRepo
import org.idktech.lumen.media.NowPlaying
import org.idktech.lumen.overlay.BlackoutActivity
import org.idktech.lumen.overlay.ClockOverlayService
import org.idktech.lumen.overlay.TrailerSession
import org.idktech.lumen.TrailerActivity
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val TABS = listOf("Home", "Search", "Games", "Settings")
private val BAR_SPACE = 92.dp
private val MOVE_KEYS = setOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown, Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape)

private sealed class Overlay {
    data object Inputs : Overlay()
    data object WeatherInfo : Overlay()
    data object ClockInfo : Overlay()
    data object Profiles : Overlay()
    data class Detail(val title: Title) : Overlay()
    data class Jarvis(val forSearch: Boolean) : Overlay()
}

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val aurora = remember { AuroraState() }
    val haze = remember { HazeState() }
    var prefs by remember { mutableStateOf(SettingsStore.load(ctx)) }
    val discover = remember { DiscoverModel.shared(ctx, prefs.country).also { it.setCountry(prefs.country) } }
    val assistant = remember { Assistant(ctx) }
    DisposableEffect(Unit) { onDispose { assistant.destroy() } }

    var apps by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var games by remember { mutableStateOf<List<TvApp>>(emptyList()) }
    var inputs by remember { mutableStateOf<List<TvInput>>(emptyList()) }
    var subs by remember { mutableStateOf<List<Subscription>>(emptyList()) }
    var weather by remember { mutableStateOf<Weather?>(WeatherRepo.cached(ctx)) }
    var status by remember { mutableStateOf<Status?>(null) }
    var profiles by remember { mutableStateOf(ProfileStore.list(ctx)) }
    var profile by remember { mutableStateOf(ProfileStore.current(ctx)) }
    var order by remember { mutableStateOf<List<String>>(emptyList()) }
    var secondZone by remember { mutableStateOf<String?>(null) }
    var language by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<Title>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var tab by remember { mutableIntStateOf(0) }
    var overlay by remember { mutableStateOf<Overlay?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var hero by remember { mutableStateOf<Title?>(null) }
    val anchors = remember { mutableStateMapOf<String, Rect>() }

    // Settings apply the moment they change: palette, motion, blur, the floating clock, the catalog country.
    fun setPrefs(p: Prefs) {
        prefs = p; SettingsStore.save(ctx, p)
        ClockOverlayService.sync(ctx, ClockOverlayService.ACTION_HIDE)
        discover.setCountry(p.country)
    }
    LaunchedEffect(prefs.theme, prefs.motion, prefs.blur) {
        Lumen.theme = THEMES.firstOrNull { it.id == prefs.theme } ?: THEMES[0]
        Lumen.motion = prefs.motion
        Lumen.blur = prefs.blur
    }

    // Focus goes back to whatever opened an overlay when it closes (the chip, the poster).
    var returnTo by remember { mutableStateOf<FocusRequester?>(null) }
    fun open(o: Overlay, from: FocusRequester) {
        // Titles open in their own see-through screen, so the trailer's picture is not covered.
        if (o is Overlay.Detail) { TrailerActivity.start(ctx, o.title); return }
        returnTo = from; overlay = o
    }
    val pending = TrailerActivity.pendingSearch.value
    LaunchedEffect(pending) { if (pending != null) { searchQuery = pending; tab = 1; TrailerActivity.pendingSearch.value = null } }
    fun close() { overlay = null }
    LaunchedEffect(overlay) {
        if (overlay == null) returnTo?.let { fr -> withFrameNanos { }; withFrameNanos { }; runCatching { fr.requestFocus() } }
    }
    // The aurora is the costliest thing Lumen draws: every glass tile redraws it. It moves at
    // ~9 fps while you use the remote, and rests 20 s after the last key (nothing is redrawn
    // then, so an idle Lumen costs no CPU). The voice screen keeps its ribbons moving.
    val lastKey = remember { longArrayOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        val start = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val idle = android.os.SystemClock.elapsedRealtime() - lastKey[0] > 20_000
            val animate = (overlay == null && Lumen.motion && !idle) || overlay is Overlay.Jarvis
            if (animate) {
                withFrameNanos { }
                aurora.t = (android.os.SystemClock.elapsedRealtime() - start) / 1000f
                delay(if (overlay is Overlay.Jarvis) 40 else 110)
            } else delay(300)
        }
    }

    // Microphone permission, asked for the first time Lumen's own voice screen opens.
    var pendingJarvis by remember { mutableStateOf<Pair<Boolean, FocusRequester>?>(null) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val p = pendingJarvis; pendingJarvis = null
        if (granted && p != null) open(Overlay.Jarvis(p.first), p.second)
    }
    fun openJarvis(forSearch: Boolean, fr: FocusRequester) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) open(Overlay.Jarvis(forSearch), fr)
        else { pendingJarvis = forSearch to fr; micPermission.launch(Manifest.permission.RECORD_AUDIO) }
    }
    /** The mic chip: Google Assistant by default, Lumen's own commands, or an installed assistant app (Settings → Voice). */
    fun openVoice(fr: FocusRequester) {
        when (val v = prefs.voice) {
            "lumen" -> openJarvis(false, fr)
            "google" -> if (!Catalog.openAssistant(ctx)) openJarvis(false, fr)
            else -> if (!Catalog.launchPackage(ctx, v) && !Catalog.openAssistant(ctx)) openJarvis(false, fr)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { if (loaded) refresh++; NowPlaying.start(ctx) }
    DisposableEffect(Unit) { NowPlaying.start(ctx); onDispose { NowPlaying.stop() } }
    LaunchedEffect(refresh, profile.id) {
        // The saved order is read BEFORE the app list is published: otherwise the first tile of the
        // unsorted list takes the initial focus and the row then re-sorts underneath it.
        val (a, i) = AppCache.appsAndInputs(ctx)
        withContext(Dispatchers.IO) {
            order = AppOrderStore.load(ctx); subs = SubscriptionStore.load(ctx); secondZone = ClockStore.secondZone(ctx)
            language = LanguageStore.get(ctx); history = HistoryStore.load(ctx)
        }
        apps = a; inputs = i; games = Catalog.games(a, ctx)
        loaded = true
    }
    LaunchedEffect(Unit) { while (true) { weather = WeatherRepo.current(ctx); delay(30L * 60 * 1000) } }
    LaunchedEffect(Unit) { while (true) { status = SystemStatus.read(ctx); delay(10_000) } }
    LaunchedEffect(tab) { if (tab != 0) hero = null }

    BackHandler {
        when {
            moving != null -> moving = null
            overlay != null -> close()
            tab != 0 -> tab = 0
        }
    }

    val orderedApps = remember(apps, order) { AppOrderStore.apply(apps, order) }
    fun moveApp(pkg: String, dir: Int) {
        val list = orderedApps.map { it.packageName }.toMutableList()
        val i = list.indexOf(pkg); val j = i + dir
        if (i < 0 || j !in list.indices) return
        list[i] = list[j].also { list[j] = list[i] }
        AppOrderStore.save(ctx, list); order = list
    }
    fun watched(t: Title) { HistoryStore.add(ctx, t); history = HistoryStore.load(ctx) }

    // What the voice screen does with what it heard.
    fun onHeard(text: String, forSearch: Boolean) {
        if (forSearch) { searchQuery = text; tab = 1; close(); return }
        scope.launch {
            when (val a = assistant.decide(text, orderedApps, inputs, weather, subs, discover.packages)) {
                is Action.Say -> assistant.speak(a.text)
                is Action.OpenApp -> { assistant.speak("Opening ${a.app.label}"); close(); Catalog.launchApp(ctx, a.app) }
                is Action.SwitchInput -> { assistant.speak("Switching to ${a.input.label}"); close(); Catalog.switchToInput(ctx, a.input) }
                is Action.ShowTitle -> { assistant.speak("Here's ${a.title.title}"); close(); TrailerActivity.start(ctx, a.title) }
                is Action.SearchTv -> { assistant.speak("Searching for ${a.query}"); close(); searchQuery = a.query; tab = 1 }
                is Action.AskGoogle -> { assistant.speak("Searching for ${a.query}"); close(); searchQuery = a.query; tab = 1 }
                Action.Close -> close()
            }
        }
    }

    CompositionLocalProvider(LocalAurora provides aurora, LocalHaze provides haze, LocalPrefs provides prefs) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Lumen.Base)
                .onPreviewKeyEvent { e ->
                    lastKey[0] = android.os.SystemClock.elapsedRealtime()
                    val pkg = moving ?: return@onPreviewKeyEvent false
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent e.key in MOVE_KEYS
                    if (e.nativeKeyEvent.repeatCount > 0) return@onPreviewKeyEvent true
                    when (e.key) {
                        Key.DirectionLeft -> { moveApp(pkg, -1); true }
                        Key.DirectionRight -> { moveApp(pkg, +1); true }
                        Key.DirectionUp, Key.DirectionDown -> true
                        Key.DirectionCenter, Key.Enter, Key.Back, Key.Escape -> { moving = null; true }
                        else -> false
                    }
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                FluidWallpaper()
                Box(Modifier.fillMaxSize().hazeSource(state = haze)) {
                    if (prefs.backdrop && tab == 0) Backdrop(hero)
                    Box(Modifier.fillMaxSize().padding(top = BAR_SPACE)) {
                        AnimatedContent(targetState = tab, transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) }, label = "tab") { shown ->
                            when (shown) {
                                0 -> HomeTab(
                                    apps = orderedApps, discover = discover, subs = subs, status = status, language = language, history = history,
                                    hero = hero, onHero = { hero = it },
                                    moving = moving, onMoving = { moving = it },
                                    onLanguage = { language = it; LanguageStore.set(ctx, it) },
                                    onOpen = { t, fr -> open(Overlay.Detail(t), fr) },
                                )
                                1 -> SearchTab(searchQuery, { searchQuery = it }, orderedApps, discover.packages, onOpen = { t, fr -> open(Overlay.Detail(t), fr) }, onVoice = { fr -> openJarvis(true, fr) })
                                2 -> GamesTab(games)
                                else -> SettingsTab(
                                    prefs = prefs, onPrefs = ::setPrefs, subs = subs, onSubs = { subs = it },
                                    secondZone = secondZone, onSecondZone = { secondZone = it; ClockStore.setSecondZone(ctx, it); ClockOverlayService.sync(ctx, ClockOverlayService.ACTION_HIDE) },
                                    onWeatherReset = { WeatherRepo.resetLocation(ctx); scope.launch { weather = WeatherRepo.current(ctx, force = true) } },
                                    onClearCache = { ctx.filesDir.resolve("discover").deleteRecursively(); discover.clear() },
                                )
                            }
                        }
                    }
                }
                TopBar(
                    tab = tab, onTab = { tab = it }, weather = weather, profile = profile, secondZone = secondZone, anchors = anchors,
                    onWeather = { open(Overlay.WeatherInfo, it) }, onInputs = { open(Overlay.Inputs, it) }, onClock = { open(Overlay.ClockInfo, it) },
                    onProfile = { open(Overlay.Profiles, it) }, onVoice = ::openVoice,
                )
            }
            var shown by remember { mutableStateOf<Overlay?>(null) }
            if (overlay != null) shown = overlay
            AnimatedVisibility(visible = overlay != null, enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.97f, animationSpec = tween(220)), exit = fadeOut(tween(140))) {
                when (val o = shown) {
                    Overlay.Inputs -> InputMenu(inputs, anchors["input"], onPick = { close(); Catalog.switchToInput(ctx, it) }, onScreenOff = { close(); BlackoutActivity.start(ctx) })
                    Overlay.WeatherInfo -> WeatherPanel(weather, anchors["weather"])
                    Overlay.ClockInfo -> ClockPanel(anchors["clock"], secondZone, onSecondZone = { secondZone = it; ClockStore.setSecondZone(ctx, it); ClockOverlayService.sync(ctx, ClockOverlayService.ACTION_HIDE) })
                    Overlay.Profiles -> ProfilePanel(
                        anchors["profile"], profiles, profile,
                        onSelect = { ProfileStore.setCurrent(ctx, it.id); profile = it; close() },
                        onAdd = { name -> val p = ProfileStore.add(ctx, name); profiles = ProfileStore.list(ctx); ProfileStore.setCurrent(ctx, p.id); profile = p; close() },
                        onRemove = { p -> ProfileStore.remove(ctx, p.id); profiles = ProfileStore.list(ctx); profile = ProfileStore.current(ctx); close() },
                    )
                    is Overlay.Detail -> TitleDetail(o.title, onClose = { close() }, onWatched = ::watched, onSearch = { q -> close(); searchQuery = q; tab = 1 })
                    is Overlay.Jarvis -> JarvisScreen(
                        assistant, o.forSearch,
                        onListen = { assistant.listen { heard -> onHeard(heard, o.forSearch) } },
                        onGoogle = { close(); Catalog.openAssistant(ctx) },
                    )
                    null -> {}
                }
            }
        }
    }
}

// ---------------------------------------------------------------- top bar

/**
 * Left: brand, profile, weather, Input. Middle: the tabs, with the same breathing room on
 * both sides. Right: mic and clock. Everything is sized so the whole bar fits 960 dp with a
 * second clock showing; the two weighted spacers share whatever is left.
 */
@Composable
private fun TopBar(
    tab: Int, onTab: (Int) -> Unit, weather: Weather?, profile: Profile, secondZone: String?, anchors: MutableMap<String, Rect>,
    onWeather: (FocusRequester) -> Unit, onInputs: (FocusRequester) -> Unit, onClock: (FocusRequester) -> Unit,
    onProfile: (FocusRequester) -> Unit, onVoice: (FocusRequester) -> Unit,
) {
    val prefs = LocalPrefs.current
    val weatherFr = remember { FocusRequester() }; val inputFr = remember { FocusRequester() }
    val clockFr = remember { FocusRequester() }; val profileFr = remember { FocusRequester() }; val voiceFr = remember { FocusRequester() }
    fun Modifier.anchor(key: String) = onGloballyPositioned { anchors[key] = it.boundsInRoot() }
    Box(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp)) {
        GlassPanel(Modifier.fillMaxWidth().height(58.dp), corner = 29.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(7.dp).shadow(8.dp, RoundedCornerShape(999.dp), ambientColor = Lumen.Accent, spotColor = Lumen.Accent).clip(RoundedCornerShape(999.dp)).background(Lumen.Accent))
                Spacer(Modifier.width(8.dp))
                Text("LUMEN", fontSize = 13.sp, fontWeight = FontWeight.Light, letterSpacing = 2.sp, color = Lumen.Text, modifier = Modifier.padding(end = 8.dp))
                GlassTile(onClick = { onProfile(profileFr) }, corner = 999.dp, focusedScale = 1.06f, modifier = Modifier.focusRequester(profileFr).anchor("profile").size(36.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Avatar(profile, 26.dp) }
                }
                Spacer(Modifier.width(6.dp))
                GlassTile(onClick = { onWeather(weatherFr) }, corner = 999.dp, focusedScale = 1.04f, modifier = Modifier.focusRequester(weatherFr).anchor("weather").height(36.dp).width(IntrinsicSize.Max)) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (weather == null) Text("Weather", fontSize = 13.sp, color = Lumen.TextDim) else {
                            WeatherGlyph(weather.code, weather.isDay, size = 19.dp); Spacer(Modifier.width(7.dp))
                            Text(prefs.temp(weather.tempC), fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Lumen.Text, softWrap = false)
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                GlassTile(onClick = { onInputs(inputFr) }, corner = 999.dp, focusedScale = 1.04f, modifier = Modifier.focusRequester(inputFr).anchor("input").height(36.dp).width(IntrinsicSize.Max)) {
                    Row(Modifier.align(Alignment.Center).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Input", fontSize = 13.sp, color = Lumen.TextDim); Spacer(Modifier.width(5.dp)); Text("⌄", fontSize = 13.sp, color = Lumen.Text)
                    }
                }
                Spacer(Modifier.width(12.dp)); Spacer(Modifier.weight(1f))
                TABS.forEachIndexed { i, title ->
                    Pill(title, onClick = { onTab(i) }, selected = i == tab, height = 36.dp, fontSize = 13.sp, padding = 12.dp, modifier = Modifier.onFocusChanged { if (it.isFocused) onTab(i) })
                    if (i < TABS.lastIndex) Spacer(Modifier.width(6.dp))
                }
                Spacer(Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
                GlassTile(onClick = { onVoice(voiceFr) }, corner = 999.dp, focusedScale = 1.06f, modifier = Modifier.focusRequester(voiceFr).size(34.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { MicGlyph(20.dp) }
                }
                Spacer(Modifier.width(6.dp))
                GlassTile(onClick = { onClock(clockFr) }, corner = 18.dp, focusedScale = 1.03f, modifier = Modifier.focusRequester(clockFr).anchor("clock").height(44.dp).width(IntrinsicSize.Max)) {
                    Box(Modifier.align(Alignment.Center).padding(horizontal = 10.dp)) { Clock(secondZone) }
                }
                Spacer(Modifier.width(2.dp))
            }
        }
    }
}

@Composable
private fun Clock(secondZone: String?) {
    val ctx = LocalContext.current
    val prefs = LocalPrefs.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(60_000L - System.currentTimeMillis() % 60_000L) } }
    val local = remember(now, prefs.clock) { prefs.timeParts(ctx, ZonedDateTime.now()) }
    val date = remember(now) { SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(now) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.End) {
            TimeText(local, 18.sp)
            Text(date, fontSize = 10.sp, color = Lumen.TextDim, maxLines = 1, softWrap = false)
        }
        if (secondZone != null) {
            val z = remember(now, secondZone, prefs.clock) { prefs.timeParts(ctx, ZonedDateTime.now(ZoneId.of(secondZone))) }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(1.dp).height(26.dp).background(Color.White.copy(alpha = 0.18f)))
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.Start) {
                TimeText(z, 13.sp, color = Lumen.Text.copy(alpha = 0.85f), weight = FontWeight.Normal, dim = Lumen.TextFaint)
                Text(ClockStore.ZONES.firstOrNull { it.first == secondZone }?.second ?: secondZone, fontSize = 9.5.sp, color = Lumen.TextFaint, maxLines = 1, softWrap = false)
            }
        }
    }
}

// ---------------------------------------------------------------- home (apps + catalog merged)

@Composable
private fun HomeTab(
    apps: List<TvApp>, discover: DiscoverModel, subs: List<Subscription>, status: Status?, language: String?, history: List<Title>,
    hero: Title?, onHero: (Title?) -> Unit,
    moving: String?, onMoving: (String?) -> Unit, onLanguage: (String?) -> Unit,
    onOpen: (Title, FocusRequester) -> Unit,
) {
    val prefs = LocalPrefs.current
    val scope = rememberCoroutineScope()
    val firstTile = remember { FocusRequester() }
    val tileFocus = remember { mutableMapOf<String, FocusRequester>() }
    val rowFocus = remember { mutableMapOf<String, FocusRequester>() }
    var currentRow by remember { mutableStateOf<String?>(null) }
    val appsState = rememberLazyListState()
    val listState = rememberLazyListState()
    // After every swap the moved tile is scrolled into view and re-focused: reordering a lazy
    // row detaches and re-attaches the node, which would otherwise drop focus elsewhere.
    LaunchedEffect(apps, moving) {
        val pkg = moving ?: return@LaunchedEffect
        val i = apps.indexOfFirst { it.packageName == pkg }
        if (i < 0) return@LaunchedEffect
        runCatching { appsState.animateScrollToItem((i - 2).coerceAtLeast(0)) }
        withFrameNanos { }; withFrameNanos { }
        runCatching { tileFocus[pkg]?.requestFocus() }
    }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty()) repeat(6) { if (runCatching { firstTile.requestFocus() }.isSuccess) return@LaunchedEffect; delay(60) }
    }
    val rows = remember(prefs.filmRows, prefs.seriesRows) {
        DISCOVER_ROWS.filter { (it.kind != Kind.MOVIE || prefs.filmRows) && (it.kind != Kind.SHOW || prefs.seriesRows) }
    }
    LaunchedEffect(discover.generation) { rows.take(3).forEach { discover.ensure(it) } }
    fun filtered(list: List<Title>?): List<Title>? = if (list == null || language == null) list else list.filter { language in it.languages }

    // The order of the list's items, for the rail to jump by key.
    val keys = remember(history.isNotEmpty(), prefs.youtubeRow, rows) {
        buildList { add("head"); add("apps"); add("lang"); if (history.isNotEmpty()) add("continue"); if (prefs.youtubeRow) add("yt"); rows.forEach { add(it.key) } }
    }
    val rail = remember(keys) {
        buildList {
            add(RailEntry("apps", "Apps"))
            if ("continue" in keys) add(RailEntry("continue", "Continue"))
            if ("yt" in keys) add(RailEntry("yt", "YouTube"))
            rows.firstOrNull { it.freeOnly }?.let { add(RailEntry(it.key, it.label)) }
            val films = rows.filter { it.kind == Kind.MOVIE }; val series = rows.filter { it.kind == Kind.SHOW }
            if (films.isNotEmpty()) { add(RailEntry("g_films", "Films", group = true)); films.forEach { add(RailEntry(it.key, it.label.removeSuffix(" movies").replaceFirstChar { c -> c.uppercase() })) } }
            if (series.isNotEmpty()) { add(RailEntry("g_series", "Series", group = true)); series.forEach { add(RailEntry(it.key, it.label.removeSuffix(" series").replaceFirstChar { c -> c.uppercase() })) } }
        }
    }
    /**
     * Jump to a row and focus its first title. A row can have nothing to land on (still loading,
     * or emptied by a language chip): then the next row down that has titles takes focus, then
     * the ones above, and the Apps row last, so focus never stays stuck in the rail.
     */
    fun jump(key: String) {
        val i = keys.indexOf(key); if (i < 0) return
        rows.firstOrNull { it.key == key }?.let { discover.ensure(it) }
        scope.launch {
            val order = (listOf(key) + keys.drop(i + 1) + keys.take(i).reversed()).filter { it != "head" && it != "lang" }
            for (k in order) {
                val idx = keys.indexOf(k)
                runCatching { listState.animateScrollToItem(idx) }
                val fr = if (k == "apps") firstTile else rowFocus[k]
                repeat(6) {
                    withFrameNanos { }
                    if (fr != null && runCatching { fr.requestFocus() }.isSuccess) return@launch
                }
            }
        }
    }
    val tileWidth = when (prefs.tileSize) { "s" -> 160.dp; "l" -> 240.dp; else -> 200.dp }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(top = 4.dp, bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "head") {
                Column(Modifier.padding(horizontal = 56.dp)) {
                    val playing = NowPlaying.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(greeting(), fontSize = 34.sp, fontWeight = FontWeight.Light, color = Lumen.Text)
                            if (prefs.statusLine) { Spacer(Modifier.height(4.dp)); StatusStrip(status) }
                        }
                        Spacer(Modifier.weight(1f))
                        if (playing != null) NowPlayingCard(playing)
                        else if (monthlyTotal(subs) > 0) Text("₹${"%,d".format(monthlyTotal(subs))} / month  ·  ${renewalText(subs)}", fontSize = 13.sp, color = Lumen.TextDim, maxLines = 1)
                    }
                }
            }
            item(key = "apps") {
                Column(Modifier.onFocusChanged { if (it.hasFocus) { onHero(null); currentRow = "apps" } }) {
                    Row(Modifier.padding(start = 80.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Apps", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Lumen.TextDim)
                        Spacer(Modifier.width(12.dp))
                        Text(if (moving != null) "LEFT / RIGHT to move, OK to drop" else "hold OK on an app to move it", fontSize = 12.sp, color = Lumen.TextFaint)
                    }
                    LazyRow(
                        state = appsState,
                        horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 16.dp),
                        modifier = Modifier.stayAtEnds(),
                    ) {
                        items(apps, key = { it.packageName }) { app ->
                            val fr = tileFocus.getOrPut(app.packageName) { FocusRequester() }
                            AppTile(
                                app, modifier = Modifier.focusRequester(fr).then(if (app === apps.first()) Modifier.focusRequester(firstTile) else Modifier),
                                moving = moving == app.packageName, onLongClick = { onMoving(app.packageName) },
                                onMoveDone = { onMoving(null) }, width = tileWidth,
                            )
                        }
                    }
                }
            }
            item(key = "lang") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 6.dp, bottom = 10.dp, start = 80.dp, end = 80.dp), modifier = Modifier.stayAtEnds().onFocusChanged { if (it.hasFocus) onHero(null) }) {
                    items(LANGUAGES.size) { i ->
                        val (code, name) = LANGUAGES[i]
                        Pill(name, onClick = { onLanguage(code) }, selected = code == language, height = 32.dp, fontSize = 13.sp, padding = 12.dp, accent = if (code == language) Lumen.Accent else null)
                    }
                }
            }
            if (history.isNotEmpty()) {
                item(key = "continue") {
                    TitleRow("Continue", filtered(history), accent = Lumen.Accent, note = "opened from Lumen", hero = hero, firstFocus = rowFocus.getOrPut("continue") { FocusRequester() }, onFocus = { onHero(it); currentRow = "continue" }, onOpen = onOpen)
                }
            }
            if (prefs.youtubeRow) item(key = "yt") { YouTubeRow(firstFocus = rowFocus.getOrPut("yt") { FocusRequester() }, modifier = Modifier.onFocusChanged { if (it.hasFocus) { onHero(null); currentRow = "yt" } }) }
            items(rows, key = { it.key }) { spec ->
                LaunchedEffect(spec.key, discover.generation) { discover.ensure(spec) }
                val all = discover.rows[spec.key]
                val titles = filtered(all)
                val exhausted = discover.exhausted(spec.key)
                // A language filter can leave a page nearly empty: keep paging until the row has something to show.
                LaunchedEffect(all?.size, language) { if (all != null && language != null && (titles?.size ?: 0) < 10 && !exhausted) discover.more(spec) }
                if (titles != null && titles.isEmpty() && exhausted) return@items
                TitleRow(
                    spec.label, if (titles != null && titles.isEmpty()) null else titles,
                    accent = if (spec.freeOnly) Lumen.Free else Lumen.TextDim,
                    note = when { discover.loading[spec.key] == true -> "loading…"; else -> null },
                    hero = hero, firstFocus = rowFocus.getOrPut(spec.key) { FocusRequester() },
                    onFocus = { onHero(it); currentRow = spec.key }, onNearEnd = { discover.more(spec) }, onOpen = onOpen,
                )
            }
        }
        GenreRail(rail, current = currentRow, onPick = ::jump, modifier = Modifier.align(Alignment.CenterStart))
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

// ---------------------------------------------------------------- games

@Composable
private fun GamesTab(games: List<TvApp>) {
    Column(Modifier.fillMaxSize().padding(top = 6.dp)) {
        Text("Games", fontSize = 36.sp, fontWeight = FontWeight.Light, color = Lumen.Text, modifier = Modifier.padding(horizontal = 56.dp))
        Spacer(Modifier.height(10.dp))
        if (games.isEmpty()) {
            GlassPanel(Modifier.padding(horizontal = 56.dp).fillMaxWidth(), blur = false) {
                Column(Modifier.padding(28.dp)) {
                    Text("No games on this TV yet", fontSize = 18.sp, color = Lumen.Text)
                    Spacer(Modifier.height(6.dp))
                    Text("Anything installed from Play Store that calls itself a game shows up here.", fontSize = 15.sp, color = Lumen.TextDim)
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 16.dp)) {
                items(games, key = { it.packageName }) { AppTile(it) }
            }
        }
    }
}

// ---------------------------------------------------------------- overlays

@Composable
private fun InputMenu(inputs: List<TvInput>, anchor: Rect?, onPick: (TvInput) -> Unit, onScreenOff: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Anchored(anchor, width = 300.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Switch input", fontSize = 13.sp, color = Lumen.TextFaint, modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 2.dp))
            inputs.forEachIndexed { i, inp ->
                GlassTile(onClick = { onPick(inp) }, corner = 16.dp, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth().height(50.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier)) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dot = when (inp.state) { 0 -> Lumen.Free; 1 -> Lumen.Accent.copy(alpha = 0.7f); else -> Color.White.copy(alpha = 0.18f) }
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(dot))
                        Spacer(Modifier.width(14.dp))
                        Text(inp.label, fontSize = 16.sp, color = Lumen.Text)
                        Spacer(Modifier.weight(1f))
                        Text(when (inp.state) { 0 -> "active"; 1 -> "standby"; else -> "" }, fontSize = 12.sp, color = Lumen.TextFaint)
                    }
                }
            }
            Text("Screen", fontSize = 13.sp, color = Lumen.TextFaint, modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp))
            GlassTile(onClick = { onScreenOff() }, corner = 16.dp, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth().height(50.dp).then(if (inputs.isEmpty()) Modifier.focusRequester(first) else Modifier)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).clip(RoundedCornerShape(999.dp)).background(Color.Black).specularEdge(999.dp))
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Screen off", fontSize = 16.sp, color = Lumen.Text)
                        Text("sound keeps playing · any key wakes", fontSize = 11.sp, color = Lumen.TextFaint)
                    }
                }
            }
        }
    }
}

/**
 * OK on a title: its trailer plays inside Lumen, full screen, with the details on a glass
 * panel over the video. Back closes it and you are where you were. If YouTube refuses to
 * embed a video, "Play in YouTube" opens it in the app instead.
 */
@Composable
fun TitleDetail(t: Title, onClose: () -> Unit, onWatched: (Title) -> Unit, onSearch: (String) -> Unit) {
    val ctx = LocalContext.current
    val first = remember { FocusRequester() }
    var looked by remember(t.id) { mutableStateOf(false) }
    var trailer by remember(t.id) { mutableStateOf<String?>(null) }
    var state by remember(t.id) { mutableStateOf("") }
    var inApp by remember(t.id) { mutableStateOf(false) }
    LaunchedEffect(t.id) {
        runCatching { first.requestFocus() }
        trailer = Trailers.find(ctx, t)
        looked = true
        // The YouTube app shows the picture; the web view here only gets the sound. Hand over
        // to YouTube with Lumen's details on top; stay here only if the Home service is off.
        val id = trailer
        if (id != null) { if (TrailerSession.start(t, id)) onClose() else inApp = true }
    }
    val playing = state == "playing" || state == "buffering"
    Box(Modifier.fillMaxSize().background(if (playing) Color.Transparent else Color.Black)) {
        val id = trailer
        if (t.backdrop != null && !playing && looked && trailer == null) AsyncImage(model = t.backdrop, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.55f, modifier = Modifier.fillMaxSize())
        if (id != null && inApp) TrailerPlayer(id, Modifier.fillMaxSize(), onState = { state = it })
        // a scrim so the panel and its text read over any frame of the video
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.6f to Color.Transparent)))
        Box(Modifier.align(Alignment.BottomStart).padding(start = 40.dp, bottom = 34.dp).width(560.dp).smokeGlass(26.dp)) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                Text(t.title, fontSize = 30.sp, fontWeight = FontWeight.Light, color = Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(metaLine(t) + (if (t.languages.isNotEmpty()) "   ·   " + t.languages.mapNotNull { c -> LANGUAGES.firstOrNull { it.first == c }?.second }.joinToString(", ") else ""), fontSize = 13.sp, color = Lumen.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Text(t.overview, fontSize = 14.sp, color = Lumen.Text.copy(alpha = 0.9f), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Watch on", fontSize = 12.sp, color = Lumen.TextFaint)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            !looked -> "looking for a trailer…"
                            trailer == null -> "no trailer found"
                            !inApp -> "opening the trailer…"
                            state.startsWith("error") -> "YouTube won't play it here ($state)"
                            playing -> "trailer · YouTube"
                            else -> "starting the trailer…"
                        },
                        fontSize = 11.sp, color = Lumen.TextFaint,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.offers.forEachIndexed { i, o ->
                        Pill(
                            if (o.isFree) "${o.name} · Free" else o.name,
                            onClick = { onWatched(t); onClose(); Catalog.openOffer(ctx, o.deeplink, o.packageName, o.name) },
                            selected = true, height = 38.dp, fontSize = 14.sp, padding = 16.dp, accent = if (o.isFree) Lumen.Free else Lumen.Text,
                            modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                    trailer?.let { v -> if (inApp) Pill("Play in YouTube", onClick = { Catalog.openYouTube(ctx, "https://www.youtube.com/watch?v=$v") }, height = 38.dp, fontSize = 14.sp, padding = 16.dp) }
                    Pill("Search in Lumen", onClick = { onSearch(t.title) }, height = 38.dp, fontSize = 14.sp, padding = 16.dp)
                }
            }
        }
        Text("Back to close", fontSize = 11.sp, color = Lumen.TextFaint, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 44.dp, bottom = 36.dp))
    }
}

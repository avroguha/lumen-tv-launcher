package org.idktech.lumen.ui

import android.content.Context
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.idktech.lumen.data.JustWatch
import org.idktech.lumen.data.RowSpec
import org.idktech.lumen.data.Title

/**
 * Loads catalog rows page by page, three requests at a time, and keeps them for the life of
 * the screen. A row grows as it is browsed (`more`) until JustWatch has nothing further or the
 * row holds MAX_PER_ROW titles.
 */
class DiscoverModel(private val ctx: Context, private val scope: CoroutineScope, country: String) {
    val rows = mutableStateMapOf<String, List<Title>>()
    val loading = mutableStateMapOf<String, Boolean>()
    /** Bumped by `clear`, so rows re-request themselves. */
    var generation by mutableIntStateOf(0)
        private set
    var country: String = country
        private set
    private val cursors = HashMap<String, String?>()
    private val done = HashSet<String>()
    private val gate = Semaphore(3)
    private val specs = HashMap<String, RowSpec>()
    val packages: List<String> by lazy { JustWatch.installedPackages(ctx) }

    init {
        // Auto refresh: every 6 hours each loaded row is fetched again in the background and
        // swapped in when it arrives; nothing on screen goes blank meanwhile.
        scope.launch { while (true) { kotlinx.coroutines.delay(6L * 60 * 60 * 1000); refreshAll() } }
    }

    fun refreshAll() {
        for ((key, spec) in specs.toMap()) scope.launch {
            val page = gate.withPermit { JustWatch.row(ctx, spec, packages, country, force = true) }
            if (page.titles.isEmpty()) return@launch
            rows[key] = page.titles; cursors[key] = page.next
            if (page.next != null) done -= key
        }
    }

    fun ensure(spec: RowSpec) { if (!rows.containsKey(spec.key)) more(spec) }

    /** The next page of a row, unless one is already in flight or the row is finished. */
    fun more(spec: RowSpec) {
        val key = spec.key
        specs[key] = spec
        if (loading[key] == true || key in done) return
        loading[key] = true
        scope.launch {
            val page = gate.withPermit { JustWatch.row(ctx, spec, packages, country, after = cursors[key]) }
            val have = rows[key].orEmpty()
            val seen = have.mapTo(HashSet()) { it.id }
            val merged = have + page.titles.filter { seen.add(it.id) }
            rows[key] = merged
            cursors[key] = page.next
            if (page.next == null || page.titles.isEmpty() || merged.size >= MAX_PER_ROW) done += key
            loading[key] = false
        }
    }

    fun exhausted(key: String): Boolean = key in done

    fun setCountry(c: String) { if (c != country) { country = c; clear() } }

    fun clear() { rows.clear(); loading.clear(); cursors.clear(); done.clear(); generation++ }

    companion object {
        const val MAX_PER_ROW = 240
        private var shared: DiscoverModel? = null
        /** One model for the process: rows survive the activity being recreated. */
        fun shared(ctx: Context, country: String): DiscoverModel =
            shared ?: DiscoverModel(ctx.applicationContext, org.idktech.lumen.data.AppCache.scope, country).also { shared = it }
    }
}

/**
 * The focused title's backdrop, faded into the page at the top right. It lives in the content
 * layer under everything else, so the glass bar blurs it like any other content.
 */
@Composable
fun Backdrop(t: Title?) {
    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = t?.backdrop, animationSpec = tween(600), label = "backdrop") { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = url, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.62f,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth(0.68f)
                            .fillMaxHeight(0.66f)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // alpha masks: fade out towards the left and the bottom, so the aurora takes over
                                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.55f to Color.Black), blendMode = BlendMode.DstIn)
                                drawRect(Brush.verticalGradient(0.3f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
                            },
                    )
                }
            }
        }
    }
}

/** Rows keep focus at their ends: RIGHT past the last tile stays put instead of jumping to the bar. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun Modifier.stayAtEnds(): Modifier = focusProperties { exit = { dir -> if (dir == FocusDirection.Right) FocusRequester.Cancel else FocusRequester.Default } }

fun metaLine(t: Title): String = buildList {
    if (t.year > 0) add(t.year.toString())
    add(if (t.isShow) "Series" else "Film")
    t.imdb?.let { add("IMDb ${"%.1f".format(it)}") }
    if (t.genres.isNotEmpty()) add(t.genres.take(3).joinToString(" · "))
}.joinToString("   ·   ")

@Composable
fun OfferBadge(text: String, free: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).glassBackdrop(false).specularEdge(999.dp).padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (free) Lumen.Free else Lumen.Text, maxLines = 1)
    }
}

/**
 * A labelled row of posters. `titles == null` = still loading (placeholder tiles). When the
 * focused title (`hero`) is one of this row's, its name, year, rating and apps sit next to the
 * label, so the detail is always beside the poster rather than in a block of its own.
 * `onNearEnd` fires when focus is within eight tiles of the end: the row loads ahead.
 */
@Composable
fun TitleRow(
    label: String,
    titles: List<Title>?,
    accent: Color = Lumen.TextDim,
    note: String? = null,
    hero: Title? = null,
    firstFocus: FocusRequester? = null,
    onFocus: (Title) -> Unit = {},
    onNearEnd: () -> Unit = {},
    onOpen: (Title, FocusRequester) -> Unit,
) {
    val shown = hero?.takeIf { h -> titles?.any { it.id == h.id } == true }
    Column {
        Row(Modifier.padding(start = 80.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = accent, maxLines = 1)
            if (shown != null) {
                Spacer(Modifier.width(14.dp))
                Text(shown.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 460.dp))
                Spacer(Modifier.width(12.dp))
                Text(metaLine(shown), fontSize = 13.sp, color = Lumen.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                shown.offers.take(2).forEach { o ->
                    Spacer(Modifier.width(8.dp))
                    OfferBadge(if (o.isFree) "Free · ${o.name}" else o.name, o.isFree)
                }
            } else if (note != null) {
                Spacer(Modifier.width(12.dp)); Text(note, fontSize = 13.sp, color = Lumen.TextFaint)
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 14.dp), modifier = Modifier.stayAtEnds()) {
            if (titles == null) {
                itemsIndexed(List(8) { it }) { _, _ -> Box(Modifier.width(120.dp).height(180.dp).clip(RoundedCornerShape(18.dp)).glassBackdrop(false)) }
            } else {
                itemsIndexed(titles, key = { _, t -> t.id }) { i, t ->
                    PosterTile(
                        t,
                        onFocus = { onFocus(t); if (i >= titles.size - 8) onNearEnd() },
                        onOpen = onOpen,
                        modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun PosterTile(t: Title, onFocus: () -> Unit, onOpen: (Title, FocusRequester) -> Unit, modifier: Modifier = Modifier, width: Dp = 120.dp) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    Column(Modifier.width(width), horizontalAlignment = Alignment.CenterHorizontally) {
        GlassTile(
            onClick = { onOpen(t, fr) }, corner = 18.dp,
            modifier = modifier.focusRequester(fr).width(width).height(width * 1.5f).onFocusChanged { if (it.isFocused) { focused = true; onFocus() } else focused = false },
        ) {
            if (t.poster != null) {
                AsyncImage(model = t.poster, contentDescription = t.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text(t.title, Modifier.align(Alignment.Center).padding(10.dp), color = Lumen.Text, fontSize = 14.sp, maxLines = 4)
            }
            if (t.hasFree) {
                Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(999.dp)).background(Lumen.Free).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("FREE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Lumen.Base)
                }
            }
            if (t.isShow) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.35f)).glassBackdrop(false).specularEdge(999.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("SERIES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Lumen.Text)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(t.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (focused) Lumen.Text else Lumen.TextDim)
    }
}

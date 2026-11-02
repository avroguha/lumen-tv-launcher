package org.idktech.lumen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import org.idktech.lumen.data.JustWatch
import org.idktech.lumen.data.Title
import org.idktech.lumen.data.TvApp

/** Type with the TV keyboard or speak: apps on the TV and titles across the installed apps' catalogs. */
@Composable
fun SearchTab(
    query: String,
    onQuery: (String) -> Unit,
    apps: List<TvApp>,
    packages: List<String>,
    onOpen: (Title, FocusRequester) -> Unit,
    onVoice: (FocusRequester) -> Unit,
) {
    val ctx = LocalContext.current
    var results by remember { mutableStateOf<List<Title>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val micFr = remember { FocusRequester() }
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = null; return@LaunchedEffect }
        delay(450)
        searching = true
        results = JustWatch.search(ctx, query.trim(), packages)
        searching = false
    }
    val appHits = remember(query, apps) { if (query.isBlank()) emptyList() else apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 56.dp, end = 56.dp, bottom = 50.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Search", fontSize = 36.sp, fontWeight = FontWeight.Light, color = Lumen.Text)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassTextField(query, onQuery, placeholder = "Films, series, apps…  (OK opens the keyboard)", onDone = {}, modifier = Modifier.width(620.dp))
                    Spacer(Modifier.width(12.dp))
                    GlassTile(onClick = { onVoice(micFr) }, corner = 999.dp, focusedScale = 1.04f, modifier = Modifier.focusRequester(micFr).height(46.dp).width(130.dp)) {
                        Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically) {
                            MicGlyph(20.dp); Spacer(Modifier.width(8.dp)); Text("Speak", fontSize = 15.sp, color = Lumen.Text)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (appHits.isNotEmpty()) {
                    Text("Apps", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lumen.TextDim)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                        items(appHits, key = { it.packageName }) { AppTile(it) }
                    }
                }
                val label = when {
                    query.trim().length < 2 -> "Type or speak to search the apps on this TV."
                    searching -> "Searching…"
                    results.isNullOrEmpty() -> "Nothing on your apps for “${query.trim()}”."
                    else -> "Titles"
                }
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Lumen.TextDim, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
        items(results.orEmpty(), key = { it.id }) { t -> PosterTile(t, onFocus = {}, onOpen = onOpen) }
    }
}

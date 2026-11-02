package org.idktech.lumen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** One stop on the rail: a row key to jump to. `group` entries are headings (Films, Series), not stops. */
data class RailEntry(val key: String, val label: String, val group: Boolean = false)

/**
 * The genre rail. Folded it is one slim glass handle on the left edge, reading "GENRES";
 * LEFT from any row lands on it and it unfolds into a glass list of every section, landing
 * on the section that was being browsed. OK jumps to that row; the rail folds the moment
 * focus leaves it, so the content keeps the whole width.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun GenreRail(entries: List<RailEntry>, current: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    var focusedIndex by remember { mutableIntStateOf(-1) }   // -2 = the folded handle
    var open by remember { mutableStateOf(false) }
    var landing by remember { mutableIntStateOf(-1) }
    val requesters = remember(entries) { List(entries.size) { FocusRequester() } }

    // Unfold while anything on the rail holds focus. Folding waits three frames: swapping the
    // handle for the list drops focus for a moment, and the list item then takes it back.
    var landingPending by remember { mutableStateOf(false) }
    LaunchedEffect(focusedIndex, landingPending) {
        if (focusedIndex != -1) {
            if (!open) {
                landing = entries.indexOfFirst { it.key == current }.takeIf { it >= 0 } ?: entries.indexOfFirst { !it.group }
                landingPending = true
                open = true
            }
        } else if (!landingPending) { repeat(3) { withFrameNanos { } }; if (focusedIndex == -1) open = false }
    }
    // The list replaces the handle, so focus has to be placed on it; retry until the entry is laid out.
    LaunchedEffect(open) {
        if (open && landing >= 0) {
            repeat(20) { withFrameNanos { }; if (runCatching { requesters[landing].requestFocus() }.isSuccess && focusedIndex >= 0) { landingPending = false; return@LaunchedEffect } }
            landingPending = false
        }
    }

    Box(
        modifier
            .fillMaxHeight()
            .padding(start = 8.dp, top = 10.dp, bottom = 10.dp)
            // Only the D-pad directions are held; Enter (a programmatic requestFocus, which is how
            // the jump into a row happens) must pass, or the rail traps focus for good.
            .focusProperties { exit = { d -> if (d == FocusDirection.Left || d == FocusDirection.Right || d == FocusDirection.Up || d == FocusDirection.Down) FocusRequester.Cancel else FocusRequester.Default } }
            .onPreviewKeyEvent { e ->
                // Back closes the rail and returns to the row you came from (both halves of the key
                // are taken, or the screen's own Back would also fire).
                if (e.key == Key.Back || e.key == Key.Escape) {
                    if (e.type == KeyEventType.KeyUp) (current ?: entries.firstOrNull { !it.group }?.key)?.let(onPick)
                    return@onPreviewKeyEvent true
                }
                if (e.type != KeyEventType.KeyDown || e.key != Key.DirectionRight) return@onPreviewKeyEvent false
                val key = when {
                    focusedIndex >= 0 -> entries[focusedIndex].key
                    else -> current ?: entries.firstOrNull { !it.group }?.key
                }
                if (key != null) onPick(key)
                true
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        if (open) {
            GlassPanel(Modifier.width(200.dp).fillMaxHeight(), corner = 22.dp) {
                // A plain scrolling Column, not a lazy list: every entry exists, so focus can land on any of them.
                Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 10.dp, horizontal = 8.dp)) {
                    entries.forEachIndexed { i, e ->
                        if (e.group) {
                            Text(e.label.uppercase(), fontSize = 10.sp, letterSpacing = 2.sp, color = Lumen.TextFaint, modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 6.dp))
                        } else {
                            var focused by remember { mutableStateOf(false) }
                            GlassTile(
                                onClick = { onPick(e.key) }, corner = 999.dp, focusedScale = 1.03f,
                                modifier = Modifier.padding(vertical = 3.dp).focusRequester(requesters[i]).fillMaxWidth().height(30.dp)
                                    .onFocusChanged { f -> focused = f.isFocused; if (f.isFocused) focusedIndex = i else if (focusedIndex == i) focusedIndex = -1 },
                            ) {
                                Text(
                                    e.label, fontSize = 12.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (focused) Lumen.Text else Lumen.TextDim, maxLines = 1,
                                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // The handle: a slim vertical tab, the only thing to the left of the rows.
            GlassTile(
                onClick = { }, corner = 12.dp, focusedScale = 1f,
                modifier = Modifier.size(24.dp, 150.dp).onFocusChanged { if (it.isFocused) focusedIndex = -2 else if (focusedIndex == -2) focusedIndex = -1 },
            ) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("‹", fontSize = 14.sp, color = Lumen.TextDim)
                    // The label is laid out at its own width (requiredWidth ignores the slim handle), then turned.
                    Box(Modifier.size(14.dp, 80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "GENRES", fontSize = 10.sp, letterSpacing = 3.sp, color = Lumen.TextDim, maxLines = 1, softWrap = false,
                            modifier = Modifier.requiredWidth(80.dp).graphicsLayer { rotationZ = -90f },
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

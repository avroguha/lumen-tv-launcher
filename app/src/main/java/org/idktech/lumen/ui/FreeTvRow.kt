package org.idktech.lumen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.idktech.lumen.web.FreeSite
import org.idktech.lumen.web.FreeSiteStore
import org.idktech.lumen.web.openSite

/**
 * Free streaming websites, each opened full screen in Lumen's web player. The last tile adds a
 * site of your own; hold OK on one you added to remove it.
 */
@Composable
fun FreeTvRow(firstFocus: FocusRequester? = null, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var sites by remember { mutableStateOf(FreeSiteStore.all(ctx)) }
    var adding by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Row(Modifier.padding(start = 80.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Free TV", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Lumen.Free)
            Spacer(Modifier.width(12.dp))
            Text(note ?: "websites, full screen  ·  ads blocked", fontSize = 12.sp, color = Lumen.TextFaint)
        }
        if (adding) {
            Row(Modifier.padding(start = 80.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassTextField(
                    typed, { typed = it }, placeholder = "Website address, e.g. tubitv.com",
                    onDone = {
                        val s = FreeSiteStore.add(ctx, typed)
                        note = if (s != null) "${s.name} added" else "That is not a web address"
                        if (s != null) { sites = FreeSiteStore.all(ctx); typed = "" }
                        adding = false
                    },
                    modifier = Modifier.width(460.dp), requestFocus = true,
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 80.dp, vertical = 14.dp), modifier = Modifier.stayAtEnds()) {
            items(sites.size, key = { sites[it].id }) { i ->
                val s = sites[i]
                SiteTile(
                    s, onClick = { openSite(ctx, s) },
                    onLongClick = if (s.custom) ({ FreeSiteStore.remove(ctx, s); sites = FreeSiteStore.all(ctx); note = "${s.name} removed" }) else null,
                    modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
                )
            }
            item(key = "add") {
                GlassTile(onClick = { adding = !adding; note = null }, corner = 18.dp, modifier = Modifier.width(150.dp).height(84.dp)) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("+", fontSize = 26.sp, color = Lumen.TextDim)
                        Text("Add a website", fontSize = 12.sp, color = Lumen.TextDim)
                    }
                }
            }
        }
    }
}

@Composable
private fun SiteTile(s: FreeSite, onClick: () -> Unit, onLongClick: (() -> Unit)?, modifier: Modifier) {
    GlassTile(onClick = onClick, onLongClick = onLongClick, corner = 18.dp, modifier = modifier.width(210.dp).height(84.dp)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(Color(s.color), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Text(s.name.take(1), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF101418))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Lumen.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.sub, fontSize = 11.sp, color = Lumen.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

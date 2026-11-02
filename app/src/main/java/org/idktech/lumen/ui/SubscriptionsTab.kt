package org.idktech.lumen.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import org.idktech.lumen.data.Cycle
import org.idktech.lumen.data.Subscription
import org.idktech.lumen.data.SubscriptionStore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val MONTHS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

fun monthlyTotal(list: List<Subscription>) = list.sumOf { it.monthlyInr }

fun nextRenewal(list: List<Subscription>): Pair<Subscription, LocalDate>? =
    list.mapNotNull { s -> s.nextRenewal()?.let { s to it } }.minByOrNull { it.second }

fun renewalText(list: List<Subscription>): String {
    val (s, d) = nextRenewal(list) ?: return "Nothing renewing"
    val days = ChronoUnit.DAYS.between(LocalDate.now(), d)
    val when_ = when (days) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $days days" }
    return "${s.name} renews $when_ · ${d.format(DATE_FMT)} · ₹${s.priceInr}"
}

/**
 * Every OTT app on the TV, each with: paid or free, price, monthly or yearly, renewal day
 * (and month for yearly). Values are changed with LEFT / RIGHT on the focused pill, OK toggles
 * paid/free. Saved on every change; there is nothing to submit.
 */
@Composable
fun SubscriptionsTab(subs: List<Subscription>, onChange: (List<Subscription>) -> Unit, embedded: Boolean = false) {
    val ctx = LocalContext.current
    fun update(s: Subscription) {
        val next = subs.map { if (it.packageName == s.packageName) s else it }
        SubscriptionStore.save(ctx, next)
        onChange(next)
    }
    val pad = if (embedded) PaddingValues(top = 52.dp, bottom = 60.dp) else PaddingValues(start = 56.dp, end = 56.dp, bottom = 60.dp)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            if (!embedded) {
                Text("Subscriptions", fontSize = 38.sp, fontWeight = FontWeight.Light, color = Lumen.Text)
                Spacer(Modifier.height(12.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                // shared widths, so the cards fit inside Settings as well as full screen
                StatCard("Per month", "₹${"%,d".format(monthlyTotal(subs))}", Modifier.weight(1f))
                StatCard("Per year", "₹${"%,d".format(monthlyTotal(subs) * 12)}", Modifier.weight(1f))
                StatCard("Next renewal", renewalText(subs), Modifier.weight(1.4f), small = true)
            }
            Spacer(Modifier.height(10.dp))
            Text("OK on Paid / Free switches it. OK on a value starts editing: LEFT / RIGHT change it, OK finishes.", fontSize = 13.sp, color = Lumen.TextFaint)
        }
        items(subs, key = { it.packageName }) { s -> SubscriptionCard(s, onChange = ::update) }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, small: Boolean = false) {
    GlassPanel(modifier.height(96.dp), corner = 24.dp, blur = false) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.Center) {
            Text(label, fontSize = 13.sp, color = Lumen.TextDim)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = if (small) 18.sp else 30.sp, fontWeight = FontWeight.Light, color = Lumen.Text, maxLines = 2)
        }
    }
}

@Composable
private fun SubscriptionCard(s: Subscription, onChange: (Subscription) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth(), corner = 24.dp, blur = false) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(240.dp)) {
                Text(s.name, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = Lumen.Text)
                val sub = if (!s.paid) "Free" else s.nextRenewal()?.let { "Renews ${it.format(DATE_FMT)}" } ?: ""
                Text(sub, fontSize = 13.sp, color = if (s.paid) Lumen.TextDim else Lumen.Free)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Pill(if (s.paid) "Paid" else "Free", onClick = { onChange(s.copy(paid = !s.paid)) }, selected = true, accent = if (s.paid) Lumen.Text else Lumen.Free)
                if (s.paid) {
                    Stepper("₹${s.priceInr}", onStep = { d -> onChange(s.copy(priceInr = (s.priceInr + d * if (s.cycle == Cycle.MONTHLY) 10 else 100).coerceIn(0, 99_999))) })
                    Stepper(if (s.cycle == Cycle.MONTHLY) "Monthly" else "Yearly", onStep = { onChange(s.copy(cycle = if (s.cycle == Cycle.MONTHLY) Cycle.YEARLY else Cycle.MONTHLY)) })
                    Stepper("Day ${s.renewDay}", onStep = { d -> onChange(s.copy(renewDay = ((s.renewDay - 1 + d + 31) % 31) + 1)) })
                    if (s.cycle == Cycle.YEARLY) {
                        Stepper(MONTHS[s.renewMonth - 1], onStep = { d -> onChange(s.copy(renewMonth = ((s.renewMonth - 1 + d + 12) % 12) + 1)) })
                    }
                    Text("₹${s.monthlyInr} / mo", fontSize = 14.sp, color = Lumen.TextDim, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

/**
 * A value pill in the Android TV settings style: OK enters edit mode (accent colour, arrows),
 * LEFT / RIGHT change the value while editing, OK or Back leaves edit mode. Outside edit mode
 * LEFT / RIGHT move focus between pills as usual.
 */
@Composable
private fun Stepper(label: String, onStep: (Int) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    Pill(
        label = if (editing) "\u2039  $label  \u203a" else label,
        onClick = { editing = !editing },
        selected = true,
        accent = if (editing) Lumen.Accent else null,
        modifier = Modifier
            .onPreviewKeyEvent { e ->
                if (!editing || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(+1); true }
                    Key.Back, Key.Escape -> { editing = false; true }
                    else -> false
                }
            }
            .onFocusChanged { if (!it.isFocused) editing = false },
    )
}

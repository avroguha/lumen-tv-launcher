package org.idktech.lumen.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth

enum class Cycle { MONTHLY, YEARLY }

/**
 * One streaming service as the user pays for it. There is no API on any of these apps that
 * reveals whether a subscription is active, so this is entered once with the remote and then
 * only the renewal dates move.
 */
data class Subscription(
    val packageName: String,
    val name: String,
    val paid: Boolean,          // false = using it free (ad tier, free tier, self-hosted)
    val priceInr: Int,
    val cycle: Cycle,
    val renewDay: Int,          // day of month (monthly) or day of month in renewMonth (yearly)
    val renewMonth: Int,        // 1-12, used for yearly
) {
    val monthlyInr: Int get() = if (!paid) 0 else if (cycle == Cycle.MONTHLY) priceInr else Math.round(priceInr / 12f)

    /** Next renewal date on or after today. */
    fun nextRenewal(today: LocalDate = LocalDate.now()): LocalDate? {
        if (!paid) return null
        return if (cycle == Cycle.MONTHLY) {
            var ym = YearMonth.from(today)
            var d = ym.atDay(minOf(renewDay, ym.lengthOfMonth()))
            if (d.isBefore(today)) { ym = ym.plusMonths(1); d = ym.atDay(minOf(renewDay, ym.lengthOfMonth())) }
            d
        } else {
            var ym = YearMonth.of(today.year, renewMonth)
            var d = ym.atDay(minOf(renewDay, ym.lengthOfMonth()))
            if (d.isBefore(today)) { ym = ym.plusYears(1); d = ym.atDay(minOf(renewDay, ym.lengthOfMonth())) }
            d
        }
    }
}

/** The OTT apps worth tracking, with a sensible starting price for India when first seen. */
private val KNOWN = listOf(
    Triple("com.netflix.ninja", "Netflix", 199),
    Triple("com.amazon.amazonvideo.livingroom", "Prime Video", 125),
    Triple("in.startv.hotstar", "JioHotstar", 149),
    Triple("com.jio.media.stb.ondemand", "JioCinema", 0),
    Triple("com.sonyliv", "Sony LIV", 83),
    Triple("com.graymatrix.did", "Zee5", 99),
    Triple("com.apple.atve.androidtv.appletv", "Apple TV", 99),
    Triple("com.google.android.youtube.tv", "YouTube", 0),
    Triple("com.mxtech.videoplayer.television", "MX Player", 0),
    Triple("org.jellyfin.androidtv", "Jellyfin", 0),
    Triple("com.surfshark.vpnclient.android", "Surfshark", 299),
    Triple("com.spotify.tv.android", "Spotify", 119),
)

object SubscriptionStore {
    private const val PREFS = "subscriptions"

    /** Stored entries merged with the OTT apps currently installed (new apps appear as free). */
    fun load(ctx: Context): List<Subscription> {
        val stored = ProfileStore.prefs(ctx, PREFS).getString("list", null)
            ?.let { runCatching { parse(JSONArray(it)) }.getOrNull() } ?: emptyList()
        val byPkg = stored.associateBy { it.packageName }.toMutableMap()
        val pm = ctx.packageManager
        for ((pkg, name, price) in KNOWN) {
            if (byPkg.containsKey(pkg)) continue
            if (runCatching { pm.getPackageInfo(pkg, 0) }.isFailure) continue
            byPkg[pkg] = Subscription(pkg, name, paid = false, priceInr = price, cycle = Cycle.MONTHLY, renewDay = 1, renewMonth = 1)
        }
        return byPkg.values.sortedWith(compareBy({ !it.paid }, { it.name }))
    }

    fun save(ctx: Context, list: List<Subscription>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().apply {
                put("pkg", s.packageName); put("name", s.name); put("paid", s.paid); put("price", s.priceInr)
                put("cycle", s.cycle.name); put("day", s.renewDay); put("month", s.renewMonth)
            })
        }
        ProfileStore.prefs(ctx, PREFS).edit().putString("list", arr.toString()).apply()
    }

    private fun parse(arr: JSONArray): List<Subscription> = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        Subscription(
            o.getString("pkg"), o.getString("name"), o.optBoolean("paid"), o.optInt("price"),
            runCatching { Cycle.valueOf(o.optString("cycle")) }.getOrDefault(Cycle.MONTHLY),
            o.optInt("day", 1).coerceIn(1, 31), o.optInt("month", 1).coerceIn(1, 12),
        )
    }
}

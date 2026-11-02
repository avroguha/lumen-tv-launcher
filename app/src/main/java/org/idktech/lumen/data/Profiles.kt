package org.idktech.lumen.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Launcher profiles. These are the launcher's own: each keeps its app order, subscriptions,
 * second clock and tab preferences. They are not Google accounts (the streaming apps keep
 * their own profile systems and Android TV has no API to switch accounts from a launcher).
 */
data class Profile(val id: String, val name: String, val color: Int) {
    val initial: String get() = name.trim().take(1).uppercase().ifEmpty { "?" }
}

object ProfileStore {
    private const val PREFS = "profiles"
    val COLORS = listOf(0xFF7FB4FF, 0xFFB388FF, 0xFF5CE0B1, 0xFFFFA86B, 0xFFFF7AA2, 0xFFFFE26B)

    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(ctx: Context): List<Profile> {
        val raw = p(ctx).getString("list", null)
        val parsed = raw?.let { runCatching { parse(JSONArray(it)) }.getOrNull() }
        if (!parsed.isNullOrEmpty()) return parsed
        val first = Profile("main", "Home", 0)
        save(ctx, listOf(first)); return listOf(first)
    }

    fun current(ctx: Context): Profile {
        val all = list(ctx)
        val id = p(ctx).getString("current", null)
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun setCurrent(ctx: Context, id: String) { p(ctx).edit().putString("current", id).apply() }

    fun add(ctx: Context, name: String): Profile {
        val all = list(ctx)
        val prof = Profile(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Profile ${all.size + 1}" }, all.size % COLORS.size)
        save(ctx, all + prof); return prof
    }

    fun remove(ctx: Context, id: String) {
        val all = list(ctx)
        if (all.size <= 1) return
        save(ctx, all.filter { it.id != id })
        if (current(ctx).id == id) setCurrent(ctx, all.first { it.id != id }.id)
    }

    /** Per-profile preferences file for a feature. */
    fun prefs(ctx: Context, name: String): SharedPreferences =
        ctx.getSharedPreferences("$name.${current(ctx).id}", Context.MODE_PRIVATE)

    private fun save(ctx: Context, list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("color", it.color) }) }
        p(ctx).edit().putString("list", arr.toString()).apply()
    }

    private fun parse(arr: JSONArray) = (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i); Profile(o.getString("id"), o.getString("name"), o.optInt("color") % COLORS.size)
    }
}

/** The order of the Apps row, per profile. Packages not listed follow in their default order. */
object AppOrderStore {
    fun load(ctx: Context): List<String> =
        ProfileStore.prefs(ctx, "apporder").getString("order", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()

    fun save(ctx: Context, order: List<String>) {
        ProfileStore.prefs(ctx, "apporder").edit().putString("order", order.joinToString("\n")).apply()
    }

    fun apply(apps: List<TvApp>, order: List<String>): List<TvApp> {
        if (order.isEmpty()) return apps
        val idx = order.withIndex().associate { it.value to it.index }
        return apps.sortedWith(compareBy { idx[it.packageName] ?: (order.size + apps.indexOf(it)) })
    }
}

/** The second clock's time zone id, per profile (null = none). */
object ClockStore {
    val ZONES = listOf(
        "Asia/Kolkata" to "India", "Europe/London" to "London", "Europe/Berlin" to "Berlin", "Asia/Dubai" to "Dubai",
        "Asia/Singapore" to "Singapore", "Asia/Tokyo" to "Tokyo", "Australia/Sydney" to "Sydney",
        "America/New_York" to "New York", "America/Chicago" to "Chicago", "America/Los_Angeles" to "Los Angeles", "America/Toronto" to "Toronto",
        "America/Sao_Paulo" to "São Paulo", "Europe/Paris" to "Paris", "Europe/Moscow" to "Moscow", "Asia/Hong_Kong" to "Hong Kong",
        "Asia/Shanghai" to "Shanghai", "Asia/Seoul" to "Seoul", "Pacific/Auckland" to "Auckland",
    )
    fun secondZone(ctx: Context): String? = ProfileStore.prefs(ctx, "clock").getString("second", null)
    fun setSecondZone(ctx: Context, id: String?) { ProfileStore.prefs(ctx, "clock").edit().putString("second", id).apply() }
}

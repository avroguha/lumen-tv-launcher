package org.idktech.lumen.web

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

/**
 * A free, ad-supported streaming website, opened full screen in Lumen's web player.
 * `desktop`: ask for the desktop page (most free sites serve TVs a "get our app" page otherwise).
 */
data class FreeSite(
    val id: String,
    val name: String,
    val url: String,
    val sub: String,
    val color: Long,
    val desktop: Boolean = true,
    val custom: Boolean = false,
)

/**
 * Legal free services that play in India in the TV's web view (checked from this network on
 * 2026-11-14). Pluto TV, Xumo, Tubi, The Roku Channel, Sling Freestream and Local Now are
 * region-locked to other countries and are left out; DistroTV's web domains are parked (for sale)
 * since 2026. Each one here was played on the TV with the remote: Fawesome and Plex (films, no ads
 * after blocking), RunnTV (live Indian channels), Internet Archive (public-domain films).
 */
val FREE_SITES = listOf(
    FreeSite("fawesome", "Fawesome", "https://fawesome.tv/", "Free films and series", 0xFFF2A007),
    FreeSite("plex", "Plex", "https://watch.plex.tv/", "Free films and live TV", 0xFFE5A00D),
    FreeSite("runn", "RunnTV", "https://www.runn.tv/", "Indian live channels", 0xFFE53950),
    FreeSite("archive", "Internet Archive", "https://archive.org/details/feature_films", "Public-domain classics", 0xFFB8B8B8),
)

object FreeSiteStore {
    private const val PREFS = "websites"

    fun custom(ctx: Context): List<FreeSite> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("custom", null) ?: return emptyList()
        return runCatching {
            val a = JSONArray(raw)
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                FreeSite(o.getString("id"), o.getString("name"), o.getString("url"), o.optString("sub", "Added by you"), 0xFF8A93A6, o.optBoolean("desktop", true), custom = true)
            }
        }.getOrDefault(emptyList())
    }

    fun all(ctx: Context): List<FreeSite> = FREE_SITES + custom(ctx)

    /** Adds a site from what was typed ("tubi.tv", "https://…"); returns it, or null if it is not a web address. */
    fun add(ctx: Context, typed: String): FreeSite? {
        val t = typed.trim()
        if (t.isEmpty() || t.contains(' ')) return null
        val url = if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
        val host = Uri.parse(url).host?.removePrefix("www.") ?: return null
        if (!host.contains('.')) return null
        val name = host.substringBefore('.').replaceFirstChar { it.uppercase() }
        val site = FreeSite("c_" + host, name, url, host, 0xFF8A93A6, custom = true)
        save(ctx, custom(ctx).filter { it.id != site.id } + site)
        return site
    }

    fun remove(ctx: Context, site: FreeSite) = save(ctx, custom(ctx).filter { it.id != site.id })

    private fun save(ctx: Context, list: List<FreeSite>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("sub", it.sub).put("desktop", it.desktop)) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("custom", a.toString()).apply()
    }
}

fun openSite(ctx: Context, site: FreeSite) {
    ctx.startActivity(
        Intent(ctx, WebPlayerActivity::class.java)
            .putExtra(WebPlayerActivity.EXTRA_URL, site.url)
            .putExtra(WebPlayerActivity.EXTRA_NAME, site.name)
            .putExtra(WebPlayerActivity.EXTRA_DESKTOP, site.desktop)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

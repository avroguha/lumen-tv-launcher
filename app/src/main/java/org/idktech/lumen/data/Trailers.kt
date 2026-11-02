package org.idktech.lumen.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val TAG = "Lumen.Trailer"

/**
 * Trailers, as YouTube video ids, from two sources in order:
 *  1. TMDB (The Movie Database), when a key is set: search the title, then its `videos`,
 *     keeping YouTube entries of type Trailer (official first), then Teaser.
 *  2. YouTube's own results page for "<title> <year> official trailer" (no key; the first
 *     `"videoId"` sits deep in a ~1.2 MB page, so the whole page is read).
 * The id is remembered per title; "" means "looked, found nothing".
 */
/** What the lookup did: the id, and a short note for the panel when there is none ("yt: HTTP 429", "tmdb: 0 results"). */
data class TrailerResult(val id: String?, val note: String)

object Trailers {
    /** The last lookup's note, per title, for the detail panel. */
    val notes = HashMap<String, String>()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    private const val PREFS = "trailers.v2"
    private val ID = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"")

    fun tmdbKey(ctx: Context): String? = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("tmdb_key", null)?.trim()?.ifBlank { null }
    fun setTmdbKey(ctx: Context, key: String?) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString("tmdb_key", key?.trim()).apply()
        // a new key may know titles the old lookups missed: forget the "nothing found" records
        if (!key.isNullOrBlank()) {
            val e = p.edit()
            for ((k, v) in p.all) if (k != "tmdb_key" && v == "") e.remove(k)
            e.apply()
        }
    }

    suspend fun find(ctx: Context, t: Title): String? = withContext(Dispatchers.IO) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.contains(t.id)) { notes[t.id] = "cached"; return@withContext prefs.getString(t.id, null)?.ifBlank { null } }
        var id: String? = null
        var looked = false
        val note = StringBuilder()
        tmdbKey(ctx)?.let { key ->
            runCatching { id = tmdb(key, t); looked = true; note.append(if (id != null) "tmdb ok" else "tmdb: no trailer") }
                .onFailure { Log.w(TAG, "tmdb failed for ${t.title}", it); note.append("tmdb: ${it.message?.take(60)}") }
        }
        if (id == null) {
            val q = URLEncoder.encode("${t.title} ${if (t.year > 0) t.year.toString() else ""} official trailer", "UTF-8")
            val html = runCatching { get("https://www.youtube.com/results?search_query=$q&sp=EgIQAQ%253D%253D") }
                .onFailure { Log.w(TAG, "youtube search failed for ${t.title}", it); note.append("  yt: ${it.javaClass.simpleName} ${it.message?.take(60)}") }.getOrNull()
            if (html != null) {
                looked = true; id = ID.find(html)?.groupValues?.get(1)
                note.append("  yt: ${html.length / 1024} KB, ${if (id != null) "id found" else "no id"}")
            }
        }
        notes[t.id] = note.toString().trim()
        if (looked) prefs.edit().putString(t.id, id ?: "").apply()
        id
    }

    /** TMDB: a v3 key (`api_key=`) or a v4 read-access token (`Bearer`, starts with "eyJ"). */
    private fun tmdb(key: String, t: Title): String? {
        val kind = if (t.isShow) "tv" else "movie"
        val yearParam = if (t.year > 0) (if (t.isShow) "&first_air_date_year=${t.year}" else "&year=${t.year}") else ""
        val search = JSONObject(tmdbGet("https://api.themoviedb.org/3/search/$kind?query=${URLEncoder.encode(t.title, "UTF-8")}$yearParam&include_adult=false", key))
        val results = search.optJSONArray("results") ?: return null
        if (results.length() == 0) return null
        val tmdbId = results.getJSONObject(0).optLong("id")
        val videos = JSONObject(tmdbGet("https://api.themoviedb.org/3/$kind/$tmdbId/videos", key)).optJSONArray("results") ?: return null
        var teaser: String? = null; var trailer: String? = null; var official: String? = null
        for (i in 0 until videos.length()) {
            val v = videos.getJSONObject(i)
            if (v.optString("site") != "YouTube") continue
            val k = v.optString("key"); if (k.isBlank()) continue
            when (v.optString("type")) {
                "Trailer" -> { if (v.optBoolean("official") && official == null) official = k; if (trailer == null) trailer = k }
                "Teaser" -> if (teaser == null) teaser = k
            }
        }
        return official ?: trailer ?: teaser
    }

    private fun tmdbGet(url: String, key: String): String {
        val bearer = key.startsWith("eyJ")
        val full = if (bearer) url else url + (if ('?' in url) "&" else "?") + "api_key=" + URLEncoder.encode(key, "UTF-8")
        val conn = (URL(full).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            if (bearer) setRequestProperty("Authorization", "Bearer $key")
        }
        try {
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("TMDB HTTP $code: ${text.take(160)}")
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 15_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Accept-Language", "en-IN,en;q=0.9")
            setRequestProperty("Cookie", "CONSENT=YES+1")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

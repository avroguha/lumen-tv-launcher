package org.idktech.lumen.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Lumen.JW"
private const val ENDPOINT = "https://apis.justwatch.com/graphql"
private const val IMG = "https://images.justwatch.com"
private const val CACHE_TTL_MS = 6L * 60 * 60 * 1000

/** One streaming offer for a title: which app, free or paid, and how to open it there. */
data class Offer(
    val tech: String,
    val name: String,
    val packageName: String,
    val monetization: String,  // FLATRATE | FREE | ADS
    val deeplink: String?,
    val web: String?,
) {
    val isFree: Boolean get() = monetization == "FREE" || monetization == "ADS"
}

data class Title(
    val id: String,
    val isShow: Boolean,
    val title: String,
    val year: Int,
    val overview: String,
    val genres: List<String>,
    val imdb: Double?,
    val poster: String?,
    val backdrop: String?,
    val offers: List<Offer>,
    val languages: Set<String> = emptySet(),   // ISO codes inferred from the original title's script and audio tracks
) {
    val hasFree: Boolean get() = offers.any { it.isFree }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("show", isShow); put("title", title); put("year", year); put("overview", overview)
        put("genres", JSONArray(genres)); put("imdb", imdb ?: JSONObject.NULL); put("poster", poster); put("backdrop", backdrop)
        put("langs", JSONArray(languages.toList()))
        put("offers", JSONArray().apply { offers.forEach { o -> put(JSONObject().apply { put("tech", o.tech); put("name", o.name); put("pkg", o.packageName); put("mon", o.monetization); put("deep", o.deeplink); put("web", o.web) }) } })
    }

    companion object {
        fun fromJson(j: JSONObject): Title {
            val g = j.optJSONArray("genres") ?: JSONArray(); val l = j.optJSONArray("langs") ?: JSONArray(); val o = j.optJSONArray("offers") ?: JSONArray()
            return Title(
                j.getString("id"), j.optBoolean("show"), j.optString("title"), j.optInt("year"), j.optString("overview"),
                (0 until g.length()).map { g.getString(it) }, if (j.isNull("imdb")) null else j.optDouble("imdb"),
                j.optString("poster").ifBlank { null }, j.optString("backdrop").ifBlank { null },
                (0 until o.length()).map { i -> val x = o.getJSONObject(i); Offer(x.optString("tech"), x.optString("name"), x.optString("pkg"), x.optString("mon"), x.optString("deep").ifBlank { null }, x.optString("web").ifBlank { null }) },
                (0 until l.length()).map { l.getString(it) }.toSet(),
            )
        }
    }
}

enum class Kind { MOVIE, SHOW }

/** One page of a row: the titles, and where the next page starts (null = no more). */
data class Page(val titles: List<Title>, val next: String?)

/** A row on Home. `kind` null = films and series mixed. */
data class RowSpec(val key: String, val label: String, val genre: String?, val kind: Kind? = null, val freeOnly: Boolean = false)

val DISCOVER_ROWS = listOf(
    RowSpec("free", "Free to watch", null, freeOnly = true),
    RowSpec("m_trend", "Trending movies", null, Kind.MOVIE),
    RowSpec("s_trend", "Trending series", null, Kind.SHOW),
    RowSpec("m_act", "Action movies", "act", Kind.MOVIE),
    RowSpec("m_cmy", "Comedy movies", "cmy", Kind.MOVIE),
    RowSpec("m_hrr", "Horror movies", "hrr", Kind.MOVIE),
    RowSpec("m_trl", "Thrillers", "trl", Kind.MOVIE),
    RowSpec("m_rma", "Romance movies", "rma", Kind.MOVIE),
    RowSpec("m_drm", "Drama movies", "drm", Kind.MOVIE),
    RowSpec("m_scf", "Sci-Fi movies", "scf", Kind.MOVIE),
    RowSpec("m_ani", "Animation", "ani", Kind.MOVIE),
    RowSpec("m_doc", "Documentaries", "doc", Kind.MOVIE),
    RowSpec("s_drm", "Drama series", "drm", Kind.SHOW),
    RowSpec("s_crm", "Crime series", "crm", Kind.SHOW),
    RowSpec("s_cmy", "Comedy series", "cmy", Kind.SHOW),
    RowSpec("s_trl", "Thriller series", "trl", Kind.SHOW),
    RowSpec("s_act", "Action series", "act", Kind.SHOW),
)

/** Language chips. Code = ISO 639-1 used in `Title.languages`; null = everything. */
val LANGUAGES = listOf(
    null to "All", "hi" to "Hindi", "en" to "English", "ta" to "Tamil", "te" to "Telugu",
    "ml" to "Malayalam", "kn" to "Kannada", "bn" to "Bengali", "mr" to "Marathi", "ko" to "Korean", "ja" to "Japanese",
)

val GENRE_NAMES = mapOf(
    "act" to "Action", "ani" to "Animation", "cmy" to "Comedy", "crm" to "Crime", "doc" to "Documentary",
    "drm" to "Drama", "fml" to "Family", "fnt" to "Fantasy", "hst" to "History", "hrr" to "Horror",
    "msc" to "Music", "rma" to "Romance", "scf" to "Sci-Fi", "spt" to "Sport", "trl" to "Thriller",
    "war" to "War", "wsn" to "Western", "eur" to "European",
)

val JW_PACKAGES: Map<String, Pair<String, String>> = mapOf(
    "netflix" to ("Netflix" to "com.netflix.ninja"),
    "amazonprimevideo" to ("Prime Video" to "com.amazon.amazonvideo.livingroom"),
    "amazonprimevideowithads" to ("Prime Video" to "com.amazon.amazonvideo.livingroom"),
    "jiohotstar" to ("JioHotstar" to "in.startv.hotstar"),
    "sonyliv" to ("Sony LIV" to "com.sonyliv"),
    "zee5" to ("Zee5" to "com.graymatrix.did"),
    "appletvplus" to ("Apple TV" to "com.apple.atve.androidtv.appletv"),
    "mxplayer" to ("MX Player" to "com.mxtech.videoplayer.television"),
    "hoichoi" to ("Hoichoi" to "com.viewlift.hoichoi"),
    "aha" to ("aha" to "ahaflix.tv"),
    "sunnxt" to ("Sun NXT" to "com.suntv.sunnxt"),
    "discoveryplus" to ("Discovery+" to "com.discoveryplus.androidtv"),
    "lionsgateplay" to ("Lionsgate Play" to "com.lionsgateplay.videoapp"),
)

object JustWatch {

    fun installedPackages(ctx: Context): List<String> {
        val pm = ctx.packageManager
        return JW_PACKAGES.filter { (_, v) -> runCatching { pm.getPackageInfo(v.second, 0) }.isSuccess }.keys.toList()
    }

    /**
     * One page of a row. The first page (no cursor) is cached for 12 h; later pages are fetched
     * as the row is browsed. `after` is JustWatch's opaque cursor from the previous page.
     */
    suspend fun row(ctx: Context, spec: RowSpec, packages: List<String>, country: String, count: Int = 24, after: String? = null, force: Boolean = false): Page =
        withContext(Dispatchers.IO) {
            val cache = File(ctx.filesDir, "discover/$country.${spec.key}.json")
            cache.parentFile?.mkdirs()
            if (!force && after == null && cache.exists() && System.currentTimeMillis() - cache.lastModified() < CACHE_TTL_MS) {
                runCatching { parsePage(JSONObject(cache.readText())) }.getOrNull()?.let { return@withContext it }
            }
            val json = runCatching { post(query(spec, packages, country, count, after = after)) }.getOrElse {
                Log.w(TAG, "fetch failed for ${spec.key}", it)
                return@withContext if (after == null && cache.exists()) runCatching { parsePage(JSONObject(cache.readText())) }.getOrDefault(Page(emptyList(), null)) else Page(emptyList(), null)
            }
            val page = parsePage(json)
            if (after == null && page.titles.isNotEmpty()) runCatching { cache.writeText(json.toString()) }
            page
        }

    /** Free-text search across the installed apps' catalogs. Not cached. */
    suspend fun search(ctx: Context, text: String, packages: List<String>, country: String = "IN", count: Int = 21): List<Title> = withContext(Dispatchers.IO) {
        runCatching { parseTitles(post(query(RowSpec("search", "Search", null), packages, country, count, text))) }
            .getOrElse { Log.w(TAG, "search failed", it); emptyList() }
    }

    private fun query(spec: RowSpec, packages: List<String>, country: String, count: Int, searchQuery: String? = null, after: String? = null): String {
        val filter = JSONObject().apply {
            if (searchQuery != null) put("searchQuery", searchQuery)
            put("packages", JSONArray(packages))
            put("objectTypes", JSONArray(when (spec.kind) { Kind.MOVIE -> listOf("MOVIE"); Kind.SHOW -> listOf("SHOW"); null -> listOf("MOVIE", "SHOW") }))
            put("monetizationTypes", JSONArray(if (spec.freeOnly) listOf("FREE", "ADS") else listOf("FLATRATE", "FREE", "ADS")))
            if (spec.genre != null) put("genres", JSONArray(listOf(spec.genre)))
            put("excludeIrrelevantTitles", false)
        }
        val variables = JSONObject().apply {
            put("country", country); put("language", "en"); put("first", count)
            put("after", after ?: JSONObject.NULL)
            put("platform", "ANDROID_TV"); put("sortBy", "POPULAR"); put("filter", filter)
        }
        return JSONObject().apply { put("query", GQL); put("variables", variables) }.toString()
    }

    private fun post(body: String): JSONObject {
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 15_000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12; TV) Lumen/0.3")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("HTTP $code: ${text.take(300)}")
            val json = JSONObject(text)
            if (json.has("errors")) throw IllegalStateException("GraphQL: ${json.getJSONArray("errors").toString().take(300)}")
            return json
        } finally {
            conn.disconnect()
        }
    }

    private fun parsePage(json: JSONObject): Page {
        val pt = json.getJSONObject("data").getJSONObject("popularTitles")
        val info = pt.optJSONObject("pageInfo")
        val next = if (info != null && info.optBoolean("hasNextPage")) info.optString("endCursor").ifBlank { null } else null
        return Page(parseTitles(json), next)
    }

    private fun parseTitles(json: JSONObject): List<Title> {
        val edges = json.getJSONObject("data").getJSONObject("popularTitles").getJSONArray("edges")
        val out = ArrayList<Title>(edges.length())
        for (i in 0 until edges.length()) {
            val node = edges.getJSONObject(i).getJSONObject("node")
            val c = node.optJSONObject("content") ?: continue
            val offers = ArrayList<Offer>()
            val seen = HashSet<String>()
            val langs = HashSet<String>()
            val oa = node.optJSONArray("offers") ?: JSONArray()
            for (j in 0 until oa.length()) {
                val o = oa.getJSONObject(j)
                val tech = o.getJSONObject("package").optString("technicalName")
                val app = JW_PACKAGES[tech] ?: continue
                o.optJSONArray("audioLanguages")?.let { a -> for (k in 0 until a.length()) langs += a.getString(k).take(2) }
                val mon = o.optString("monetizationType")
                val key = "${app.second}/${mon == "FREE" || mon == "ADS"}"
                if (!seen.add(key)) continue
                offers += Offer(tech, app.first, app.second, mon, o.optString("deeplinkURL").ifBlank { null }, o.optString("standardWebURL").ifBlank { null })
            }
            if (offers.isEmpty()) continue
            val countries = c.optJSONArray("productionCountries")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
            scriptLanguage(c.optString("originalTitle"), countries)?.let { langs += it }
            val genres = c.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.getJSONObject(it).optString("shortName") } } ?: emptyList()
            val backdrops = c.optJSONArray("backdrops")
            out += Title(
                id = node.optString("id"),
                isShow = node.optString("objectType") == "SHOW",
                title = c.optString("title"),
                year = c.optInt("originalReleaseYear", 0),
                overview = c.optString("shortDescription"),
                genres = genres.mapNotNull { GENRE_NAMES[it] },
                imdb = c.optJSONObject("scoring")?.let { s -> if (s.isNull("imdbScore")) null else s.optDouble("imdbScore") },
                poster = c.optString("posterUrl").ifBlank { null }?.let { image(it, "s332") },
                backdrop = backdrops?.takeIf { it.length() > 0 }?.getJSONObject(0)?.optString("backdropUrl")?.ifBlank { null }?.let { image(it, "s1440") },
                offers = offers.sortedBy { if (it.isFree) 0 else 1 },
                languages = langs,
            )
        }
        return out
    }

    /** The script of the original title names the language for Indian and East Asian titles; Latin titles fall back to country. */
    private fun scriptLanguage(original: String, countries: List<String>): String? {
        for (ch in original) {
            when (ch.code) {
                in 0x0900..0x097F -> return if ("IN" in countries && original.contains('ळ')) "mr" else "hi"
                in 0x0980..0x09FF -> return "bn"
                in 0x0A00..0x0A7F -> return "pa"
                in 0x0A80..0x0AFF -> return "gu"
                in 0x0B80..0x0BFF -> return "ta"
                in 0x0C00..0x0C7F -> return "te"
                in 0x0C80..0x0CFF -> return "kn"
                in 0x0D00..0x0D7F -> return "ml"
                in 0xAC00..0xD7AF -> return "ko"
                in 0x3040..0x30FF -> return "ja"
                in 0x4E00..0x9FFF -> return "zh"
                in 0x0600..0x06FF -> return "ar"
                in 0x0400..0x04FF -> return "ru"
                in 0x0E00..0x0E7F -> return "th"
            }
        }
        if (original.isBlank()) return null
        return when {
            countries.any { it in setOf("US", "GB", "CA", "AU", "NZ", "IE") } -> "en"
            countries.any { it in setOf("ES", "MX", "AR", "CO") } -> "es"
            "FR" in countries -> "fr"
            "DE" in countries -> "de"
            "IT" in countries -> "it"
            "BR" in countries || "PT" in countries -> "pt"
            "TR" in countries -> "tr"
            "KR" in countries -> "ko"
            "JP" in countries -> "ja"
            "IN" in countries -> "en"
            else -> null
        }
    }

    private fun image(path: String, profile: String) = IMG + path.replace("{profile}", profile).replace("{format}", "webp")

    private val GQL = """
        query T(${'$'}country: Country!, ${'$'}language: Language!, ${'$'}first: Int!, ${'$'}after: String, ${'$'}filter: TitleFilter, ${'$'}sortBy: PopularTitlesSorting!, ${'$'}platform: Platform!) {
          popularTitles(country: ${'$'}country, first: ${'$'}first, after: ${'$'}after, filter: ${'$'}filter, sortBy: ${'$'}sortBy) {
            pageInfo { endCursor hasNextPage }
            edges { node {
              id objectType
              content(country: ${'$'}country, language: ${'$'}language) {
                title originalTitle productionCountries originalReleaseYear shortDescription genres { shortName }
                scoring { imdbScore } posterUrl backdrops { backdropUrl }
              }
              ... on MovieOrShow {
                offers(country: ${'$'}country, platform: ${'$'}platform) {
                  monetizationType deeplinkURL(platform: ${'$'}platform) standardWebURL audioLanguages
                  package { technicalName }
                }
              }
            } }
          }
        }
    """.trimIndent()
}

/** Titles opened through Lumen, newest first, per profile: the launcher's own "Continue" row. */
object HistoryStore {
    fun load(ctx: Context): List<Title> {
        val raw = ProfileStore.prefs(ctx, "history").getString("titles", null) ?: return emptyList()
        return runCatching { val a = JSONArray(raw); (0 until a.length()).map { Title.fromJson(a.getJSONObject(it)) } }.getOrDefault(emptyList())
    }

    fun add(ctx: Context, t: Title) {
        val next = listOf(t) + load(ctx).filter { it.id != t.id }
        val arr = JSONArray(); next.take(20).forEach { arr.put(it.toJson()) }
        ProfileStore.prefs(ctx, "history").edit().putString("titles", arr.toString()).apply()
    }
}

/** The language chip chosen on Home, per profile. */
object LanguageStore {
    fun get(ctx: Context): String? = ProfileStore.prefs(ctx, "language").getString("code", null)
    fun set(ctx: Context, code: String?) { ProfileStore.prefs(ctx, "language").edit().putString("code", code).apply() }
}

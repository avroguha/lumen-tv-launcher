package org.idktech.lumen.web

import android.content.Context
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Blocks requests to advertising and tracking hosts inside Lumen's web player.
 *
 * The list is StevenBlack's unified hosts file (the one Pi-hole uses), fetched by the TV itself
 * and kept in Lumen's files. Only a version at least 72 hours old is ever taken: the newest commit
 * to `hosts` before that cutoff, looked up through GitHub's API, with a known-good commit as the
 * fallback. It is refreshed weekly. Until the first download lands, a short built-in list of the
 * big ad networks does the job.
 *
 * A host is blocked when it, or any domain above it, is on the list (`pubads.g.doubleclick.net`
 * falls under `doubleclick.net`).
 */
object AdBlocker {
    private const val TAG = "Lumen.AdBlock"
    private const val REPO = "StevenBlack/hosts"
    /** 2026-11-08, used when GitHub's API cannot be reached. */
    private const val PINNED = "6866cc66859a4ba99d945f076a09bca74a496d4c"
    private const val MIN_AGE_MS = 72L * 3600_000
    private const val REFRESH_MS = 7L * 24 * 3600_000

    /** Video-ad and pop-up networks, some of which the hosts file leaves out. */
    private val BUILTIN = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com", "2mdn.net",
        "amazon-adsystem.com", "adnxs.com", "rubiconproject.com", "pubmatic.com", "openx.net", "criteo.com", "criteo.net",
        "springserve.com", "spotxchange.com", "spotx.tv", "fwmrm.net", "freewheel.tv", "innovid.com", "adsrvr.org",
        "teads.tv", "lkqd.net", "tremorhub.com", "aniview.com", "vidoomy.com", "connatix.com", "smartadserver.com",
        "taboola.com", "outbrain.com", "moatads.com", "doubleverify.com", "adsafeprotected.com", "serving-sys.com",
        "propellerads.com", "popads.net", "popcash.net", "adsterra.com", "exoclick.com", "hilltopads.net", "onclickads.net",
        "google-analytics.com", "googletagmanager.com", "scorecardresearch.com", "quantserve.com",
    )

    @Volatile private var hosts: Set<String> = emptySet()
    @Volatile private var started = false
    /** Requests refused since the player opened (reset per site). */
    val blocked = AtomicInteger()
    @Volatile var listSize = 0; private set
    @Volatile var listVersion: String? = null; private set

    fun start(ctx: Context) {
        if (started) return
        started = true
        val app = ctx.applicationContext
        thread(name = "adblock", isDaemon = true) {
            val file = File(app.filesDir, "adblock/hosts.txt")
            if (file.exists()) load(file)
            val prefs = app.getSharedPreferences("adblock", Context.MODE_PRIVATE)
            if (!file.exists() || System.currentTimeMillis() - prefs.getLong("fetched", 0) > REFRESH_MS) {
                runCatching { refresh(file) }
                    .onSuccess { prefs.edit().putLong("fetched", System.currentTimeMillis()).apply(); load(file) }
                    .onFailure { Log.w(TAG, "list refresh failed; using ${if (hosts.isEmpty()) "the built-in list" else "the saved one"}", it) }
            }
        }
    }

    /** Ads a site serves from its own servers, recognisable only by the path (Plex: vod-content.plexvideos.com/ad-server/…). */
    private val AD_PATHS = listOf("/ad-server/", "/adserver/", "/ads/vast", "/vast.xml", "/vmap")

    fun isBlocked(url: android.net.Uri): Boolean {
        if (isBlocked(url.host)) return true
        val path = url.path?.lowercase() ?: return false
        return AD_PATHS.any { path.contains(it) }
    }

    fun isBlocked(host: String?): Boolean {
        var h = host?.lowercase()?.trimEnd('.') ?: return false
        val list = hosts
        while (true) {
            if (h in BUILTIN || h in list) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
            if (h.indexOf('.') < 0) return false   // never a whole top-level domain
        }
    }

    private fun load(file: File) {
        val set = HashSet<String>(90_000)
        var version: String? = null
        file.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                if (line.startsWith("#lumen-sha ")) { version = line.substring(11).trim(); return@forEach }
                val t = line.trim()
                if (t.isEmpty() || t[0] == '#') return@forEach
                val parts = t.split(' ', '\t').filter { it.isNotEmpty() }
                if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                    val h = parts[1].lowercase()
                    if (h != "0.0.0.0" && h != "localhost" && h.contains('.')) set.add(h)
                }
            }
        }
        hosts = set
        listSize = set.size
        listVersion = version
        Log.i(TAG, "loaded ${set.size} hosts from ${version?.take(7) ?: "?"}")
    }

    /** The newest commit to the list that is at least 72 hours old. */
    private fun agedCommit(): String {
        val until = java.time.Instant.ofEpochMilli(System.currentTimeMillis() - MIN_AGE_MS).toString()
        return runCatching {
            val body = get("https://api.github.com/repos/$REPO/commits?path=hosts&per_page=1&until=$until")
            Regex("\"sha\"\\s*:\\s*\"([0-9a-f]{40})\"").find(body)?.groupValues?.get(1) ?: error("no sha in reply")
        }.getOrElse { Log.w(TAG, "GitHub API: ${it.message}; using the pinned list"); PINNED }
    }

    private fun refresh(file: File) {
        val sha = agedCommit()
        if (sha == listVersion && file.exists()) { Log.i(TAG, "list already at ${sha.take(7)}"); return }
        val text = get("https://raw.githubusercontent.com/$REPO/$sha/hosts")
        require(text.length > 500_000 && text.contains("0.0.0.0 ")) { "unexpected list (${text.length} bytes)" }
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "hosts.tmp")
        tmp.writeText("#lumen-sha $sha\n$text")
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        Log.i(TAG, "downloaded list ${sha.take(7)} (${text.length / 1024} KB)")
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Lumen-TV-launcher")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (c.responseCode !in 200..299) error("HTTP ${c.responseCode} from $url")
            return c.inputStream.bufferedReader().readText()
        } finally { c.disconnect() }
    }
}

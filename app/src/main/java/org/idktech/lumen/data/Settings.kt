package org.idktech.lumen.data

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.compositionLocalOf

/**
 * Everything the Settings tab manages. Global to the TV (not per profile): they describe the
 * launcher's look and the hardware, not a person's taste in films.
 */
data class Prefs(
    val theme: String = "aurora",        // see THEMES in ui/Glass.kt
    val motion: Boolean = true,          // the aurora drifts and the stars twinkle
    val blur: Boolean = true,            // real backdrop blur on bar and panels (off = cheaper glass)
    val statusLine: Boolean = true,      // connection / RAM / storage / CPU under the greeting
    val tileSize: String = "m",          // s | m | l  — the Apps row
    val youtubeRow: Boolean = true,
    val backdrop: Boolean = false,       // the focused title's backdrop behind the page (off: the theme's aurora only)
    val filmRows: Boolean = true,
    val seriesRows: Boolean = true,
    val clock: String = "auto",          // auto | 12 | 24
    val tempUnit: String = "c",          // c | f
    val floatingClock: String = "off",   // off | tl | tr | bl | br — the clock drawn over other apps
    val floatingSize: String = "s",      // s | m
    val voice: String = "google",        // google | lumen | <package name of an assistant app>
    val country: String = "IN",          // JustWatch country: which catalogs and offers
    val dreamAfter: Int = 10,            // minutes idle on Lumen before the screensaver starts; 0 = leave it to the TV
) {
    fun is24h(ctx: Context): Boolean = when (clock) { "12" -> false; "24" -> true; else -> DateFormat.is24HourFormat(ctx) }
    fun timePattern(ctx: Context): String = if (is24h(ctx)) "HH:mm" else "h:mm a"
    /** The time split for display: "10:14" and, in 12-hour mode, a small "AM" / "PM" beside it. */
    fun timeParts(ctx: Context, t: java.time.ZonedDateTime): Pair<String, String?> =
        if (is24h(ctx)) t.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) to null
        else t.format(java.time.format.DateTimeFormatter.ofPattern("h:mm")) to t.format(java.time.format.DateTimeFormatter.ofPattern("a")).uppercase()
    fun temp(c: Int): String = if (tempUnit == "f") "${Math.round(c * 9f / 5f + 32)}°" else "$c°"
}

object SettingsStore {
    private const val PREFS = "settings"
    private fun p(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): Prefs {
        val d = Prefs()
        val s = p(ctx)
        return Prefs(
            theme = s.getString("theme", d.theme) ?: d.theme,
            motion = s.getBoolean("motion", d.motion),
            blur = s.getBoolean("blur", d.blur),
            statusLine = s.getBoolean("statusLine", d.statusLine),
            tileSize = s.getString("tileSize", d.tileSize) ?: d.tileSize,
            youtubeRow = s.getBoolean("youtubeRow", d.youtubeRow),
            backdrop = s.getBoolean("backdrop2", d.backdrop),
            filmRows = s.getBoolean("filmRows", d.filmRows),
            seriesRows = s.getBoolean("seriesRows", d.seriesRows),
            clock = s.getString("clock", d.clock) ?: d.clock,
            tempUnit = s.getString("tempUnit", d.tempUnit) ?: d.tempUnit,
            floatingClock = s.getString("floatingClock", d.floatingClock) ?: d.floatingClock,
            floatingSize = s.getString("floatingSize", d.floatingSize) ?: d.floatingSize,
            voice = s.getString("voice", d.voice) ?: d.voice,
            country = s.getString("country", d.country) ?: d.country,
            dreamAfter = s.getInt("dreamAfter", d.dreamAfter),
        )
    }

    fun save(ctx: Context, x: Prefs) {
        p(ctx).edit()
            .putString("theme", x.theme).putBoolean("motion", x.motion).putBoolean("blur", x.blur)
            .putBoolean("statusLine", x.statusLine).putString("tileSize", x.tileSize).putBoolean("youtubeRow", x.youtubeRow)
            .putBoolean("backdrop2", x.backdrop).putBoolean("filmRows", x.filmRows).putBoolean("seriesRows", x.seriesRows)
            .putString("clock", x.clock).putString("tempUnit", x.tempUnit)
            .putString("floatingClock", x.floatingClock).putString("floatingSize", x.floatingSize)
            .putString("voice", x.voice).putString("country", x.country).putInt("dreamAfter", x.dreamAfter)
            .apply()
    }
}

val LocalPrefs = compositionLocalOf { Prefs() }

/** JustWatch countries offered in Settings (code to name). */
val COUNTRIES = listOf("IN" to "India", "US" to "United States", "GB" to "United Kingdom", "AE" to "UAE", "CA" to "Canada", "AU" to "Australia", "SG" to "Singapore")

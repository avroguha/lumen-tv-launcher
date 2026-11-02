package org.idktech.lumen.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

private const val TAG = "Lumen.Weather"

data class Day(val date: LocalDate, val code: Int, val hiC: Int, val loC: Int)

data class Weather(
    val city: String,
    val tempC: Int,
    val feelsC: Int,
    val humidity: Int,
    val windKmh: Int,
    val code: Int,          // WMO weather code
    val isDay: Boolean,
    val hiC: Int,
    val loC: Int,
    val days: List<Day>,    // today + 6
    val fetchedAt: Long,
) {
    val label: String get() = wmoLabel(code)
}

fun wmoLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63, 65 -> "Rain"
    66, 67 -> "Freezing rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Hail storm"
    else -> "—"
}

/**
 * Weather without any server of ours: the TV's public IP gives the city (ip-api.com, free,
 * plain HTTP on its free tier), Open-Meteo gives the forecast (free, HTTPS, no key).
 */
object WeatherRepo {
    private const val PREFS = "weather"
    private const val TTL_MS = 30L * 60 * 1000

    fun cached(ctx: Context): Weather? {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val json = p.getString("last", null) ?: return null
        return runCatching { fromJson(JSONObject(json)) }.getOrNull()
    }

    suspend fun current(ctx: Context, force: Boolean = false): Weather? = withContext(Dispatchers.IO) {
        val c = cached(ctx)
        if (!force && c != null && System.currentTimeMillis() - c.fetchedAt < TTL_MS) return@withContext c
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        try {
            var city = p.getString("city", null)
            var lat = p.getString("lat", null)?.toDoubleOrNull()
            var lon = p.getString("lon", null)?.toDoubleOrNull()
            if (city == null || lat == null || lon == null) {
                val ip = JSONObject(get("http://ip-api.com/json/?fields=status,city,lat,lon"))
                if (ip.optString("status") != "success") throw IllegalStateException("ip-api: $ip")
                city = ip.getString("city"); lat = ip.getDouble("lat"); lon = ip.getDouble("lon")
                p.edit().putString("city", city).putString("lat", lat.toString()).putString("lon", lon.toString()).apply()
            }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,is_day,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=7"
            val j = JSONObject(get(url))
            val cur = j.getJSONObject("current")
            val daily = j.getJSONObject("daily")
            val dates = daily.getJSONArray("time"); val codes = daily.getJSONArray("weather_code")
            val his = daily.getJSONArray("temperature_2m_max"); val los = daily.getJSONArray("temperature_2m_min")
            val days = (0 until dates.length()).map { i ->
                Day(LocalDate.parse(dates.getString(i)), codes.getInt(i), Math.round(his.getDouble(i)).toInt(), Math.round(los.getDouble(i)).toInt())
            }
            val w = Weather(
                city = city,
                tempC = Math.round(cur.getDouble("temperature_2m")).toInt(),
                feelsC = Math.round(cur.getDouble("apparent_temperature")).toInt(),
                humidity = cur.getInt("relative_humidity_2m"),
                windKmh = Math.round(cur.getDouble("wind_speed_10m")).toInt(),
                code = cur.getInt("weather_code"),
                isDay = cur.getInt("is_day") == 1,
                hiC = days.firstOrNull()?.hiC ?: 0,
                loC = days.firstOrNull()?.loC ?: 0,
                days = days,
                fetchedAt = System.currentTimeMillis(),
            )
            p.edit().putString("last", toJson(w).toString()).apply()
            w
        } catch (e: Exception) {
            Log.w(TAG, "weather fetch failed", e)
            c
        }
    }

    fun resetLocation(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("city").remove("lat").remove("lon").remove("last").apply()
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000
            setRequestProperty("User-Agent", "Lumen/0.3 (Android TV)")
        }
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode} for $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun toJson(w: Weather) = JSONObject().apply {
        put("city", w.city); put("tempC", w.tempC); put("feelsC", w.feelsC); put("humidity", w.humidity)
        put("windKmh", w.windKmh); put("code", w.code); put("isDay", w.isDay); put("hiC", w.hiC); put("loC", w.loC)
        put("fetchedAt", w.fetchedAt)
        put("days", JSONArray().apply { w.days.forEach { d -> put(JSONObject().apply { put("d", d.date.toString()); put("c", d.code); put("h", d.hiC); put("l", d.loC) }) } })
    }

    private fun fromJson(j: JSONObject): Weather {
        val arr = j.optJSONArray("days") ?: JSONArray()
        val days = (0 until arr.length()).map { i -> val o = arr.getJSONObject(i); Day(LocalDate.parse(o.getString("d")), o.getInt("c"), o.getInt("h"), o.getInt("l")) }
        return Weather(
            j.getString("city"), j.getInt("tempC"), j.getInt("feelsC"), j.getInt("humidity"), j.getInt("windKmh"),
            j.getInt("code"), j.getBoolean("isDay"), j.getInt("hiC"), j.getInt("loC"), days, j.getLong("fetchedAt"),
        )
    }
}

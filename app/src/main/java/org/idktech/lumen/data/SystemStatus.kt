package org.idktech.lumen.data

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Environment
import android.os.StatFs
import android.net.wifi.WifiManager
import androidx.compose.runtime.mutableStateListOf
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Status(
    val network: String,        // "Wi-Fi", "Ethernet", "No network"
    val online: Boolean,        // validated by Android AND answered our probe
    val latencyMs: Long?,       // round trip of the probe
    val ramUsedMb: Int,
    val ramTotalMb: Int,
    val storageFreeGb: Float,
    val storageTotalGb: Float,
    val cpuPct: Int?,           // whole-TV busy %, from /proc/stat (null when unreadable)
    val ssid: String? = null,   // Wi-Fi network name (needs the location permission; null otherwise)
    val ip: String? = null,     // the TV's IPv4 address on that network
    val remote: RemoteMic.Result = RemoteMic.Result(RemoteMic.State.UNAVAILABLE, null),
)

/** What the TV is doing right now: connection, internet reachability, memory, storage, CPU. */
object SystemStatus {
    private var lastIdle = 0L
    private var lastTotal = 0L
    /** The last 40 CPU samples (0..100), newest last, for the sparkline in the status line. */
    val cpuHistory = mutableStateListOf<Int>()
    /** "busy" when /proc/stat is readable, "clock" when only the CPU clock speed is (a proxy). */
    var cpuSource: String = "busy"
        private set

    /** Called every couple of seconds while the home screen is up. */
    private var sourceLogged = false
    fun sampleCpu() {
        val v = cpu() ?: cpuClock() ?: return
        if (!sourceLogged) { sourceLogged = true; android.util.Log.i("Lumen.Status", "cpu figure from $cpuSource") }
        cpuHistory.add(v)
        while (cpuHistory.size > 40) cpuHistory.removeAt(0)
    }

    /** Apps may not read /proc/stat on newer Android; the CPU clock in sysfs is open, and scales with load. */
    private fun cpuClock(): Int? = runCatching {
        val cpus = File("/sys/devices/system/cpu").listFiles { f -> f.name.matches(Regex("cpu\\d+")) }.orEmpty()
        val ratios = cpus.mapNotNull { c ->
            val cur = File(c, "cpufreq/scaling_cur_freq").readText().trim().toLongOrNull() ?: return@mapNotNull null
            val max = File(c, "cpufreq/cpuinfo_max_freq").readText().trim().toLongOrNull() ?: return@mapNotNull null
            if (max > 0) cur.toDouble() / max else null
        }
        if (ratios.isEmpty()) null else { cpuSource = "clock"; (ratios.average() * 100).toInt().coerceIn(0, 100) }
    }.getOrNull()

    suspend fun read(ctx: Context): Status = withContext(Dispatchers.IO) {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = when {
            caps == null -> "No network"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            else -> "Network"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val latency = if (caps != null) probe() else null

        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt()
        val usedMb = ((mi.totalMem - mi.availMem) / (1024 * 1024)).toInt()

        val st = StatFs(Environment.getDataDirectory().path)
        val freeGb = st.availableBytes / 1e9f
        val totalGb = st.totalBytes / 1e9f

        val ssid = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) runCatching {
            @Suppress("DEPRECATION")
            ctx.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid?.trim('"')?.takeIf { it.isNotBlank() && !it.contains("unknown ssid", ignoreCase = true) && it != "0x" }
        }.getOrNull() else null
        val ip = runCatching {
            cm?.activeNetwork?.let { cm.getLinkProperties(it) }?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        }.getOrNull()
        val cpuNow = cpuHistory.lastOrNull() ?: cpu()
        Status(network, validated && latency != null, latency, usedMb, totalMb, freeGb, totalGb, cpuNow, ssid, ip, RemoteMic.read(ctx))
    }

    private fun probe(): Long? = runCatching {
        val t0 = System.nanoTime()
        val conn = (URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"; connectTimeout = 3000; readTimeout = 3000; useCaches = false
        }
        try { if (conn.responseCode in 200..399) (System.nanoTime() - t0) / 1_000_000 else null } finally { conn.disconnect() }
    }.getOrNull()

    private fun cpu(): Int? = runCatching {
        val line = File("/proc/stat").bufferedReader().use { it.readLine() }
        val f = line.trim().split(Regex("\\s+")).drop(1).map { it.toLong() }
        val idle = f[3] + f[4]; val total = f.sum()
        val dIdle = idle - lastIdle; val dTotal = total - lastTotal
        lastIdle = idle; lastTotal = total
        if (dTotal <= 0 || lastTotal == total) null else ((1.0 - dIdle.toDouble() / dTotal) * 100).toInt().coerceIn(0, 100)
    }.getOrNull()
}

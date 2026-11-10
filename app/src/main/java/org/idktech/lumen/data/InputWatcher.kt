package org.idktech.lumen.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.mutableIntStateOf

/**
 * Follows the TV's HDMI ports for the life of the process. It keeps the Input menu live (a device
 * that speaks HDMI-CEC appears as a new input the moment it powers on) and switches to a port when
 * the device on it turns on.
 *
 * Auto-switching cannot be left to HDMI-CEC here: an Xbox asks "who is the active source?", the
 * TV truthfully answers "me", and the console then never claims the screen. What is reliable is
 * the port itself going from standby to connected.
 */
object InputWatcher {
    private const val TAG = "Lumen.Input"
    /** Ports re-announce themselves while the TV wakes: not a device being switched on. */
    private const val SETTLE_MS = 20_000L
    /** A console re-links HDMI when it changes mode: one switch per power-on. */
    private const val COOLDOWN_MS = 60_000L
    private const val SWITCH_DELAY_MS = 1_200L

    /** Bumped on every change, so the Input menu reloads. */
    val version = mutableIntStateOf(0)

    private val main = Handler(Looper.getMainLooper())
    private val states = HashMap<String, Int>()
    private val lastSwitch = HashMap<String, Long>()
    private var started = false
    private var awakeSince = 0L

    fun start(context: Context) {
        if (started) return
        started = true
        val ctx = context.applicationContext
        val tim = ctx.getSystemService(TvInputManager::class.java) ?: return
        awakeSince = SystemClock.elapsedRealtime()
        runCatching { ports(tim).forEach { states[it.id] = tim.getInputState(it.id) } }
            .onFailure { Log.w(TAG, "tv inputs unavailable", it) }
        ctx.registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { awakeSince = SystemClock.elapsedRealtime() }
        }, IntentFilter(Intent.ACTION_SCREEN_ON))
        tim.registerCallback(object : TvInputManager.TvInputCallback() {
            override fun onInputStateChanged(inputId: String, state: Int) { changed(ctx, tim, inputId, state) }
            override fun onInputAdded(inputId: String) { refresh("added $inputId") }
            override fun onInputRemoved(inputId: String) { refresh("removed $inputId") }
            override fun onInputUpdated(inputId: String) { refresh("updated $inputId") }
        }, main)
        Log.i(TAG, "watching: $states")
    }

    private fun ports(tim: TvInputManager): List<TvInputInfo> =
        tim.tvInputList.filter { it.isPassthroughInput && it.parentId == null && it.type == TvInputInfo.TYPE_HDMI }

    private fun refresh(why: String) {
        Log.i(TAG, why)
        AppCache.invalidateInputs()
        version.intValue++
    }

    private fun changed(ctx: Context, tim: TvInputManager, id: String, state: Int) {
        val known = states.containsKey(id)
        val before = states[id]
        if (known) states[id] = state
        refresh("state $id: $before -> $state")
        if (!known || state != TvInputManager.INPUT_STATE_CONNECTED || before == TvInputManager.INPUT_STATE_CONNECTED) return
        val now = SystemClock.elapsedRealtime()
        val why = when {
            !SettingsStore.load(ctx).autoInput -> "auto-switch is off"
            ctx.getSystemService(PowerManager::class.java)?.isInteractive == false -> "the screen is off"
            now - awakeSince < SETTLE_MS -> "the TV has only just woken"
            now - (lastSwitch[id] ?: -COOLDOWN_MS) < COOLDOWN_MS -> "switched here under a minute ago"
            else -> null
        }
        if (why != null) { Log.i(TAG, "not switching to $id: $why"); return }
        lastSwitch[id] = now
        // A moment for the picture to lock, and for the device's name to arrive.
        main.postDelayed({
            if (runCatching { tim.getInputState(id) }.getOrNull() != TvInputManager.INPUT_STATE_CONNECTED) { Log.i(TAG, "$id went away again"); return@postDelayed }
            val input = Catalog.loadInputs(ctx).firstOrNull { it.inputId == id } ?: run { Log.w(TAG, "$id is not in the input list"); return@postDelayed }
            Log.i(TAG, "device turned on: switching to ${input.label}")
            Catalog.switchToInput(ctx, input)
        }, SWITCH_DELAY_MS)
    }
}

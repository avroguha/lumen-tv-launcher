package org.idktech.lumen.data

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Whether the remote's microphone can reach the TV. On a Google TV remote the buttons work over
 * infrared with no pairing at all, but the microphone is a Bluetooth link: when that link is
 * down every voice feature on the TV, Google's included, hears silence and says it did not
 * catch that. That cost a day of debugging once; the status line says it outright now.
 */
object RemoteMic {
    enum class State { CONNECTED, DISCONNECTED, NO_PERMISSION, UNAVAILABLE }
    data class Result(val state: State, val name: String?)

    private const val TAG = "Lumen.Remote"
    private const val HID_HOST = 4   // BluetoothProfile.HID_HOST: public behaviour, hidden constant
    private var hid: BluetoothProfile? = null
    private var requested = false

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun read(ctx: Context): Result {
        val bm = ctx.getSystemService(BluetoothManager::class.java) ?: return Result(State.UNAVAILABLE, null)
        val adapter: BluetoothAdapter = bm.adapter ?: return Result(State.UNAVAILABLE, null)
        if (!hasPermission(ctx)) return Result(State.NO_PERMISSION, null)
        if (runCatching { !adapter.isEnabled }.getOrDefault(true)) return Result(State.DISCONNECTED, null)
        if (hid == null && !requested) {
            requested = true
            runCatching {
                adapter.getProfileProxy(ctx.applicationContext, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) { hid = proxy }
                    override fun onServiceDisconnected(profile: Int) { hid = null; requested = false }
                }, HID_HOST)
            }.onFailure { Log.w(TAG, "no HID host proxy", it) }
        }
        // A remote is an input device (HID host side); a BLE remote also holds a GATT connection.
        val devices = runCatching { hid?.connectedDevices }.getOrNull().orEmpty()
            .ifEmpty { runCatching { bm.getConnectedDevices(BluetoothProfile.GATT) }.getOrNull().orEmpty() }
        if (devices.isEmpty()) return Result(State.DISCONNECTED, null)
        return Result(State.CONNECTED, runCatching { devices.first().name }.getOrNull())
    }

    /** One line for a person: what is wrong and where to fix it. */
    fun advice(r: Result): String = when (r.state) {
        State.CONNECTED -> "Remote mic connected" + (r.name?.let { " ($it)" } ?: "")
        State.DISCONNECTED -> "Remote mic NOT connected: pair the remote in the TV's Settings > Remotes & accessories (buttons work without it, the microphone does not)"
        State.NO_PERMISSION -> "Remote mic: allow Bluetooth for Lumen (Settings > System) to check it"
        State.UNAVAILABLE -> "Remote mic: no Bluetooth on this TV"
    }
}

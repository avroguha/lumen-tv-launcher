package org.idktech.lumen.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Brings the floating clock back after the TV restarts, when it is switched on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ClockOverlayService.sync(context, ClockOverlayService.ACTION_SHOW)
    }
}

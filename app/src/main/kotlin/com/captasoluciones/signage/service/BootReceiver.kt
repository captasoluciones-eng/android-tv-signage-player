package com.captasoluciones.signage.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starts the supervising foreground service as soon as the device finishes booting,
 * so the kiosk comes back up unattended after a power cycle. BOOT_COMPLETED (and the
 * QUICKBOOT_POWERON variant some TV/box vendors fire instead) are both allowed
 * exceptions to Android's background-start restrictions for foreground services.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val serviceIntent = Intent(context, WatchdogService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}

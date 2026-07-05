package com.example.androidautosound

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the reliable-mode service after a reboot. Bluetooth mode needs no
 * boot handling — its manifest receiver is always registered.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val readyForService = SoundPrefs.isEnabled(context) &&
            SoundPrefs.triggerMode(context) == SoundPrefs.MODE_CARCONNECTION &&
            SoundPrefs.connectUri(context) != null
        if (readyForService) CarConnectionService.start(context)
    }
}

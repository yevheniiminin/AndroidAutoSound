package com.example.androidautosound

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bluetooth-mode trigger. Fires when the user's chosen car connects/disconnects
 * over Bluetooth — no persistent service or notification required. Uses
 * goAsync() to stay alive while the (short) sound plays.
 */
class BluetoothReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!SoundPrefs.isEnabled(context)) return
        if (SoundPrefs.triggerMode(context) != SoundPrefs.MODE_BLUETOOTH) return
        if (!hasBluetoothPermission(context)) return

        val device = deviceFrom(intent) ?: return
        if (device.address != SoundPrefs.carAddress(context)) return

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> play(context, connect = true)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> play(context, connect = false)
        }
    }

    private fun play(context: Context, connect: Boolean) {
        val uri = when {
            connect -> SoundPrefs.connectUri(context)
            SoundPrefs.disconnectEnabled(context) -> SoundPrefs.disconnectUri(context)
            else -> null
        } ?: return

        val now = SystemClock.elapsedRealtime()
        if (connect) {
            if (now - lastConnect < DEBOUNCE_MS) return
            lastConnect = now
        } else {
            if (now - lastDisconnect < DEBOUNCE_MS) return
            lastDisconnect = now
        }

        val pending = goAsync()
        val released = AtomicBoolean(false)
        val finish = { if (released.compareAndSet(false, true)) pending.finish() }

        val volume = SoundPrefs.volume(context) / 100f
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed(
            { SoundPlayer.play(context, Uri.parse(uri), volume) { finish() } },
            SoundPrefs.delayMs(context).toLong()
        )
        handler.postDelayed({ finish() }, MAX_ALIVE_MS) // safety before the receiver time limit
    }

    private fun deviceFrom(intent: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private fun hasBluetoothPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val DEBOUNCE_MS = 8_000L
        private const val MAX_ALIVE_MS = 9_000L

        @Volatile private var lastConnect = 0L
        @Volatile private var lastDisconnect = 0L
    }
}

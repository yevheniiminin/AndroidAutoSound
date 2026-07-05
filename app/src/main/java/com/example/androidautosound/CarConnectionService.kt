package com.example.androidautosound

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.connection.CarConnection
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleService

/**
 * Reliable-mode listener. A foreground service that observes [CarConnection]
 * and plays the connect (and optional disconnect) sound on transitions.
 */
class CarConnectionService : LifecycleService() {

    private var lastType = CarConnection.CONNECTION_TYPE_NOT_CONNECTED
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        CarConnection(this).type.observe(this) { type -> onConnectionTypeChanged(type) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    private fun onConnectionTypeChanged(type: Int) {
        Log.d(TAG, "Car connection type=$type (was $lastType)")
        val connectedNow = isConnected(type)
        val wasConnected = isConnected(lastType)
        when {
            connectedNow && !wasConnected -> playConnect()
            !connectedNow && wasConnected -> playDisconnect()
        }
        lastType = type
    }

    private fun isConnected(type: Int) =
        type == CarConnection.CONNECTION_TYPE_PROJECTION ||
            type == CarConnection.CONNECTION_TYPE_NATIVE

    private fun playConnect() = SoundPrefs.connectUri(this)?.let(::playDelayed)

    private fun playDisconnect() {
        if (SoundPrefs.disconnectEnabled(this)) SoundPrefs.disconnectUri(this)?.let(::playDelayed)
    }

    private fun playDelayed(uri: String) {
        val volume = SoundPrefs.volume(this) / 100f
        handler.postDelayed(
            { SoundPlayer.play(this, uri.toUri(), volume) },
            SoundPrefs.delayMs(this).toLong()
        )
    }

    private fun startAsForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_stat_car)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    companion object {
        private const val TAG = "CarConnectionService"
        private const val CHANNEL_ID = "car_connection_listener"
        private const val NOTIF_ID = 1001

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, CarConnectionService::class.java))

        fun stop(context: Context) =
            context.stopService(Intent(context, CarConnectionService::class.java))
    }
}

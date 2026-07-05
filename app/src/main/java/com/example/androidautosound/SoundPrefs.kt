package com.example.androidautosound

import android.content.Context

/** SharedPreferences wrapper for all app settings. */
object SoundPrefs {
    const val MODE_BLUETOOTH = "bluetooth"
    const val MODE_CARCONNECTION = "carconnection"

    private const val PREFS = "aas_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_MODE = "trigger_mode"
    private const val KEY_CONNECT_URI = "connect_uri"
    private const val KEY_CONNECT_NAME = "connect_name"
    private const val KEY_DISCONNECT_ENABLED = "disconnect_enabled"
    private const val KEY_DISCONNECT_URI = "disconnect_uri"
    private const val KEY_DISCONNECT_NAME = "disconnect_name"
    private const val KEY_VOLUME = "volume"
    private const val KEY_DELAY_MS = "delay_ms"
    private const val KEY_CAR_ADDRESS = "car_address"
    private const val KEY_CAR_NAME = "car_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean(KEY_ENABLED, false)
    fun setEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()

    fun triggerMode(context: Context): String =
        prefs(context).getString(KEY_MODE, MODE_BLUETOOTH) ?: MODE_BLUETOOTH
    fun setTriggerMode(context: Context, mode: String) =
        prefs(context).edit().putString(KEY_MODE, mode).apply()

    fun connectUri(context: Context): String? = prefs(context).getString(KEY_CONNECT_URI, null)
    fun connectName(context: Context): String? = prefs(context).getString(KEY_CONNECT_NAME, null)
    fun setConnectSound(context: Context, uri: String, name: String) =
        prefs(context).edit().putString(KEY_CONNECT_URI, uri).putString(KEY_CONNECT_NAME, name).apply()

    fun disconnectEnabled(context: Context) = prefs(context).getBoolean(KEY_DISCONNECT_ENABLED, false)
    fun setDisconnectEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_DISCONNECT_ENABLED, value).apply()

    fun disconnectUri(context: Context): String? = prefs(context).getString(KEY_DISCONNECT_URI, null)
    fun disconnectName(context: Context): String? = prefs(context).getString(KEY_DISCONNECT_NAME, null)
    fun setDisconnectSound(context: Context, uri: String, name: String) =
        prefs(context).edit().putString(KEY_DISCONNECT_URI, uri).putString(KEY_DISCONNECT_NAME, name).apply()

    /** 0..100 */
    fun volume(context: Context) = prefs(context).getInt(KEY_VOLUME, 100)
    fun setVolume(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_VOLUME, value.coerceIn(0, 100)).apply()

    fun delayMs(context: Context) = prefs(context).getInt(KEY_DELAY_MS, 0)
    fun setDelayMs(context: Context, value: Int) =
        prefs(context).edit().putInt(KEY_DELAY_MS, value.coerceAtLeast(0)).apply()

    fun carAddress(context: Context): String? = prefs(context).getString(KEY_CAR_ADDRESS, null)
    fun carName(context: Context): String? = prefs(context).getString(KEY_CAR_NAME, null)
    fun setCar(context: Context, address: String, name: String) =
        prefs(context).edit().putString(KEY_CAR_ADDRESS, address).putString(KEY_CAR_NAME, name).apply()
}

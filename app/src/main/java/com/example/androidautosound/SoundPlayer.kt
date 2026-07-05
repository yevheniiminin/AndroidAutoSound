package com.example.androidautosound

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Plays a one-shot sound on the media stream so that, while Android Auto is
 * connected, the audio is routed to the car speakers.
 *
 * Only one sound plays at a time: a new [play] tears down any in-progress
 * playback first, so tapping the two test buttons (or the same one twice) never
 * overlaps. All calls happen on the main thread.
 *
 * @param volume 0f..1f applied to both channels.
 * @param onComplete invoked once when playback finishes, fails, or is replaced —
 *   used by the Bluetooth receiver to release its keep-alive.
 */
object SoundPlayer {
    private const val TAG = "SoundPlayer"

    private var current: MediaPlayer? = null
    private var stopCurrent: (() -> Unit)? = null

    fun play(context: Context, uri: Uri, volume: Float = 1f, onComplete: () -> Unit = {}) {
        stop()

        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .build()

        val player = MediaPlayer()
        val done = AtomicBoolean(false)
        val teardown: () -> Unit = {
            if (done.compareAndSet(false, true)) {
                try { player.release() } catch (_: Exception) {}
                audioManager.abandonAudioFocusRequest(focusRequest)
                if (current === player) {
                    current = null
                    stopCurrent = null
                }
                onComplete()
            }
        }

        try {
            player.setAudioAttributes(attributes)
            player.setDataSource(appContext, uri)
            player.setOnPreparedListener {
                if (done.get()) return@setOnPreparedListener
                it.setVolume(volume, volume)
                audioManager.requestAudioFocus(focusRequest)
                it.start()
            }
            player.setOnCompletionListener { teardown() }
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                teardown()
                true
            }
            current = player
            stopCurrent = teardown
            player.prepareAsync()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound for uri=$uri", e)
            teardown()
        }
    }

    /** Stops any in-progress playback. Safe to call when nothing is playing. */
    fun stop() {
        stopCurrent?.invoke()
    }
}

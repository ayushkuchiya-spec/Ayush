package com.example.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.sin

object AudioSoundPlayer {
    private const val TAG = "AudioSoundPlayer"
    private var activeTrack: AudioTrack? = null
    private var isPlaying = false

    /**
     * Dynamically synthesizes and plays a custom audio waveform based on the type requested.
     */
    fun startSound(context: Context, soundType: String, volumePerc: Int, vibrationEnabled: Boolean) {
        val appContext = context.applicationContext ?: context
        stopSound(appContext)
        isPlaying = true

        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val targetVolume = (maxVolume * (volumePerc / 100.0f)).toInt().coerceIn(1, maxVolume)
        am.setStreamVolume(AudioManager.STREAM_ALARM, targetVolume, 0)

        // Generate synthetic audio wave based on selected sound
        val sampleRate = 44100
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioTrack(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
                AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .build(),
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
        } else {
            @Suppress("DEPRECATION")
            AudioTrack(
                AudioManager.STREAM_ALARM,
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
                AudioTrack.MODE_STREAM
            )
        }

        activeTrack = audioTrack
        audioTrack.play()

        // Generate waveforms in a helper thread
        Thread {
            try {
                val shortBuffer = ShortArray(1024)
                var phase = 0.0

                while (isPlaying) {
                    val track = activeTrack ?: break
                    for (i in shortBuffer.indices) {
                        val frequency = when (soundType.lowercase()) {
                            "chime" -> {
                                // Melodic dual tones alternating
                                val t = phase / sampleRate
                                val core = if ((t % 1.0) < 0.5) 523.25 else 659.25 // C5 to E5
                                core
                            }
                            "gentle" -> {
                                // Relaxed slow sine wave
                                261.63 // C4
                            }
                            "loud" -> {
                                // Piercing alarm square frequency
                                val t = phase / sampleRate
                                val isBeepOff = (t % 0.4) < 0.2
                                if (isBeepOff) 0.0 else 880.0 // Pitchy High A5
                            }
                            else -> { // "beep"
                                // Standard quick intermittent beep
                                val t = phase / sampleRate
                                val isBeepOff = (t % 0.3) < 0.15
                                if (isBeepOff) 0.0 else 660.0 // E5 key
                            }
                        }

                        val amplitude = if (frequency == 0.0) 0.0 else 24000.0
                        val angle = 2.0 * Math.PI * frequency * (phase / sampleRate)
                        shortBuffer[i] = (sin(angle) * amplitude).toInt().toShort()
                        phase++
                    }
                    track.write(shortBuffer, 0, shortBuffer.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio synthesis thread crashed", e)
            }
        }.start()

        // Handle Vibration
        if (vibrationEnabled) {
            triggerVibration(appContext)
        }
    }

    fun stopSound(context: Context) {
        val appContext = context.applicationContext ?: context
        isPlaying = false
        try {
            activeTrack?.stop()
            activeTrack?.release()
        } catch (e: Exception) {
            // Silently swallow
        }
        activeTrack = null

        cancelVibration(appContext)
    }

    private fun triggerVibration(context: Context) {
        val appContext = context.applicationContext ?: context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let { v ->
            if (v.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Repeat vibration pattern: Wait 0, Vibe 500ms, Wait 300ms, Vibe 500ms
                    v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(longArrayOf(0, 500, 300, 500), 0)
                }
            }
        }
    }

    private fun cancelVibration(context: Context) {
        val appContext = context.applicationContext ?: context
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.cancel()
    }
}

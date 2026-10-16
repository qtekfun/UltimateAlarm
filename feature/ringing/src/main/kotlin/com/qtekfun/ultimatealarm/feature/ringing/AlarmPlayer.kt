package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays the alarm on the alarm stream (USAGE_ALARM), looping. Something always sounds (CLAUDE.md rule 1): if the
 * system alarm tone cannot be played or does not start within [START_TIMEOUT_MS] (e.g. media storage not available
 * before the first unlock), a generated beep takes over. The bundled pack joins the chain in Phase 5.
 */
internal class AlarmPlayer(private val context: Context, initialGain: Float, private val onSource: (String) -> Unit) {
    private var gain = initialGain
    private val handler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var beep: AudioTrack? = null
    private var started = false
    private var stopped = false

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start() {
        handler.postDelayed({ if (!started) fallback("system tone did not start in time") }, START_TIMEOUT_MS)
        runCatching {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: error("no system alarm tone")
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, uri)
                isLooping = true
                setOnPreparedListener { player ->
                    if (!stopped && !started) {
                        player.setVolume(gain, gain)
                        player.start()
                        started = true
                        onSource("system:$uri")
                    }
                }
                setOnErrorListener { _, what, extra ->
                    fallback("media error what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        }.onFailure { e ->
            // Whatever goes wrong with the tone (missing file, storage locked, codec), the beep must still play.
            fallback("system tone failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun fallback(reason: String) {
        if (stopped || beep != null) return
        mediaPlayer?.release()
        mediaPlayer = null
        started = true
        beep = runCatching {
            generatedBeep().also {
                it.setVolume(gain)
                it.play()
            }
        }
            .onSuccess { onSource("beep ($reason)") }
            .onFailure { onSource("NOTHING: beep failed ${it.javaClass.simpleName}: ${it.message} ($reason)") }
            .getOrNull()
    }

    /** Software volume 0..1 on top of the alarm stream (ramp, max volume). */
    fun setGain(value: Float) {
        gain = value.coerceIn(0f, 1f)
        mediaPlayer?.runCatching { setVolume(gain, gain) }
        beep?.runCatching { setVolume(gain) }
    }

    fun stop() {
        stopped = true
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.runCatching { stop() }
        mediaPlayer?.release()
        mediaPlayer = null
        beep?.runCatching { stop() }
        beep?.release()
        beep = null
    }

    /** One second of a soft-edged 880 Hz tone followed by silence, looped forever. */
    private fun generatedBeep(): AudioTrack {
        val frames = SAMPLE_RATE
        val toneFrames = SAMPLE_RATE / 2
        val fade = SAMPLE_RATE / 100
        val samples = ShortArray(frames) { i ->
            if (i >= toneFrames) {
                0
            } else {
                val envelope = min(1.0, min(i, toneFrames - i).toDouble() / fade)
                (sin(2 * PI * BEEP_HZ * i / SAMPLE_RATE) * envelope * Short.MAX_VALUE * BEEP_LEVEL).toInt().toShort()
            }
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(frames * 2)
            .build()
        track.write(samples, 0, samples.size)
        track.setLoopPoints(0, frames, -1)
        return track
    }

    private companion object {
        const val START_TIMEOUT_MS = 3_000L
        const val SAMPLE_RATE = 44_100
        const val BEEP_HZ = 880.0
        const val BEEP_LEVEL = 0.8
    }
}

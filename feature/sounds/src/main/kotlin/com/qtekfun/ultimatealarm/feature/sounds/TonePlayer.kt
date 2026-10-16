package com.qtekfun.ultimatealarm.feature.sounds

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Plays a tone on the alarm stream (USAGE_ALARM), looping, trying [chain] in order (§5.3): the chosen sound, the pack
 * default, the system alarm tone, and finally a generated beep. Each source gets [START_TIMEOUT_MS] to start; a missing
 * file, a lost permission or storage locked before the first unlock just moves on. Something always sounds
 * (CLAUDE.md rule 1).
 */
class TonePlayer(
    private val context: Context,
    private val chain: List<SoundRef>,
    initialGain: Float,
    private val looping: Boolean = true,
    private val onSource: (String) -> Unit = {}
) {
    private val handler = Handler(Looper.getMainLooper())
    private var gain = initialGain
    private var mediaPlayer: MediaPlayer? = null
    private var beep: AudioTrack? = null
    private var index = -1
    private var stopped = false
    private val failures = mutableListOf<String>()

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start() = next(null)

    private fun next(failure: String?) {
        if (stopped) return
        failure?.let { failures += "${chain.getOrNull(index)?.encode()}: $it" }
        handler.removeCallbacksAndMessages(null)
        mediaPlayer?.release()
        mediaPlayer = null
        index++
        val source = chain.getOrNull(index)
        if (source == null) {
            playBeep()
            return
        }
        val attempt = index
        handler.postDelayed({ if (attempt == index && mediaPlayer?.isPlaying != true) next("did not start in time") }, START_TIMEOUT_MS)
        runCatching {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSourceFor(this, source)
                isLooping = looping
                setOnPreparedListener { player ->
                    if (stopped || attempt != index) return@setOnPreparedListener
                    player.setVolume(gain, gain)
                    player.start()
                    handler.removeCallbacksAndMessages(null)
                    onSource(describe(source))
                }
                setOnErrorListener { _, what, extra ->
                    if (attempt == index) next("media error what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        }.onFailure { next("${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun setDataSourceFor(player: MediaPlayer, source: SoundRef) {
        when (source) {
            is SoundRef.Bundled -> {
                val asset = SoundCatalog.byId(source.id) ?: error("unknown bundled sound ${source.id}")
                context.assets.openFd(asset.assetPath).use { player.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
            }
            is SoundRef.System -> {
                val uri = if (source.uri == SoundCatalog.SYSTEM_DEFAULT) {
                    RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: error("no system alarm tone")
                } else {
                    source.uri.toUri()
                }
                player.setDataSource(context, uri)
            }
            is SoundRef.File -> player.setDataSource(context, source.uri.toUri())
        }
    }

    private fun describe(source: SoundRef): String {
        val prefix = if (index == 0) "" else "fallback "
        val why = if (failures.isEmpty()) "" else " after ${failures.joinToString("; ")}"
        return prefix + source.encode() + why
    }

    private fun playBeep() {
        if (stopped || beep != null) return
        beep = runCatching {
            generatedBeep().also {
                it.setVolume(gain)
                it.play()
            }
        }
            .onSuccess { onSource("beep after ${failures.joinToString("; ")}") }
            .onFailure { onSource("NOTHING: beep failed ${it.javaClass.simpleName}: ${it.message}; ${failures.joinToString("; ")}") }
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

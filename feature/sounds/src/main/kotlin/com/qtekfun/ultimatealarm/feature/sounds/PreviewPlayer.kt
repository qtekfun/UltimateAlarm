package com.qtekfun.ultimatealarm.feature.sounds

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.qtekfun.ultimatealarm.core.data.RingPolicy

/** RF-S4: previews a tone with the alarm's ramp and maximum volume, for up to [MAX_PREVIEW_MS]. */
class PreviewPlayer(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var player: TonePlayer? = null

    fun play(ref: SoundRef, rampSeconds: Int, maxVolume: Int) {
        stop()
        val started = System.currentTimeMillis()
        val tone = TonePlayer(context, listOf(ref), RingPolicy.gain(0, rampSeconds, maxVolume))
        player = tone
        tone.start()
        val tick = object : Runnable {
            override fun run() {
                val elapsed = System.currentTimeMillis() - started
                if (elapsed >= MAX_PREVIEW_MS) {
                    stop()
                    return
                }
                tone.setGain(RingPolicy.gain(elapsed, rampSeconds, maxVolume))
                handler.postDelayed(this, TICK_MS)
            }
        }
        tick.run()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        player?.stop()
        player = null
    }

    private companion object {
        const val MAX_PREVIEW_MS = 30_000L
        const val TICK_MS = 250L
    }
}

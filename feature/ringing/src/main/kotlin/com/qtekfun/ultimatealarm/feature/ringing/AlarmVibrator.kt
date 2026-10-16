package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Repeating vibration flagged as an alarm, so Do Not Disturb treats it like the alarm sound. */
internal class AlarmVibrator(context: Context) {
    private val vibrator: Vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    /** [continuous]: the backup alarm's insistent pattern (RF-R4). */
    fun start(continuous: Boolean = false) {
        if (!vibrator.hasVibrator()) return
        vibrator.cancel()
        val effect = VibrationEffect.createWaveform(if (continuous) CONTINUOUS else PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
    }

    fun stop() {
        vibrator.cancel()
    }

    private companion object {
        val PATTERN = longArrayOf(0, 800, 600)
        val CONTINUOUS = longArrayOf(0, 1_000, 150)
    }
}

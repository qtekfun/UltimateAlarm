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

    fun start() {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(PATTERN, 0)
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
    }
}

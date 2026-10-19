package com.qtekfun.ultimatealarm.feature.ringing

import android.content.Context
import android.media.AudioManager
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler

/**
 * RF-R7 when the ringing screen is not in front: screen off, a heads-up instead of the full screen, another app open.
 * While the alarm rings, an active media session with a volume of its own receives the volume keys (also with the screen
 * off), so they snooze or dismiss as chosen and never lower the alarm. With focus, the ringing screen handles them itself.
 */
internal class VolumeKeys(context: Context, handler: Handler, private val onPress: () -> Unit) {
    private val session = MediaSession(context, TAG).apply {
        // Without a callback the session drops volume adjustments; they arrive on [handler].
        setCallback(object : MediaSession.Callback() {}, handler)
        setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, MAX_VOLUME, MAX_VOLUME) {
            override fun onAdjustVolume(direction: Int) {
                if (direction != AudioManager.ADJUST_SAME) onPress()
            }
        })
        // Only a playing session is given the volume keys.
        setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).build())
        isActive = true
    }

    fun release() {
        session.isActive = false
        session.release()
    }

    private companion object {
        const val TAG = "UltimateAlarm:ringing"
        const val MAX_VOLUME = 10
    }
}

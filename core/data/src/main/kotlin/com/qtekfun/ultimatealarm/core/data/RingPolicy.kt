package com.qtekfun.ultimatealarm.core.data

import kotlin.math.min

/** How the current ring sounds: the alarm's own tone with its ramp, or the backup tone at full volume (RF-R4). */
enum class RingMode { NORMAL, BACKUP }

/** A ringing alarm, persisted so the ring survives the death of the process (02-diseno §2 `RingState`, §5.4). */
data class RingState(
    val alarmId: Long,
    /** When the current ring started (each snooze starts a new ring). */
    val firedAt: Long,
    /** Snoozes used in this session, reset when the alarm is dismissed. */
    val snoozeCount: Int,
    val backupFired: Boolean
)

/** The rules of a ringing alarm, without Android, so they can be tested (RF-R2, RF-R3, RF-R4, RF-R8). */
object RingPolicy {
    /**
     * Gain 0..1 applied to the tone [elapsedMs] after the ring started: from a soft but clearly audible start (a tenth of
     * [maxVolume] %, -20 dB) up to [maxVolume] % in [rampSeconds], linear. A quadratic curve from 2 % left a 30 s ramp
     * almost inaudible for its first half, so people slept through it. Backup rings skip the ramp and use full volume.
     */
    fun gain(elapsedMs: Long, rampSeconds: Int, maxVolume: Int, mode: RingMode = RingMode.NORMAL): Float {
        if (mode == RingMode.BACKUP) return 1f
        val target = maxVolume.coerceIn(1, PERCENT) / PERCENT.toFloat()
        if (rampSeconds <= 0) return target
        val progress = min(1f, elapsedMs.coerceAtLeast(0) / (rampSeconds * MS_PER_SECOND.toFloat()))
        return target * MIN_RAMP_GAIN.coerceAtMost(target).let { floor -> floor + (1f - floor) * progress }
    }

    /** Whether one more snooze is allowed after [snoozeCount] snoozes (RF-R3). */
    fun canSnooze(snoozeCount: Int, maxSnoozes: Int): Boolean = maxSnoozes == Alarm.UNLIMITED_SNOOZES || snoozeCount < maxSnoozes

    /**
     * Mode of a ring that starts now. The ring that follows the last allowed snooze goes straight to the backup tone:
     * the user has used up every snooze (RF-R4, docs/spec/DECISIONS.md D-13). Only when the alarm has a backup.
     */
    fun modeForRing(snoozeCount: Int, alarm: Alarm): RingMode =
        if (alarm.backupAfterMinutes != null && snoozeCount > 0 && !canSnooze(snoozeCount, alarm.maxSnoozes)) RingMode.BACKUP else RingMode.NORMAL

    /** When the backup tone takes over a normal ring without interaction, or `null` without backup (RF-R4). */
    fun backupAt(firedAt: Long, alarm: Alarm): Long? = alarm.backupAfterMinutes?.let { firedAt + it * MS_PER_MINUTE }

    /**
     * When a ring silences itself (RF-R8). With a backup it never does by default; without one, after
     * [silenceAfterMinutes] (global setting, `null` = never). [HARD_LIMIT_MINUTES] caps a ring nobody answers so a phone
     * left at home does not ring for days.
     */
    fun silenceAt(firedAt: Long, alarm: Alarm, silenceAfterMinutes: Int?): Long {
        val minutes = when {
            silenceAfterMinutes != null -> silenceAfterMinutes
            alarm.backupAfterMinutes != null -> HARD_LIMIT_MINUTES
            else -> DEFAULT_SILENCE_MINUTES
        }
        return firedAt + min(minutes, HARD_LIMIT_MINUTES) * MS_PER_MINUTE
    }

    /**
     * What a volume key does while ringing (RF-R7): the chosen [action], unless the alarm cannot be snoozed any more or a
     * challenge guards the dismissal (RF-R5). `null` = nothing, but the key is still swallowed so it cannot lower the alarm.
     */
    fun volumeKeyCommand(action: VolumeButtonAction, canSnooze: Boolean, hasChallenge: Boolean): VolumeButtonAction? = when (action) {
        VolumeButtonAction.SNOOZE -> action.takeIf { canSnooze }
        VolumeButtonAction.DISMISS -> action.takeIf { !hasChallenge }
        VolumeButtonAction.NOTHING -> null
    }

    /** A persisted ring older than this is not resumed after a reboot or process death: it would only be noise. */
    fun isResumable(state: RingState, now: Long): Boolean = now - state.firedAt in 0 until HARD_LIMIT_MINUTES * MS_PER_MINUTE

    const val DEFAULT_SILENCE_MINUTES = 30
    const val HARD_LIMIT_MINUTES = 60
    private const val PERCENT = 100
    private const val MS_PER_SECOND = 1_000
    private const val MS_PER_MINUTE = 60_000L

    /** Start of the ramp, relative to the maximum: soft, but enough to wake someone up from the first seconds. */
    private const val MIN_RAMP_GAIN = 0.1f
}

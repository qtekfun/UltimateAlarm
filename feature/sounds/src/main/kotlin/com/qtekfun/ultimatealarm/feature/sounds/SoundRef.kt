package com.qtekfun.ultimatealarm.feature.sounds

/** A sound an alarm can play (02-diseno §7). Stored as text in the alarm row: `bundled:id`, `system:uri`, `file:uri`. */
sealed interface SoundRef {
    data class Bundled(val id: String) : SoundRef

    /** A system tone (RingtoneManager, alarm type) (RF-S2). */
    data class System(val uri: String) : SoundRef

    /** A file the user picked with the system picker, permission persisted (RF-S3). */
    data class File(val uri: String) : SoundRef

    fun encode(): String = when (this) {
        is Bundled -> "$BUNDLED$id"
        is System -> "$SYSTEM$uri"
        is File -> "$FILE$uri"
    }

    companion object {
        private const val BUNDLED = "bundled:"
        private const val SYSTEM = "system:"
        private const val FILE = "file:"

        /** `null` or unknown text → `null` (the default tone). */
        fun decode(text: String?): SoundRef? = when {
            text == null -> null
            text.startsWith(BUNDLED) -> text.removePrefix(BUNDLED).takeIf { it.isNotBlank() }?.let(::Bundled)
            text.startsWith(SYSTEM) -> text.removePrefix(SYSTEM).takeIf { it.isNotBlank() }?.let(::System)
            text.startsWith(FILE) -> text.removePrefix(FILE).takeIf { it.isNotBlank() }?.let(::File)
            else -> null
        }
    }
}

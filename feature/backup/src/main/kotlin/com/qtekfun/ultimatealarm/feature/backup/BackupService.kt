package com.qtekfun.ultimatealarm.feature.backup

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.qtekfun.ultimatealarm.core.data.BackupStore
import com.qtekfun.ultimatealarm.core.data.HolidaySettings
import com.qtekfun.ultimatealarm.core.data.RestoreMode
import com.qtekfun.ultimatealarm.core.data.RingStore
import com.qtekfun.ultimatealarm.core.data.VolumeButtonAction
import java.io.File
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * RF-B1–B5: builds and restores backups (file and Nextcloud). Restoring validates the whole file first, then replaces
 * or merges in one transaction; the store's listener re-schedules every alarm afterwards.
 */
class BackupService(private val context: Context, private val store: BackupStore, private val appVersion: String) {
    private val nextcloud by lazy { NextcloudSettings(context) }

    /** [passphrase] encrypts the Nextcloud account into the file (RF-B2); without it the account is left out. */
    suspend fun export(includeAudio: Boolean, passphrase: CharArray?): ByteArray = withContext(Dispatchers.IO) {
        val content = store.snapshot()
        val audioRefs = mutableMapOf<String, String>()
        val audio = mutableMapOf<String, ByteArray>()
        if (includeAudio) {
            content.alarms.flatMap { listOfNotNull(it.sound, it.backupSound) }.filter { it.startsWith(FILE_PREFIX) }.distinct().forEachIndexed { i, ref ->
                val uri = ref.removePrefix(FILE_PREFIX).toUri()
                val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull() ?: return@forEachIndexed
                val name = "$i-" + BackupFormat.safeName(uri.lastPathSegment ?: "audio")
                audioRefs[ref] = name
                audio[name] = bytes
            }
        }
        val credentials = passphrase?.takeIf { it.isNotEmpty() && nextcloud.account.configured }?.let {
            BackupCrypto.encrypt(nextcloud.accountJson().toByteArray(Charsets.UTF_8), it)
        }
        val file = BackupFormat.toFile(content, System.currentTimeMillis(), appVersion, readSettings(), credentials, audioRefs)
        BackupFormat.pack(file, audio)
    }

    fun parse(bytes: ByteArray): ParsedBackup = BackupFormat.unpack(bytes)

    /** Applies a parsed backup. [passphrase] opens the Nextcloud account when the file carries one; wrong → [BackupException]. */
    suspend fun restore(parsed: ParsedBackup, mode: RestoreMode, passphrase: CharArray?) = withContext(Dispatchers.IO) {
        val key = passphrase?.takeIf { it.isNotEmpty() }
        val account = BackupFormat.credentials(parsed.file)?.takeIf { key != null }?.let {
            String(BackupCrypto.decrypt(it, key!!), Charsets.UTF_8)
        }
        val rewritten = parsed.file.audioRefs.mapNotNull { (ref, name) ->
            val bytes = parsed.audio[name] ?: return@mapNotNull null
            val target = File(File(context.filesDir, AUDIO_DIR).apply { mkdirs() }, BackupFormat.safeName(name))
            target.writeBytes(bytes)
            ref to FILE_PREFIX + Uri.fromFile(target)
        }.toMap()
        val content = parsed.content.copy(
            alarms = parsed.content.alarms.map { a -> a.copy(sound = rewritten[a.sound] ?: a.sound, backupSound = rewritten[a.backupSound] ?: a.backupSound) }
        )
        store.restore(content, mode)
        if (mode == RestoreMode.REPLACE) applySettings(parsed.file.settings)
        account?.let(nextcloud::restoreAccount)
    }

    /** RF-B3: uploads a backup (no audio, no credentials) and keeps the newest [NextcloudSettings.keep] copies. */
    suspend fun backupToNextcloud(): String = withContext(Dispatchers.IO) {
        val account = nextcloud.account
        check(account.configured) { "Nextcloud is not configured" }
        val dav = dav(account)
        val name = "$FILE_PREFIX_NAME${LocalDateTime.now().format(STAMP)}.zip"
        dav.mkcol(account.folder)
        dav.put("${account.folder}/$name", export(includeAudio = false, passphrase = null))
        listRemote(dav, account).drop(nextcloud.keep).forEach { dav.delete("${account.folder}/${it.name}") }
        nextcloud.lastBackup = System.currentTimeMillis()
        nextcloud.lastError = null
        name
    }

    /** RF-B4: backups on the server, newest first. */
    suspend fun listNextcloud(): List<DavFile> = withContext(Dispatchers.IO) {
        val account = nextcloud.account
        listRemote(dav(account), account)
    }

    suspend fun downloadNextcloud(name: String): ParsedBackup = withContext(Dispatchers.IO) {
        val account = nextcloud.account
        parse(dav(account).get("${account.folder}/$name"))
    }

    /** Checks the account and the folder, creating it if needed. */
    suspend fun testNextcloud(account: NextcloudAccount) = withContext(Dispatchers.IO) {
        val dav = dav(account)
        dav.mkcol(account.folder)
        dav.list(account.folder)
    }

    private suspend fun listRemote(dav: WebDav, account: NextcloudAccount) =
        dav.list(account.folder).filter { it.name.startsWith(FILE_PREFIX_NAME) && it.name.endsWith(".zip") }.sortedByDescending { it.name }

    private fun dav(account: NextcloudAccount): WebDav {
        val root = WebDav.nextcloudRoot(account.server, account.username) ?: throw DavException(DavError.INSECURE_URL, "Invalid server address")
        return WebDav(root, account.username, account.password)
    }

    /** Settings that travel in the file: holidays and ringing. */
    private fun readSettings(): Map<String, String> {
        val holidays = HolidaySettings(context)
        val ring = RingStore(context)
        return buildMap {
            put("holidays.automatic", holidays.automatic.toString())
            put("holidays.country", holidays.country)
            holidays.subdivision?.let { put("holidays.subdivision", it) }
            holidays.municipalityPath?.let { put("holidays.municipalityPath", it) }
            holidays.municipalityName?.let { put("holidays.municipalityName", it) }
            holidays.icsUrl?.let { put("holidays.icsUrl", it) }
            put("holidays.eveEnabled", holidays.eveEnabled.toString())
            put("holidays.eveTime", holidays.eveTime.toString())
            put("ring.volumeButtons", ring.volumeButtons.name)
            ring.silenceAfterMinutes?.let { put("ring.silenceAfterMinutes", it.toString()) }
            ring.backupSound?.let { put("ring.backupSound", it) }
            put("ring.bedtimeEnabled", ring.bedtimeEnabled.toString())
            put("ring.bedtimeHours", ring.bedtimeHours.toString())
        }
    }

    private fun applySettings(settings: Map<String, String>) {
        val holidays = HolidaySettings(context)
        val ring = RingStore(context)
        settings["holidays.automatic"]?.toBooleanStrictOrNull()?.let { holidays.automatic = it }
        settings["holidays.country"]?.let { holidays.country = it }
        holidays.subdivision = settings["holidays.subdivision"]
        holidays.municipalityPath = settings["holidays.municipalityPath"]
        holidays.municipalityName = settings["holidays.municipalityName"]
        holidays.icsUrl = settings["holidays.icsUrl"]
        settings["holidays.eveEnabled"]?.toBooleanStrictOrNull()?.let { holidays.eveEnabled = it }
        settings["holidays.eveTime"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() }?.let { holidays.eveTime = it }
        settings["ring.volumeButtons"]?.let { name -> VolumeButtonAction.entries.firstOrNull { it.name == name } }?.let { ring.volumeButtons = it }
        ring.silenceAfterMinutes = settings["ring.silenceAfterMinutes"]?.toIntOrNull()
        ring.backupSound = settings["ring.backupSound"]
        settings["ring.bedtimeEnabled"]?.toBooleanStrictOrNull()?.let { ring.bedtimeEnabled = it }
        settings["ring.bedtimeHours"]?.toIntOrNull()?.let { ring.bedtimeHours = it }
    }

    companion object {
        const val FILE_PREFIX_NAME = "ultimatealarm-"
        private const val FILE_PREFIX = "file:"
        private const val AUDIO_DIR = "audio"
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun fileName(): String = "$FILE_PREFIX_NAME${LocalDateTime.now().format(STAMP)}.zip"
    }
}

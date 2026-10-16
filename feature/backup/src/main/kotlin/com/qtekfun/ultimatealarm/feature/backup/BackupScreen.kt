package com.qtekfun.ultimatealarm.feature.backup

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qtekfun.ultimatealarm.core.data.RestoreMode
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Empty text means "no passphrase". */
private fun String.asSecret(): CharArray? = if (isEmpty()) null else toCharArray()

/** What the screen is doing, for the status line and the preview dialog. */
data class BackupUiState(val busy: Boolean = false, val message: String? = null, val preview: ParsedBackup? = null, val remote: List<DavFile>? = null)

class BackupViewModel(application: Application) : AndroidViewModel(application) {
    private val service: BackupService = BackupGraph.service?.invoke(application) ?: error("BackupGraph not set")
    val settings = NextcloudSettings(application)
    private val mutable = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = mutable

    private fun run(block: suspend () -> String?) {
        mutable.value = mutable.value.copy(busy = true, message = null)
        viewModelScope.launch {
            val message = runCatching { block() }.getOrElse { describe(it) }
            mutable.value = mutable.value.copy(busy = false, message = message)
        }
    }

    fun export(target: android.net.Uri, includeAudio: Boolean, passphrase: String) = run {
        val bytes = service.export(includeAudio, passphrase.asSecret())
        getApplication<Application>().contentResolver.openOutputStream(target)?.use { it.write(bytes) }
        getApplication<Application>().getString(R.string.backup_exported)
    }

    fun open(source: android.net.Uri) = run {
        val bytes = getApplication<Application>().contentResolver.openInputStream(source)?.use { it.readBytes() } ?: error("cannot read")
        mutable.value = mutable.value.copy(preview = service.parse(bytes))
        null
    }

    fun restore(mode: RestoreMode, passphrase: String) {
        val parsed = mutable.value.preview ?: return
        mutable.value = mutable.value.copy(preview = null)
        run {
            service.restore(parsed, mode, passphrase.asSecret())
            getApplication<Application>().getString(R.string.backup_restored, parsed.alarmCount)
        }
    }

    fun dismissPreview() {
        mutable.value = mutable.value.copy(preview = null, remote = null)
    }

    fun saveAccount(account: NextcloudAccount, auto: Boolean, keep: Int) {
        settings.account = account
        settings.auto = auto
        settings.keep = keep
        if (auto) {
            AutoBackup.ensureDaily(getApplication())
            AutoBackup.afterChange(getApplication())
        }
    }

    fun test(account: NextcloudAccount) = run {
        service.testNextcloud(account)
        getApplication<Application>().getString(R.string.backup_connection_ok)
    }

    fun backupNow() = run { getApplication<Application>().getString(R.string.backup_uploaded, service.backupToNextcloud()) }

    fun listRemote() = run {
        mutable.value = mutable.value.copy(remote = service.listNextcloud())
        null
    }

    fun openRemote(name: String) = run {
        mutable.value = mutable.value.copy(remote = null, preview = service.downloadNextcloud(name))
        null
    }

    private fun describe(error: Throwable): String {
        val app = getApplication<Application>()
        return when (error) {
            is BackupException -> app.getString(
                if (error.failure ==
                    BackupFailure.WRONG_PASSWORD_OR_ALTERED
                ) {
                    R.string.backup_wrong_password
                } else {
                    R.string.backup_invalid
                }
            )
            is DavException -> app.getString(
                when (error.error) {
                    DavError.AUTH -> R.string.backup_error_auth
                    DavError.INSECURE_URL -> R.string.backup_error_https
                    DavError.TLS -> R.string.backup_error_tls
                    DavError.NETWORK -> R.string.backup_error_network
                    else -> R.string.backup_error_server
                }
            )
            is IllegalArgumentException -> app.getString(R.string.backup_invalid)
            else -> error.message ?: error.javaClass.simpleName
        }
    }
}

/** Settings → Backup (RF-B1–B5). */
@Composable
fun BackupScreen(modifier: Modifier = Modifier, viewModel: BackupViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var includeAudio by rememberSaveable { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { viewModel.export(it, includeAudio, exportPassword) }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::open) }

    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text(stringResource(R.string.backup_file), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.backup_file_help), style = MaterialTheme.typography.bodySmall)
        SwitchRow(stringResource(R.string.backup_include_audio), includeAudio) { includeAudio = it }
        PasswordField(exportPassword, stringResource(R.string.backup_export_password)) { exportPassword = it }
        Text(stringResource(R.string.backup_export_password_help), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { create.launch(BackupService.fileName()) }, enabled = !state.busy) { Text(stringResource(R.string.backup_export)) }
            OutlinedButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }, enabled = !state.busy) {
                Text(stringResource(R.string.backup_import))
            }
        }
        HorizontalDivider()
        NextcloudSection(viewModel, state.busy)
    }

    state.preview?.let { RestoreDialog(it, onRestore = viewModel::restore, onDismiss = viewModel::dismissPreview) }
    state.remote?.let { files ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text(stringResource(R.string.backup_remote_list)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (files.isEmpty()) Text(stringResource(R.string.backup_remote_empty))
                    files.forEach { file ->
                        Text(
                            file.modified?.let { DateFormat.getDateTimeInstance().format(Date(it.toEpochMilli())) } ?: file.name,
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { viewModel.openRemote(file.name) }.padding(vertical = 12.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = viewModel::dismissPreview) { Text(stringResource(R.string.backup_cancel)) } }
        )
    }
}

@Composable
private fun NextcloudSection(viewModel: BackupViewModel, busy: Boolean) {
    val saved = remember { viewModel.settings.account }
    var server by rememberSaveable { mutableStateOf(saved.server) }
    var user by rememberSaveable { mutableStateOf(saved.username) }
    var password by rememberSaveable { mutableStateOf(saved.password) }
    var folder by rememberSaveable { mutableStateOf(saved.folder) }
    var auto by rememberSaveable { mutableStateOf(viewModel.settings.auto) }
    var keep by rememberSaveable { mutableStateOf(viewModel.settings.keep) }
    val account = NextcloudAccount(server, user, password, folder.ifBlank { NextcloudAccount.DEFAULT_FOLDER })
    val save = { viewModel.saveAccount(account, auto, keep) }

    Text(stringResource(R.string.backup_nextcloud), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.backup_nextcloud_help), style = MaterialTheme.typography.bodySmall)
    Field(server, stringResource(R.string.backup_server), KeyboardType.Uri) { server = it }
    Field(user, stringResource(R.string.backup_user)) { user = it }
    PasswordField(password, stringResource(R.string.backup_app_password)) { password = it }
    Field(folder, stringResource(R.string.backup_folder)) { folder = it }
    Text(stringResource(R.string.backup_keep, keep))
    Slider(value = keep.toFloat(), onValueChange = { keep = it.roundToInt() }, valueRange = 1f..30f, steps = 28)
    SwitchRow(stringResource(R.string.backup_auto), auto) { auto = it }
    viewModel.settings.lastError?.let {
        Text(stringResource(R.string.backup_last_error, it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    viewModel.settings.lastBackup.takeIf { it > 0 }?.let {
        Text(stringResource(R.string.backup_last, DateFormat.getDateTimeInstance().format(Date(it))), style = MaterialTheme.typography.bodySmall)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = save, enabled = !busy) { Text(stringResource(R.string.backup_save)) }
        OutlinedButton(onClick = { viewModel.test(account) }, enabled = !busy && account.configured) { Text(stringResource(R.string.backup_test)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            save()
            viewModel.backupNow()
        }, enabled = !busy && account.configured) { Text(stringResource(R.string.backup_now)) }
        OutlinedButton(onClick = {
            save()
            viewModel.listRemote()
        }, enabled = !busy && account.configured) { Text(stringResource(R.string.backup_restore_remote)) }
    }
}

@Composable
private fun RestoreDialog(parsed: ParsedBackup, onRestore: (RestoreMode, String) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(RestoreMode.REPLACE) }
    var password by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.backup_preview, DateFormat.getDateTimeInstance().format(Date(parsed.file.createdAt)), parsed.alarmCount))
                listOf(RestoreMode.REPLACE to R.string.backup_replace, RestoreMode.MERGE to R.string.backup_merge).forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = mode == value, onClick = { mode = value }, role = Role.RadioButton),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = mode == value, onClick = null)
                        Text(stringResource(label), Modifier.padding(start = 8.dp))
                    }
                }
                if (parsed.file.encryptedCredentials != null) {
                    PasswordField(password, stringResource(R.string.backup_import_password)) { password = it }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onRestore(mode, password) }) { Text(stringResource(R.string.backup_restore)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.backup_cancel)) } }
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Field(value: String, label: String, type: KeyboardType = KeyboardType.Text, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PasswordField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
}

package com.qtekfun.ultimatealarm.settings

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qtekfun.ultimatealarm.R
import com.qtekfun.ultimatealarm.core.data.HolidayEntry
import com.qtekfun.ultimatealarm.core.data.HolidaySourceType
import com.qtekfun.ultimatealarm.feature.holidays.LocalHolidays
import com.qtekfun.ultimatealarm.feature.holidays.OfficialCalendars
import java.text.DateFormat as JavaDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

/** Settings → Holidays (D-14): no screen of its own in the app, everything about holidays lives here. */
@Composable
fun HolidaySettingsScreen(modifier: Modifier = Modifier, viewModel: HolidaySettingsViewModel = hiltViewModel()) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val year by viewModel.year.collectAsStateWithLifecycle()
    Column(modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Heading(stringResource(R.string.holidays_where))
        SwitchRow(stringResource(R.string.holidays_automatic), config.automatic, viewModel::setAutomatic)
        if (config.automatic) {
            CountryPicker(config.country, viewModel::setCountry)
            RegionPicker(config.country, config.subdivision, viewModel::setSubdivision)
        }
        if (config.country == "ES" && config.subdivision != null) MunicipalityPicker(config, viewModel)
        Text(stringResource(R.string.holidays_source_note), style = MaterialTheme.typography.bodySmall)
        IcsField(config.icsUrl, viewModel::setIcsUrl)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = viewModel::refresh, enabled = !config.refreshing) { Text(stringResource(R.string.holidays_refresh)) }
            Text(refreshStatus(config), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }

        HorizontalDivider()
        Heading(stringResource(R.string.holidays_eve))
        SwitchRow(stringResource(R.string.holidays_eve_enabled), config.eveEnabled) { viewModel.setEve(it) }
        if (config.eveEnabled) EveTime(config.eveTime) { viewModel.setEve(true, it) }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.year.value = year - 1 }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.holidays_previous_year))
            }
            Text(stringResource(R.string.holidays_list, year), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { viewModel.year.value = year + 1 }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = stringResource(R.string.holidays_next_year))
            }
        }
        AddHoliday(viewModel::addManual)
        if (entries.isEmpty()) Text(stringResource(R.string.holidays_none), style = MaterialTheme.typography.bodyMedium)
        entries.forEach { entry ->
            HolidayRow(entry, onCancelled = { viewModel.setCancelled(entry.date, it) }, onDelete = entry.manualId?.let { id -> { viewModel.deleteManual(id) } })
        }
    }
}

@Composable
private fun Heading(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CountryPicker(country: String, onSelect: (String) -> Unit) {
    Dropdown(
        label = stringResource(R.string.holidays_country),
        value = Locale("", country).displayCountry,
        options = Regions.countries.map { it to Locale("", it).displayCountry },
        onSelect = onSelect
    )
}

@Composable
private fun RegionPicker(country: String, subdivision: String?, onSelect: (String?) -> Unit) {
    if (country == "ES") {
        val none = stringResource(R.string.holidays_region_none)
        Dropdown(
            label = stringResource(R.string.holidays_region),
            value = Regions.spain.firstOrNull { it.first == subdivision }?.second ?: none,
            options = listOf("" to none) + Regions.spain,
            onSelect = { onSelect(it.ifEmpty { null }) }
        )
    } else {
        var text by rememberSaveable(country) { mutableStateOf(subdivision.orEmpty()) }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.holidays_region_code)) },
            supportingText = { Text(stringResource(R.string.holidays_region_code_hint, country)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextButton(onClick = { onSelect(text) }) { Text(stringResource(R.string.holidays_apply)) }
    }
}

/** D-16: province, then municipality with a search box; attribution link required by the data provider's terms. */
@Composable
private fun MunicipalityPicker(config: HolidayConfig, viewModel: HolidaySettingsViewModel) {
    val provinces by viewModel.provinces.collectAsStateWithLifecycle()
    val municipalities by viewModel.municipalities.collectAsStateWithLifecycle()
    val error by viewModel.placesError.collectAsStateWithLifecycle()
    var province by rememberSaveable(config.subdivision) { mutableStateOf(config.municipalityPath?.split('/')?.getOrNull(1)) }
    var searching by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(config.subdivision) { viewModel.loadProvinces() }
    LaunchedEffect(province) { province?.let(viewModel::loadMunicipalities) }
    LaunchedEffect(provinces) {
        if (province == null && provinces.size == 1) province = provinces.single().slug
    }

    Text(stringResource(R.string.holidays_local), style = MaterialTheme.typography.titleSmall)
    if (provinces.size > 1) {
        Dropdown(
            label = stringResource(R.string.holidays_province),
            value = provinces.firstOrNull { it.slug == province }?.name ?: stringResource(R.string.holidays_choose),
            options = provinces.map { it.slug to it.name },
            onSelect = { province = it }
        )
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(enabled = province != null) { searching = true }) {
            Text(
                stringResource(R.string.holidays_municipality),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(config.municipalityName ?: stringResource(R.string.holidays_choose), style = MaterialTheme.typography.bodyLarge)
        }
        if (config.municipalityPath != null) {
            TextButton(onClick = { viewModel.setMunicipality(province.orEmpty(), null) }) { Text(stringResource(R.string.holidays_remove)) }
        }
    }
    if (error) Text(stringResource(R.string.holidays_places_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    val uriHandler = LocalUriHandler.current
    Text(
        stringResource(R.string.holidays_local_attribution),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { uriHandler.openUri(LocalHolidays.ATTRIBUTION_URL) }.padding(vertical = 4.dp)
    )
    OfficialCalendars.forPath(config.municipalityPath)?.let { official ->
        Text(
            stringResource(R.string.holidays_official_source, official.attribution),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri(official.attributionUrl) }.padding(vertical = 4.dp)
        )
    }
    if (searching && province != null) {
        MunicipalitySearch(municipalities, onDismiss = { searching = false }) { place ->
            searching = false
            viewModel.setMunicipality(province!!, place)
        }
    }
}

@Composable
private fun MunicipalitySearch(all: List<LocalHolidays.Place>, onDismiss: () -> Unit, onPick: (LocalHolidays.Place) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val normalized = query.trim().lowercase()
    val matches = all.filter { normalized.isEmpty() || it.name.lowercase().contains(normalized) }.take(MAX_RESULTS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.holidays_municipality)) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.holidays_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (all.isEmpty()) Text(stringResource(R.string.holidays_loading), Modifier.padding(top = 8.dp))
                    matches.forEach { place ->
                        Text(place.name, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(place) }.padding(vertical = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.holidays_cancel)) } }
    )
}

private const val MAX_RESULTS = 100

@Composable
private fun Dropdown(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { open = true }.padding(vertical = 4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onSelect(code)
                })
            }
        }
    }
}

@Composable
private fun IcsField(url: String, onSave: (String) -> Unit) {
    var text by rememberSaveable(url) { mutableStateOf(url) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(stringResource(R.string.holidays_ics)) },
        supportingText = { Text(stringResource(R.string.holidays_ics_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    if (text.trim() != url) TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.holidays_apply)) }
}

@Composable
private fun refreshStatus(config: HolidayConfig): String = when {
    config.refreshing -> stringResource(R.string.holidays_refreshing)
    config.lastError != null -> stringResource(R.string.holidays_refresh_error, config.lastError)
    config.lastRefresh > 0 -> stringResource(
        R.string.holidays_refreshed,
        JavaDateFormat.getDateTimeInstance(JavaDateFormat.SHORT, JavaDateFormat.SHORT).format(Date(config.lastRefresh))
    )
    else -> stringResource(R.string.holidays_snapshot)
}

@Composable
private fun EveTime(time: LocalTime, onTime: (LocalTime) -> Unit) {
    val context = LocalContext.current
    val millis = time.atDate(LocalDate.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.holidays_eve_time), Modifier.weight(1f))
        TextButton(onClick = {
            TimePickerDialog(context, { _, h, m -> onTime(LocalTime.of(h, m)) }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
        }) { Text(DateFormat.getTimeFormat(context).format(Date(millis))) }
    }
}

@Composable
private fun HolidayRow(entry: HolidayEntry, onCancelled: (Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val origin = stringResource(
        when (entry.source) {
            HolidaySourceType.API -> R.string.holidays_origin_api
            HolidaySourceType.LOCAL -> R.string.holidays_origin_local
            HolidaySourceType.ICS -> R.string.holidays_origin_ics
            HolidaySourceType.MANUAL -> R.string.holidays_origin_manual
        }
    )
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = !entry.cancelled, onCheckedChange = { onCancelled(!it) })
        Column(Modifier.weight(1f)) {
            Text(entry.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) + " · " + entry.name)
            Text(
                if (entry.cancelled) stringResource(R.string.holidays_cancelled, origin) else origin,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.holidays_delete)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddHoliday(onAdd: (LocalDate, String, Boolean) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Button(onClick = { open = true }) { Text(stringResource(R.string.holidays_add)) }
    if (!open) return
    var name by rememberSaveable { mutableStateOf("") }
    var yearly by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val dateState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    val date = dateState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.holidays_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { picking = true }) {
                    Text(date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)) ?: stringResource(R.string.holidays_pick_date))
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.holidays_name)) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = yearly, onCheckedChange = { yearly = it })
                    Text(stringResource(R.string.holidays_yearly))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = date != null, onClick = {
                date?.let { onAdd(it, name, yearly) }
                open = false
            }) { Text(stringResource(R.string.holidays_save)) }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.holidays_cancel)) } }
    )
    if (picking) {
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.holidays_save)) } }
        ) { DatePicker(state = dateState) }
    }
}

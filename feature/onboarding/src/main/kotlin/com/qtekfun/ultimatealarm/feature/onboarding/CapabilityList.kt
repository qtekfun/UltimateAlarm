package com.qtekfun.ultimatealarm.feature.onboarding

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Diagnostics (spike) and later onboarding: every capability with its state and, when missing, a way to grant it. */
@Composable
fun CapabilityList(capabilities: Capabilities, onFix: (Capability) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CapabilityRow(R.string.capability_exact_alarms, capabilities.exactAlarms, critical = true) { onFix(Capability.EXACT_ALARMS) }
        CapabilityRow(R.string.capability_notifications, capabilities.notifications, critical = true) { onFix(Capability.NOTIFICATIONS) }
        CapabilityRow(R.string.capability_full_screen, capabilities.fullScreenIntent, critical = true) { onFix(Capability.FULL_SCREEN_INTENT) }
        CapabilityRow(R.string.capability_battery, capabilities.ignoringBatteryOptimizations, critical = false) {
            onFix(Capability.BATTERY_OPTIMIZATION)
        }
        CapabilityRow(R.string.capability_power_save_off, !capabilities.powerSaveMode, critical = false, fix = null)
        CapabilityRow(R.string.capability_nfc, capabilities.nfc, critical = false, fix = null)
        CapabilityRow(R.string.capability_camera, capabilities.camera, critical = false, fix = null)
        Text(
            stringResource(
                R.string.capability_device,
                capabilities.manufacturer,
                capabilities.model,
                capabilities.androidVersion,
                capabilities.sdk
            ),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun CapabilityRow(@StringRes label: Int, ok: Boolean, critical: Boolean, fix: (() -> Unit)?) {
    val (icon: ImageVector, tint) = when {
        ok -> Icons.Filled.CheckCircle to MaterialTheme.colorScheme.primary
        critical -> Icons.Filled.Error to MaterialTheme.colorScheme.error
        else -> Icons.Outlined.Info to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val state = stringResource(if (ok) R.string.capability_ok else R.string.capability_missing)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = state, tint = tint)
        Text(stringResource(label), Modifier.weight(1f).padding(horizontal = 12.dp))
        if (!ok && fix != null) {
            TextButton(onClick = fix) { Text(stringResource(R.string.capability_fix)) }
        }
    }
}

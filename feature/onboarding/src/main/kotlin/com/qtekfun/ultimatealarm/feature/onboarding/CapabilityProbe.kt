package com.qtekfun.ultimatealarm.feature.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.os.Build
import android.os.PowerManager

/** What the device lets the app do, read at runtime (CLAUDE.md rule 7: never assumed from the manufacturer). */
data class Capabilities(
    val exactAlarms: Boolean,
    val fullScreenIntent: Boolean,
    val notifications: Boolean,
    val ignoringBatteryOptimizations: Boolean,
    val nfc: Boolean,
    val camera: Boolean,
    val powerSaveMode: Boolean,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdk: Int
) {
    /** Missing capabilities that can stop an alarm from ringing or from showing (RF-O3 banner, diagnostics). */
    fun missingCritical(): List<Capability> = buildList {
        if (!exactAlarms) add(Capability.EXACT_ALARMS)
        if (!notifications) add(Capability.NOTIFICATIONS)
        if (!fullScreenIntent) add(Capability.FULL_SCREEN_INTENT)
    }
}

enum class Capability { EXACT_ALARMS, NOTIFICATIONS, FULL_SCREEN_INTENT, BATTERY_OPTIMIZATION }

object CapabilityProbe {
    fun probe(context: Context): Capabilities {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val packageManager = context.packageManager
        val notificationsAllowed = notifications.areNotificationsEnabled() &&
            (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                )
        return Capabilities(
            exactAlarms = alarmManager.canScheduleExactAlarms(),
            fullScreenIntent = Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || notifications.canUseFullScreenIntent(),
            notifications = notificationsAllowed,
            ignoringBatteryOptimizations = power.isIgnoringBatteryOptimizations(context.packageName),
            nfc = NfcAdapter.getDefaultAdapter(context) != null,
            camera = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            powerSaveMode = power.isPowerSaveMode,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT
        )
    }
}

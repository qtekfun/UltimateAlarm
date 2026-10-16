package com.qtekfun.ultimatealarm.feature.onboarding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The system screen that grants a capability. If this device has no such screen, the app details page is opened
 * instead (02-diseno §8: never assume an intent exists).
 */
object CapabilitySettings {
    fun open(context: Context, capability: Capability) {
        runCatching { context.startActivity(intentFor(context, capability).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .recoverCatching { if (it is ActivityNotFoundException) context.startActivity(appDetails(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    // Asking for the exemption directly is allowed for alarm clocks (F-Droid has no such policy anyway).
    @SuppressLint("BatteryLife")
    private fun intentFor(context: Context, capability: Capability): Intent {
        val pkg = "package:${context.packageName}".toUri()
        return when (capability) {
            Capability.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            Capability.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Capability.FULL_SCREEN_INTENT -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkg)
            } else {
                appDetails(context)
            }
            Capability.BATTERY_OPTIMIZATION -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
        }
    }

    private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
}

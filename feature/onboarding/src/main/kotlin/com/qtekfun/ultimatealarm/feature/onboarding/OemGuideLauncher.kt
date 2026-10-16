package com.qtekfun.ultimatealarm.feature.onboarding

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri

/** Loads the guide of this phone (manufacturer and brand from [Build], never assumed) and opens its screens. */
object OemGuideLauncher {
    fun topics(context: Context): Pair<OemGuide, List<ResolvedTopic>> {
        val file =
            runCatching { context.assets.open(OemGuides.ASSET).bufferedReader().use { OemGuides.parse(it.readText()) } }.getOrNull() ?: OemGuides.fallback()
        val guide = OemGuides.select(file, Build.MANUFACTURER, Build.BRAND)
        return guide to OemGuides.resolve(file, guide)
    }

    /**
     * Tries every candidate screen in order and stops at the first one the phone has (02-diseno §8: if an intent does not
     * exist, the text instructions remain). Returns whether something opened.
     */
    fun open(context: Context, topic: ResolvedTopic): Boolean = topic.intents.any { spec ->
        val intent = Intent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        spec.action?.let(intent::setAction)
        if (spec.componentPackage != null && spec.componentClass != null) intent.component = ComponentName(spec.componentPackage!!, spec.componentClass!!)
        spec.dataFor(context.packageName)?.let { intent.data = it.toUri() }
        spec.extrasFor(context.packageName).forEach { (key, value) -> intent.putExtra(key, value) }
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            // Some OEM screens are not exported; try the next one.
            false
        }
    }

    /**
     * The text of [topic]. Guide texts are looked up by name from the JSON; when that name is missing from this build the
     * generic text is used, referenced directly so it always exists (a missing resource must never crash the assistant).
     */
    fun textId(context: Context, topic: ResolvedTopic): Int {
        // The resource package, not the application id: a debug build adds a suffix to the latter.
        val resourcePackage = context.resources.getResourcePackageName(R.string.oem_generic_battery)
        return context.resources.getIdentifier(topic.textKey, "string", resourcePackage).takeIf { it != 0 } ?: genericText(topic.topic)
    }

    private fun genericText(topic: String): Int = when (topic) {
        GuideTopics.BATTERY -> R.string.oem_generic_battery
        GuideTopics.AUTOSTART -> R.string.oem_generic_autostart
        GuideTopics.RECENTS -> R.string.oem_generic_recents
        else -> R.string.oem_generic_fullscreen
    }
}

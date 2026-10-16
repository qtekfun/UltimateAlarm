package com.qtekfun.ultimatealarm.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the first-run assistant was completed (credential storage: it is UI state, not needed to ring). */
object OnboardingStore {
    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"

    fun isDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DONE, false)

    fun markDone(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_DONE, true) }
}

/**
 * RF-O1 / RF-O2: first-run assistant. Permissions (each with its system screen), the steps of this phone's manufacturer
 * (opened directly when the screen exists, explained otherwise) and a real one-minute autotest.
 */
@Composable
fun OnboardingScreen(onStartAutotest: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val capabilities = remember(refresh) { CapabilityProbe.probe(context) }
    val (guide, topics) = remember { OemGuideLauncher.topics(context) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    // The step scrolls; Back/Next stay fixed below it so they are reachable on long steps and small screens.
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LinearProgressIndicator(progress = { (step + 1) / STEPS.toFloat() }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                0 -> {
                    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onboarding_welcome_text))
                }
                1 -> {
                    Text(stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onboarding_permissions_text))
                    CapabilityList(capabilities, onFix = { capability ->
                        val runtime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        if (capability == Capability.NOTIFICATIONS && runtime) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            CapabilitySettings.open(context, capability)
                        }
                    })
                }
                2 -> {
                    Text(stringResource(R.string.onboarding_phone_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onboarding_phone_text, guideName(guide.id)))
                    topics.forEach { topic ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(stringResource(topicTitle(topic.topic)), style = MaterialTheme.typography.titleMedium)
                                Text(stringResource(OemGuideLauncher.textId(context, topic)))
                                if (topic.intents.isNotEmpty()) {
                                    OutlinedButton(onClick = {
                                        OemGuideLauncher.open(context, topic)
                                    }) { Text(stringResource(R.string.onboarding_open_settings)) }
                                }
                            }
                        }
                    }
                }
                else -> {
                    Text(stringResource(R.string.onboarding_autotest_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.onboarding_autotest_text))
                    Button(onClick = onStartAutotest, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.onboarding_autotest_start)) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (step >
                0
            ) {
                TextButton(onClick = { step-- }) { Text(stringResource(R.string.onboarding_back)) }
            } else {
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            Button(onClick = { if (step < STEPS - 1) step++ else onFinish() }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(if (step < STEPS - 1) R.string.onboarding_next else R.string.onboarding_done))
            }
        }
    }
}

private fun topicTitle(topic: String) = when (topic) {
    GuideTopics.BATTERY -> R.string.onboarding_topic_battery
    GuideTopics.AUTOSTART -> R.string.onboarding_topic_autostart
    GuideTopics.RECENTS -> R.string.onboarding_topic_recents
    else -> R.string.onboarding_topic_fullscreen
}

@Composable
private fun guideName(id: String): String = when (id) {
    "coloros" -> "ColorOS"
    "oneui" -> "One UI"
    "hyperos" -> "HyperOS / MIUI"
    "originos" -> "OriginOS / Funtouch OS"
    "magicos" -> "MagicOS"
    "stock" -> "Android"
    else -> stringResource(R.string.onboarding_generic_phone)
}

private const val STEPS = 4

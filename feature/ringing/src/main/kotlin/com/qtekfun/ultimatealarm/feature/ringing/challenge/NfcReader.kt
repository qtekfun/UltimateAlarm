package com.qtekfun.ultimatealarm.feature.ringing.challenge

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Hex id of an NFC tag, the identity a challenge compares (RF-R5). */
fun tagIdHex(id: ByteArray): String = id.joinToString("") { "%02X".format(it) }

/** While composed, reports the id of every NFC tag held to the phone (reader mode, so no other app reacts). */
@Composable
fun NfcTagListener(onTag: (String) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        val activity = context.findActivity()
        val adapter = NfcAdapter.getDefaultAdapter(context)
        if (activity != null && adapter != null) {
            val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
            adapter.enableReaderMode(activity, { tag -> ContextCompat.getMainExecutor(context).execute { onTag(tagIdHex(tag.id)) } }, flags, null)
        }
        onDispose { if (activity != null) runCatching { adapter?.disableReaderMode(activity) } }
    }
}

fun hasNfc(context: Context) = NfcAdapter.getDefaultAdapter(context) != null

fun hasCamera(context: Context) = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

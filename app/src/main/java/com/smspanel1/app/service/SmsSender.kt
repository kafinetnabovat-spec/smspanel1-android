package com.smspanel1.app.service

import android.Manifest
import android.content.pm.PackageManager
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

internal object SmsSender {
    enum class Result(val status: String) { SENT("sent"), FAILED("failed"), UNKNOWN("sending") }

    /** SENT means all parts were accepted by the radio, NOT delivery to the recipient.
     * Timeouts/cancellation can happen after dispatch, so they must never trigger a resend.
     */
    suspend fun send(context: Context, to: String, body: String, isAuthorized: () -> Boolean): Result {
        if (to.isBlank() || body.isBlank()) return Result.FAILED
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            return Result.FAILED
        }
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return Result.FAILED
        val parts = manager.divideMessage(body)
        if (parts.isEmpty()) return Result.FAILED
        val action = "${context.packageName}.SMS_SENT.${UUID.randomUUID()}"
        val result = CompletableDeferred<Result>()
        val tracker = SmsPartTracker(parts.size)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != action) return
                tracker.record(intent.getIntExtra("part", -1), resultCode == Activity.RESULT_OK)
                    ?.let { success -> result.complete(if (success) Result.SENT else Result.FAILED) }
            }
        }
        val intents = ArrayList<PendingIntent>()
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            parts.indices.forEach { index ->
                intents.add(PendingIntent.getBroadcast(context, index,
                    Intent(action).setPackage(context.packageName).putExtra("part", index),
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE))
            }
            currentCoroutineContext().ensureActive()
            if (!isAuthorized()) throw kotlinx.coroutines.CancellationException("Send authorization revoked")
            try {
                if (parts.size == 1) manager.sendTextMessage(to, null, body, intents[0], null)
                else manager.sendMultipartTextMessage(to, null, parts, intents, null)
            } catch (_: SecurityException) {
                return Result.FAILED
            } catch (_: IllegalArgumentException) {
                return Result.FAILED
            } catch (_: RuntimeException) {
                // A binder failure does not prove that dispatch did not happen.
                return Result.UNKNOWN
            }
            return withTimeoutOrNull(90_000) { result.await() } ?: Result.UNKNOWN
        } finally {
            context.unregisterReceiver(receiver)
            intents.forEach { it.cancel() }
        }
    }
}

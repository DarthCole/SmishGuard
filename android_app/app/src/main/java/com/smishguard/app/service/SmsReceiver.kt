package com.smishguard.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.content.ContextCompat

/*
 * SmsReceiver.kt — Broadcast Receiver for Incoming SMS
 * ======================================================
 * A "BroadcastReceiver" listens for system-wide events (broadcasts).
 * This one listens for SMS_RECEIVED — the system sends this broadcast
 * every time a new SMS arrives on the device.
 *
 * FLOW:
 *   1. New SMS arrives on the device
 *   2. Android sends a SMS_RECEIVED broadcast
 *   3. This receiver's onReceive() is called
 *   4. We extract the message content and forward it to SmsMonitorService
 *   5. The service runs the ML model and flags if fraudulent
 *
 * IMPORTANT SECURITY NOTES:
 *   - We NEVER intercept or block the SMS — the user still receives it normally
 *   - We only READ the message content for analysis
 *   - The receiver is protected by BROADCAST_SMS permission (only system can trigger it)
 *   - We do NOT log message content (privacy)
 *
 * "BroadcastReceiver" is an abstract class — we must override onReceive().
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    /**
     * Called by the system when a new SMS is received.
     *
     * @param context The Context in which the receiver is running
     * @param intent Contains the SMS data bundled by the system
     *
     * "override" indicates we're providing our implementation of the
     * abstract function defined in BroadcastReceiver.
     */
    override fun onReceive(context: Context, intent: Intent) {
        // Verify this is actually an SMS received broadcast
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return  // Not our broadcast, ignore it
        }

        Log.d(TAG, "SMS received broadcast triggered")

        // Extract SMS messages from the intent
        // "Telephony.Sms.Intents.getMessagesFromIntent()" is Android's
        // official method to parse SMS data from a broadcast intent.
        val smsMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (smsMessages.isNullOrEmpty()) {
            Log.w(TAG, "No messages found in broadcast intent")
            return
        }

        // SMS messages can be split across multiple parts (if > 160 chars).
        // We need to concatenate them back together.
        val sender = smsMessages[0].displayOriginatingAddress ?: "Unknown"
        val fullMessage = smsMessages.joinToString("") { it.messageBody ?: "" }
        // "joinToString" concatenates elements with a separator.
        // "" means no separator — just merge them together.
        // "{ it.messageBody ?: "" }" is a transform applied to each element.

        if (fullMessage.isBlank()) return

        Log.d(TAG, "SMS from: $sender (message length: ${fullMessage.length})")
        // NOTE: We log the sender and length, but NEVER the actual message content

        // Forward the message to the monitoring service for analysis
        val serviceIntent = Intent(context, SmsMonitorService::class.java).apply {
            action = SmsMonitorService.ACTION_ANALYZE_MESSAGE
            putExtra(SmsMonitorService.EXTRA_MESSAGE_BODY, fullMessage)
            putExtra(SmsMonitorService.EXTRA_SENDER, sender)
            // We use -1 as placeholder IDs here; the service can look up the
            // actual message ID from the Content Provider if needed.
            putExtra(SmsMonitorService.EXTRA_MESSAGE_ID, System.currentTimeMillis())
            putExtra(SmsMonitorService.EXTRA_THREAD_ID, -1L)
        }

        // "ContextCompat.startForegroundService" is a backward-compatible way
        // to start a foreground service. On Android 8.0+, it ensures the
        // service calls startForeground() within 5 seconds.
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start monitoring service", e)
        }
    }
}

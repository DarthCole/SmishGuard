package com.smishguard.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.smishguard.app.R
import com.smishguard.app.data.local.SmishGuardDatabase
import com.smishguard.app.data.local.entity.AnalysisResultEntity
import com.smishguard.app.ml.SmishDetector
import com.smishguard.app.ui.main.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/*
 * SmsMonitorService.kt — Foreground Service
 * ============================================
 * A "Foreground Service" is a long-running background task that shows a
 * persistent notification to the user. Android requires this for any
 * work that continues when the user leaves the app.
 *
 * LIFECYCLE:
 *   1. User toggles "Protection ON" in the app
 *   2. App calls startForegroundService(intent)
 *   3. Android creates this service and calls onCreate()
 *   4. onStartCommand() is called — we show the persistent notification
 *   5. Service runs until the user toggles "Protection OFF"
 *   6. App calls stopService(intent), which triggers onDestroy()
 *
 * WHY Foreground Service (not just a background service)?
 *   Since Android 8.0 (Oreo), background services are killed after ~1 minute
 *   when the app isn't in the foreground. Foreground services with a
 *   notification are allowed to run indefinitely.
 *
 * KEY CONCEPTS:
 *   - Service: An Android component that runs without a UI
 *   - IBinder: Interface for inter-process communication (we return null
 *     because this is a "started" service, not a "bound" service)
 *   - PendingIntent: A token that lets the system perform an action on
 *     our behalf later (e.g., opening the app when notification is tapped)
 *   - CoroutineScope: Defines the lifetime of coroutines — when the scope
 *     is cancelled, all coroutines within it are cancelled too
 *   - SupervisorJob: If one coroutine fails, others keep running
 */
class SmsMonitorService : Service() {

    companion object {
        private const val TAG = "SmsMonitorService"
        const val CHANNEL_ID = "smishguard_monitor_channel"
        const val NOTIFICATION_ID = 1001
        const val ALERT_CHANNEL_ID = "smishguard_alert_channel"
        const val ACTION_ANALYZE_MESSAGE = "com.smishguard.app.ANALYZE_MESSAGE"
        const val EXTRA_MESSAGE_BODY = "extra_message_body"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_SENDER = "extra_sender"
    }

    // "lateinit var" = this variable WILL be initialized, but not here.
    // It's a promise that we'll set it before using it (in onCreate).
    // If you try to use it before initializing, it crashes with
    // UninitializedPropertyAccessException.
    private lateinit var smishDetector: SmishDetector

    // Coroutine scope tied to this service's lifecycle
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // "Dispatchers.IO" = runs on background threads meant for I/O work
    // "+" combines the dispatcher with the supervisor job

    private lateinit var database: SmishGuardDatabase

    /**
     * Called when the service is first created.
     * Initialize resources here.
     */
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        smishDetector = SmishDetector(this)
        database = SmishGuardDatabase.getInstance(this)
        createNotificationChannels()
    }

    /**
     * Called every time the service receives a start command.
     *
     * START_STICKY tells Android: "If you kill this service due to low memory,
     * restart it when memory is available again."
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started with action: ${intent?.action}")
        // "${...}" is STRING INTERPOLATION — embeds an expression inside a string

        // Show the persistent "SmishGuard is protecting you" notification
        startForeground(NOTIFICATION_ID, createPersistentNotification())

        // Check if we have a specific message to analyze
        if (intent?.action == ACTION_ANALYZE_MESSAGE) {
            val messageBody = intent.getStringExtra(EXTRA_MESSAGE_BODY)
            val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1)
            val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
            val sender = intent.getStringExtra(EXTRA_SENDER)

            if (messageBody != null && messageId != -1L) {
                // Launch a coroutine to analyze the message asynchronously
                serviceScope.launch {
                    analyzeIncomingMessage(messageId, threadId, messageBody, sender)
                }
            }
        }

        return START_STICKY
    }

    /**
     * Analyze an incoming SMS message using the ML detector.
     * If fraudulent, show a warning notification.
     *
     * "private suspend fun" — private (only this class), suspend (coroutine)
     */
    private suspend fun analyzeIncomingMessage(
        messageId: Long,
        threadId: Long,
        messageBody: String,
        sender: String?
    ) {
        val (isFraudulent, confidence) = smishDetector.isFraudulent(messageBody)
        // DESTRUCTURING: unpacks the Pair into two separate variables

        // Save the analysis result to the local database
        database.analysisResultDao().insertResult(
            AnalysisResultEntity(
                messageId = messageId,
                threadId = threadId,
                isFraudulent = isFraudulent,
                confidenceScore = confidence,
                analyzedAt = System.currentTimeMillis()
            )
        )

        if (isFraudulent) {
            showFraudAlert(sender ?: "Unknown", confidence)
        }

        // NOTE: We do NOT log the message body — that would be a privacy violation
        Log.d(TAG, "Message $messageId analyzed. Fraudulent: $isFraudulent, Confidence: $confidence")
    }

    /**
     * Show a warning notification when a fraudulent SMS is detected.
     */
    private fun showFraudAlert(sender: String, confidence: Float) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // "apply { }" executes the block on the object and returns it
            // "or" is the bitwise OR operator for combining intent flags
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            // FLAG_IMMUTABLE: security requirement — the PendingIntent can't be modified
        )

        val confidencePercent = (confidence * 100).toInt()

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_warning_dot)
            .setContentTitle("⚠️ Suspicious SMS Detected")
            .setContentText("Message from $sender has $confidencePercent% fraud probability")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)  // Dismiss when tapped
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        // Generate unique notification ID from sender's hashCode so each sender
        // gets their own notification (and it updates if a new fraud message
        // comes from the same sender)
        notificationManager.notify(sender.hashCode(), notification)
    }

    /**
     * Create the persistent notification shown while the service runs.
     */
    private fun createPersistentNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("SmishGuard Active")
            .setContentText("Monitoring incoming SMS for fraud")
            .setPriority(NotificationCompat.PRIORITY_LOW)  // Low priority = less intrusive
            .setOngoing(true)  // Cannot be swiped away
            .setContentIntent(pendingIntent)
            .build()
    }

    /**
     * Create notification channels. Required since Android 8.0 (Oreo).
     * Channels let users control notification settings per-category.
     */
    private fun createNotificationChannels() {
        val monitorChannel = NotificationChannel(
            CHANNEL_ID,
            "Protection Status",
            NotificationManager.IMPORTANCE_LOW  // No sound for the persistent notification
        ).apply {
            description = "Shows when SmishGuard is actively monitoring SMS"
        }

        val alertChannel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Fraud Alerts",
            NotificationManager.IMPORTANCE_HIGH  // Sound + vibration for fraud alerts
        ).apply {
            description = "Alerts when a suspicious SMS is detected"
            enableVibration(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(monitorChannel)
        notificationManager.createNotificationChannel(alertChannel)
    }

    /**
     * Required override for bound services. We return null because
     * this is a STARTED service (not bound).
     */
    override fun onBind(intent: Intent?): IBinder? = null
    // "= null" is a single-expression function — equivalent to { return null }

    /**
     * Called when the service is being destroyed.
     * Clean up all resources to prevent memory leaks.
     */
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        smishDetector.close()       // Release ML model resources
        serviceScope.cancel()       // Cancel all running coroutines
    }
}

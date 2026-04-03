package com.smishguard.app.domain.model

/*
 * SmsMessage.kt — Domain Model
 * ==============================
 * Represents a SINGLE SMS message within a conversation.
 */
data class SmsMessage(
    val id: Long,                 // Unique message ID from the SMS Content Provider
    val threadId: Long,           // Which conversation thread this belongs to
    val address: String,          // Sender's phone number
    val body: String,             // The actual message text
    val timestamp: Long,          // When the message was sent/received (Unix millis)
    val isIncoming: Boolean,      // true = received, false = sent by user
    val isFlaggedFraudulent: Boolean = false,
    val fraudConfidence: Float = 0f
)

package com.smishguard.app.domain.model

/*
 * SmsConversation.kt — Domain Model
 * ===================================
 * Represents ONE conversation thread (all messages from one sender).
 * Now supports 3-category classification: SAFE, SPAM, FRAUD.
 *
 * "data class" automatically generates equals(), hashCode(), toString(),
 * copy(), and componentN() functions.
 */
data class SmsConversation(
    val threadId: Long,           // Unique ID for this conversation thread
    val senderAddress: String,    // Phone number or short code of the sender
    val senderName: String?,      // Contact name if available, null otherwise
    val lastMessage: String,      // The most recent message text in this thread
    val lastTimestamp: Long,      // Unix timestamp (milliseconds) of the last message
    val messageCount: Int,        // Total number of messages in this thread
    val threatCategory: ThreatCategory = ThreatCategory.SAFE,
    val threatConfidence: Float = 0f,  // Confidence score from 0.0 to 1.0
    val threatReason: String? = null   // Why this conversation was flagged (matched rule)
) {
    // Convenience properties
    val isThreat: Boolean get() = threatCategory != ThreatCategory.SAFE
    val isSpam: Boolean get() = threatCategory == ThreatCategory.SPAM
    val isFraud: Boolean get() = threatCategory == ThreatCategory.FRAUD
}

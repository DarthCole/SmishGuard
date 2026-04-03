package com.smishguard.app.domain.model

/*
 * SmsConversation.kt — Domain Model
 * ===================================
 * A "data class" in Kotlin automatically generates:
 *   - equals() / hashCode() — for comparing two objects
 *   - toString() — for printing a readable representation
 *   - copy() — for creating a modified clone
 *   - componentN() — for destructuring (val (id, sender) = conversation)
 *
 * This class represents ONE conversation thread (all messages from one sender).
 *
 * "val" = read-only property (set once in the constructor, never changed)
 */
data class SmsConversation(
    val threadId: Long,           // Unique ID for this conversation thread
    val senderAddress: String,    // Phone number or short code of the sender
    val senderName: String?,      // Contact name if available, null otherwise
    // "String?" means this value CAN be null — the "?" makes it nullable
    val lastMessage: String,      // The most recent message text in this thread
    val lastTimestamp: Long,      // Unix timestamp (milliseconds) of the last message
    val messageCount: Int,        // Total number of messages in this thread
    val isFlaggedFraudulent: Boolean = false,  // Whether our ML model flagged this
    // "= false" is a DEFAULT VALUE — if you don't pass this parameter, it's false
    val fraudConfidence: Float = 0f            // Confidence score from 0.0 to 1.0
    // "0f" means 0 as a Float (decimal number). The "f" suffix denotes Float type.
)

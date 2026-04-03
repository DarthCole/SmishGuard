package com.smishguard.app.domain.model

/*
 * AnalysisResult.kt — Domain Model
 * ==================================
 * Stores the result of our ML model's analysis of a single SMS message.
 * This is kept in a local encrypted database so we don't re-analyze messages.
 */
data class AnalysisResult(
    val messageId: Long,              // Links back to the SMS message ID
    val threadId: Long,               // The conversation thread this belongs to
    val isFraudulent: Boolean,        // Did the model flag this as smishing?
    val confidenceScore: Float,       // 0.0 (safe) to 1.0 (definitely fraud)
    val analyzedAt: Long              // Timestamp when analysis was performed
)

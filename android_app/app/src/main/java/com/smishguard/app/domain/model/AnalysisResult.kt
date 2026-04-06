package com.smishguard.app.domain.model

/*
 * AnalysisResult.kt — Domain Model
 * ==================================
 * Stores the result of SmishGuard's hybrid analysis of a single SMS message.
 * The classification has three possible outcomes: SAFE, SPAM, or FRAUD.
 *
 * Pipeline: DistilBERT (binary: Safe/Threat) → rule-based Spam/Fraud split
 */

/**
 * ThreatCategory — the three classification outcomes.
 *
 * "enum class" defines a fixed set of named constants.
 * Each entry is a singleton instance of the enum.
 */
enum class ThreatCategory {
    SAFE,   // Non-threatening message
    SPAM,   // Unsolicited but not directly harmful (lottery, betting, ads)
    FRAUD   // Actively deceptive / phishing / scam
}

data class AnalysisResult(
    val messageId: Long,                        // Links back to the SMS message ID
    val threadId: Long,                         // The conversation thread this belongs to
    val category: ThreatCategory,               // SAFE, SPAM, or FRAUD
    val confidenceScore: Float,                 // 0.0 (safe) to 1.0 (definitely threat)
    val analyzedAt: Long,                       // Timestamp when analysis was performed
    val matchedRule: String? = null              // Which rule/pattern triggered (for explainability)
) {
    // Convenience properties for backward compatibility and readability
    val isThreat: Boolean get() = category != ThreatCategory.SAFE
    val isSpam: Boolean get() = category == ThreatCategory.SPAM
    val isFraud: Boolean get() = category == ThreatCategory.FRAUD
}

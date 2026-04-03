package com.smishguard.app.ml

import android.content.Context
import android.util.Log
import java.io.Closeable

/*
 * SmishDetector.kt — Machine Learning Detection Engine
 * ======================================================
 * This class runs our smishing detection model ON-DEVICE.
 *
 * CURRENT IMPLEMENTATION: Heuristic-based (keyword + pattern matching).
 * This is a placeholder that works immediately. Later, you'll swap this
 * with a TensorFlow Lite model for much better accuracy.
 *
 * ARCHITECTURE NOTE: This class implements "Closeable" so resources
 * (like the TFLite interpreter) can be properly released when done.
 *
 * PRIVACY: ALL processing happens here on the device. The SMS text
 * is NEVER sent to any server, API, or cloud service.
 *
 * "object" keyword would make this a singleton, but we use a regular
 * class here so it can hold state (like a loaded ML model) and be
 * properly closed when the service stops.
 */
class SmishDetector(private val context: Context) : Closeable {
    // ": Closeable" means this class promises to have a close() function

    companion object {
        // These live on the CLASS, not on instances — like static in Java
        private const val TAG = "SmishDetector"

        // Threshold above which we flag a message as fraudulent
        // 0.6 = 60% confidence needed to flag as smishing
        private const val FRAUD_THRESHOLD = 0.6f
    }

    // ── Smishing indicator patterns ──────────────────────────────────
    // These are common patterns found in SMS phishing (smishing) attacks.
    // Each pair is (regex pattern, weight). Higher weight = stronger signal.
    private val smishingPatterns: List<Pair<Regex, Float>> = listOf(
        // "Pair" holds two values. "Regex" is a compiled regular expression.
        // "to" is an infix function that creates a Pair: "a to b" = Pair(a, b)

        // Urgency language
        Regex("(?i)\\b(urgent|immediately|right now|asap|expire|suspend)\\b") to 0.3f,

        // Financial lures
        Regex("(?i)\\b(won|winner|prize|reward|claim|free gift|congrat)") to 0.35f,

        // Account threats
        Regex("(?i)\\b(account.*suspend|verify.*account|confirm.*identity|unusual.*activity)") to 0.4f,

        // Action demands with links
        Regex("(?i)(click|tap|visit|go to|open).*http") to 0.35f,

        // Suspicious shortened URLs
        Regex("(?i)(bit\\.ly|tinyurl|t\\.co|goo\\.gl|rb\\.gy|is\\.gd|short\\.link)") to 0.3f,

        // Generic suspicious URLs (not from known legitimate domains)
        Regex("https?://[^\\s]+\\.(xyz|tk|ml|ga|cf|top|buzz|click|loan)") to 0.35f,

        // Impersonation of banks/services
        Regex("(?i)\\b(bank|paypal|apple|google|amazon|netflix|dhl|fedex|ups).*\\b(verify|confirm|update|secure)") to 0.3f,

        // OTP / code theft
        Regex("(?i)(share|send|provide).*(code|otp|pin|password)") to 0.4f,

        // Threat of negative consequence
        Regex("(?i)(will be|shall be).*(blocked|locked|terminated|deleted|closed)") to 0.3f,

        // Request for personal information
        Regex("(?i)(social security|ssn|date of birth|credit card|cvv|bank details)") to 0.45f
    )

    /**
     * Analyze a single SMS message and return a confidence score.
     *
     * @param messageBody The text content of the SMS
     * @return A Float from 0.0 (safe) to 1.0 (definitely smishing)
     *
     * "fun" declares a function.
     * ": Float" after the parentheses is the RETURN TYPE.
     */
    fun analyzeMessage(messageBody: String): Float {
        if (messageBody.isBlank()) return 0f
        // "isBlank()" returns true if the string is empty or only whitespace
        // Early return — if nothing to analyze, score is 0

        var score = 0f
        // "var" because we need to modify this value ("val" would be read-only)

        for ((pattern, weight) in smishingPatterns) {
            // Destructuring: extracts the two values from each Pair
            if (pattern.containsMatchIn(messageBody)) {
                score += weight
                // "+=" is shorthand for "score = score + weight"
            }
        }

        // Clamp the score between 0.0 and 1.0
        // "coerceIn" restricts a value to a range
        return score.coerceIn(0f, 1f)
    }

    /**
     * Determine if a message is fraudulent based on its analysis score.
     *
     * @return Pair<Boolean, Float> — (isFraudulent, confidenceScore)
     */
    fun isFraudulent(messageBody: String): Pair<Boolean, Float> {
        val score = analyzeMessage(messageBody)
        return Pair(score >= FRAUD_THRESHOLD, score)
        // Returns both the boolean flag AND the score
    }

    /**
     * Release any resources held by the detector.
     * Called when the background service stops.
     * Currently no resources to release with the heuristic approach,
     * but this will be needed when we add TFLite.
     *
     * "override" means we're providing OUR implementation of a function
     * defined in the Closeable interface.
     */
    override fun close() {
        Log.d(TAG, "SmishDetector resources released")
        // Future: interpreter?.close()
    }
}

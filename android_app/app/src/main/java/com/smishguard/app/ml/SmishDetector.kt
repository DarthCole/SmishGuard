package com.smishguard.app.ml

import android.content.Context
import android.util.Log
import com.smishguard.app.domain.model.ThreatCategory
import org.tensorflow.lite.Interpreter
import java.io.BufferedReader
import java.io.Closeable
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/*
 * SmishDetector.kt — Hybrid Detection Engine
 * =============================================
 * SmishGuard's core classification pipeline. Combines:
 *   1. Sender whitelist matching (safe_senders.txt + safe_chats.csv)
 *   2. User contacts matching (phone contacts → SAFE override)
 *   3. DistilBERT TFLite model (binary: Safe vs Threat)
 *   4. Regex rule engine (splits Threat into SPAM vs FRAUD)
 *
 * Classification flow:
 *   SMS → [Contact check] → [Whitelist check] → [DistilBERT] → [Spam/Fraud rules]
 *
 * If the TFLite model file is not found in assets, the detector falls back
 * to heuristic-only mode so the app still works without the model.
 */
class SmishDetector private constructor(private val context: Context) : Closeable {

    companion object {
        private const val TAG = "SmishDetector"
        private const val MODEL_FILE = "smishguard_model.tflite"
        private const val VOCAB_FILE = "vocab.txt"
        private const val SAFE_SENDERS_FILE = "safe_senders.txt"
        private const val SAFE_CHATS_FILE = "safe_chats.csv"
        private const val MAX_SEQ_LENGTH = 128
        private const val THREAT_THRESHOLD = 0.4f
        // Higher threshold required when no regex rule matches — prevents
        // borderline model scores on casual messages from triggering false alarms
        private const val MODEL_ONLY_THRESHOLD = 0.6f

        @Volatile
        private var instance: SmishDetector? = null

        fun getInstance(context: Context): SmishDetector {
            return instance ?: synchronized(this) {
                instance ?: SmishDetector(context.applicationContext).also { instance = it }
            }
        }
    }

    // TFLite interpreter — null until first classify() call (lazy loading)
    private var interpreter: Interpreter? = null
    private var modelLoadAttempted = false

    // WordPiece vocabulary: token string → token ID
    private val vocab: Map<String, Int>

    // Known safe sender names (loaded from assets)
    private val safeSenders: Set<String>

    // Known safe contact/chat names (loaded from assets)
    private val safeChats: Set<String>

    // Whether the TFLite model is available
    val isModelLoaded: Boolean get() = interpreter != null

    // ── Initialization ──────────────────────────────────────────
    // Only load lightweight assets here. The heavy TFLite model is loaded
    // lazily on the first classify() call to avoid OOM during app startup.
    init {
        vocab = loadVocabulary()
        safeSenders = loadSafeSenders()
        safeChats = loadSafeChats()
        Log.d(TAG, "SmishDetector initialized (model loading deferred): " +
                "vocab=${vocab.size}, safeSenders=${safeSenders.size}, " +
                "safeChats=${safeChats.size}")
    }

    /**
     * Ensure the TFLite model is loaded. Called lazily before first inference.
     * Synchronized to prevent double-loading from concurrent coroutines.
     */
    @Synchronized
    private fun ensureModelLoaded() {
        if (!modelLoadAttempted) {
            modelLoadAttempted = true
            interpreter = loadModel()
            Log.d(TAG, "Model load result: ${if (interpreter != null) "SUCCESS" else "FALLBACK TO HEURISTICS"}")
        }
    }

    // ════════════════════════════════════════════════════════════
    //  PUBLIC API
    // ════════════════════════════════════════════════════════════

    /**
     * Full classification pipeline. Returns (ThreatCategory, confidence, matchedRule).
     *
     * @param messageBody The SMS text content
     * @param senderName  The conversation/sender name (from SMS thread or contacts)
     * @param isInContacts Whether this sender is in the user's phone contacts
     */
    fun classify(
        messageBody: String,
        senderName: String?,
        isInContacts: Boolean
    ): Triple<ThreatCategory, Float, String?> {
        // "Triple" holds three values — like Pair but with three elements

        // Lazy-load the TFLite model on first call
        ensureModelLoaded()

        // ── Step 1: Always run the model/heuristics on the message content ──
        // Prepend sender name to message body (model was trained with this format)
        val modelInput = if (senderName != null) {
            "$senderName: $messageBody"
        } else {
            "Unknown: $messageBody"
        }

        val rawThreatScore = if (interpreter != null) {
            runModel(modelInput)
        } else {
            runHeuristicFallback(messageBody)
        }

        val snippet = messageBody.take(60).replace("\n", " ")
        Log.d(TAG, "classify: sender=$senderName, raw=$rawThreatScore, msg=\"$snippet...\"")

        // Short/casual messages with no URLs or suspicious patterns are safe.
        // Prevents false positives on greetings like "yo what's up", "hello twinn", etc.
        val hasUrl = messageBody.contains("http", ignoreCase = true)
                || messageBody.contains("bit.ly", ignoreCase = true)
        val hasDigitSequence = Regex("\\d{4,}").containsMatchIn(messageBody)
        val isCasualGreeting = casualGreetingPattern.containsMatchIn(messageBody)

        if (messageBody.length < 40 && !hasUrl && !hasDigitSequence) {
            // Very short messages or messages matching casual greetings
            if (messageBody.length < 15 || isCasualGreeting) {
                Log.d(TAG, "Short/casual harmless message — skipping threat classification")
                return Triple(ThreatCategory.SAFE, rawThreatScore, "short_casual_message")
            }
        }

        // ── Step 2: Apply contact/whitelist adjustments ──
        // Contacts and whitelisted senders get a reduced threat score,
        // but extremely suspicious content still gets flagged
        val normalizedSender = senderName?.trim()?.lowercase() ?: ""
        val isWhitelisted = isSafeSender(normalizedSender)
        val threatScore: Float
        val contextNote: String?

        if (isInContacts && senderName != null) {
            // Contact: reduce score by 40% — suspicious content can still trigger
            threatScore = rawThreatScore * 0.6f
            contextNote = "contact_adjusted"
            Log.d(TAG, "Sender '$senderName' is in contacts — score adjusted: $rawThreatScore → $threatScore")
        } else if (isWhitelisted) {
            // Whitelisted sender: reduce score by 30%
            threatScore = rawThreatScore * 0.7f
            contextNote = "whitelist_adjusted"
            Log.d(TAG, "Sender '$senderName' is whitelisted — score adjusted: $rawThreatScore → $threatScore")
        } else {
            threatScore = rawThreatScore
            contextNote = null
        }

        // ── Step 3: If threat detected, classify as SPAM or FRAUD ──
        if (threatScore >= THREAT_THRESHOLD) {
            val (subCategory, rule) = classifyThreatType(messageBody)

            // If NO specific rule matched (model_threat_default), require a higher
            // confidence threshold. This prevents borderline model scores (0.4–0.6)
            // on casual/conversational messages from being classified as threats.
            if (rule == "model_threat_default" && threatScore < MODEL_ONLY_THRESHOLD) {
                Log.d(TAG, "Model-only detection at borderline score $threatScore — treating as SAFE")
                return Triple(ThreatCategory.SAFE, threatScore, "model_borderline_safe")
            }

            val finalRule = if (contextNote != null) "$rule ($contextNote)" else rule
            Log.d(TAG, "Message classified as $subCategory (score=$threatScore, rule=$finalRule)")
            return Triple(subCategory, threatScore, finalRule)
        }

        return Triple(ThreatCategory.SAFE, threatScore, contextNote)
    }

    // ════════════════════════════════════════════════════════════
    //  SENDER WHITELIST MATCHING
    // ════════════════════════════════════════════════════════════

    /**
     * Check if a sender name matches any known-safe sender.
     * Uses case-insensitive partial matching with both lists.
     */
    private fun isSafeSender(normalizedSender: String): Boolean {
        if (normalizedSender.isEmpty()) return false

        // Check exact match in safe senders list
        if (normalizedSender in safeSenders) return true

        // Check exact match in safe chats list
        if (normalizedSender in safeChats) return true

        // Check if the sender name CONTAINS a known safe name
        // (e.g., "MTN Ghana" contains "MTN")
        for (safe in safeSenders) {
            if (normalizedSender.contains(safe) || safe.contains(normalizedSender)) {
                return true
            }
        }
        for (safe in safeChats) {
            if (normalizedSender.contains(safe) || safe.contains(normalizedSender)) {
                return true
            }
        }

        return false
    }

    // ════════════════════════════════════════════════════════════
    //  DISTILBERT TFLITE INFERENCE
    // ════════════════════════════════════════════════════════════

    /**
     * Run the DistilBERT TFLite model on a message.
     * Returns threat probability (0.0 = safe, 1.0 = threat).
     */
    private fun runModel(text: String): Float {
        val interp = interpreter ?: return runHeuristicFallback(text)

        // Tokenize using WordPiece
        val tokenIds = tokenize(text)

        // Create attention mask (1 for real tokens, 0 for padding)
        val attentionMask = IntArray(MAX_SEQ_LENGTH) { if (it < tokenIds.size) 1 else 0 }

        // Pad token IDs to MAX_SEQ_LENGTH
        val paddedIds = IntArray(MAX_SEQ_LENGTH) { if (it < tokenIds.size) tokenIds[it] else 0 }

        // Prepare input tensors — shape [1, 128] for each
        val inputIds = Array(1) { paddedIds }
        val inputMask = Array(1) { attentionMask }

        // Prepare output tensor — shape [1, 2] (probabilities for [safe, threat])
        val output = Array(1) { FloatArray(2) }

        try {
            // Run inference with multiple inputs
            val inputs = arrayOf(inputIds, inputMask)
            val outputs = mapOf(0 to output)
            interp.runForMultipleInputsOutputs(inputs, outputs)

            val safeProb = output[0][0]
            val threatProb = output[0][1]
            Log.d(TAG, "Model output: safe=$safeProb, threat=$threatProb")
            return threatProb
        } catch (e: Exception) {
            Log.e(TAG, "Model inference failed, falling back to heuristics", e)
            return runHeuristicFallback(text)
        }
    }

    // ════════════════════════════════════════════════════════════
    //  WORDPIECE TOKENIZER
    // ════════════════════════════════════════════════════════════

    /**
     * WordPiece tokenization — the same algorithm used by DistilBERT.
     *
     * Steps:
     *   1. Lowercase and split text into words
     *   2. For each word, try to find it in the vocabulary
     *   3. If not found, break it into subword pieces (prefixed with "##")
     *   4. Add [CLS] at start and [SEP] at end
     *
     * Example: "smishing" → ["[CLS]", "sm", "##ishing", "[SEP]"]
     */
    private fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>()
        val clsId = vocab["[CLS]"] ?: 101
        val sepId = vocab["[SEP]"] ?: 102
        val unkId = vocab["[UNK]"] ?: 100

        tokens.add(clsId)

        // Split into words, lowercase, limit to MAX_SEQ_LENGTH - 2 tokens
        val words = text.lowercase().split(Regex("\\s+"))

        for (word in words) {
            if (tokens.size >= MAX_SEQ_LENGTH - 1) break

            // Clean the word: keep only alphanumeric and basic punctuation
            val cleanWord = word.replace(Regex("[^a-z0-9.,!?'/@#:;\\-]"), "")
            if (cleanWord.isEmpty()) continue

            // Try the full word first
            if (cleanWord in vocab) {
                tokens.add(vocab[cleanWord]!!)
                continue
            }

            // WordPiece: break into subwords
            var start = 0
            var matched = false
            while (start < cleanWord.length) {
                if (tokens.size >= MAX_SEQ_LENGTH - 1) break

                var end = cleanWord.length
                var found = false

                while (start < end) {
                    val subword = if (start == 0) {
                        cleanWord.substring(start, end)
                    } else {
                        "##" + cleanWord.substring(start, end)
                    }

                    if (subword in vocab) {
                        tokens.add(vocab[subword]!!)
                        start = end
                        found = true
                        matched = true
                        break
                    }
                    end--
                }

                if (!found) {
                    // Character not in vocab — use [UNK]
                    tokens.add(unkId)
                    start++
                }
            }
        }

        tokens.add(sepId)
        return tokens
    }

    // ════════════════════════════════════════════════════════════
    //  REGEX RULE ENGINE — SPAM vs FRAUD CLASSIFICATION
    // ════════════════════════════════════════════════════════════

    /**
     * Once the model says "Threat", these regex rules determine
     * whether it's SPAM or FRAUD.
     *
     * SPAM patterns: lottery, gambling, betting, unsolicited ads
     * FRAUD patterns: phishing, fake deliveries, urgency, impersonation
     */

    // Casual greeting patterns — used to detect harmless conversational messages
    // that the model may give borderline scores to
    private val casualGreetingPattern = Regex(
        """(?i)^\s*(hey|hi|hello|yo|sup|what'?s\s*up|howdy|hola|good\s*(morning|afternoon|evening|night)|how\s*(are|r)\s*(you|u)|what\s*(are|r)\s*(you|u)\s*(up to|doing)|long\s*time|twin+|bro|sis|fam|charle|chale|eii|herh|boss|g[uy]\b)\b"""
    )

    // Spam indicators — unsolicited but not directly deceptive
    private val spamPatterns: List<Pair<Regex, String>> = listOf(
        Regex("(?i)\\b(lotto|lottery|jackpot|mega\\s*jackpot)\\b") to "lottery_keywords",
        Regex("(?i)\\b(afriluck|nla|national lottery)\\b") to "lottery_brand",
        Regex("(?i)\\b(dial \\*7[0-9]{2}#)") to "lottery_shortcode",
        Regex("(?i)\\b(win|won)\\s+.{0,20}(cash|money|ghs|ghc|cedis)\\b") to "win_money",
        Regex("(?i)\\b(bet|betting|stake|gamble|play.*draw)\\b") to "gambling_keywords",
        Regex("(?i)\\b(daily deal|exclusive.{0,10}offer|subscribe now)\\b") to "promo_language",
    )

    // Fraud indicators — actively deceptive / phishing
    private val fraudPatterns: List<Pair<Regex, String>> = listOf(
        Regex("(?i)(verify|confirm|update).{0,30}(address|information|identity|account|location)") to "verify_demand",
        Regex("(?i)(suspend|suspended|temporarily|blocked|locked)") to "account_threat",
        Regex("(?i)(package|parcel|delivery|shipment|order).{0,30}(suspend|return|address|house)") to "fake_delivery",
        Regex("(?i)(https?://[^\\s]*(ghanapost|ghapost|ghaposta|turviza|lihi\\.cc|fuiuiuni)[^\\s]*)") to "phishing_url",
        Regex("(?i)\\b(wa\\.me/|whatsapp\\.com/)\\d+") to "whatsapp_scam",
        Regex("(?i)(daily salary|start work|contact for details)") to "job_scam",
        Regex("(?i)(dhl|fedex|ups|post office).{0,30}(verify|action|return|address)") to "delivery_impersonation",
        Regex("(?i)(outstanding|overdue).{0,20}(ticket|fine|fee|payment)") to "fake_fine",
        Regex("(?i)(storage fee|last reminder|will be returned)") to "urgency_scam",
        Regex("(?i)https?://[^\\s]*\\.(cn|top|blog|xyz|tk)/") to "suspicious_tld",
    )

    /**
     * Classify a threat as SPAM or FRAUD based on regex pattern matching.
     * Returns (ThreatCategory, matched_rule_name).
     * Defaults to FRAUD if no spam pattern matches (safer default).
     */
    private fun classifyThreatType(messageBody: String): Pair<ThreatCategory, String> {
        // Check fraud patterns first (higher priority)
        for ((pattern, ruleName) in fraudPatterns) {
            if (pattern.containsMatchIn(messageBody)) {
                return Pair(ThreatCategory.FRAUD, ruleName)
            }
        }

        // Check spam patterns
        for ((pattern, ruleName) in spamPatterns) {
            if (pattern.containsMatchIn(messageBody)) {
                return Pair(ThreatCategory.SPAM, ruleName)
            }
        }

        // Default: if model says threat but no specific rule matches, classify as FRAUD
        // (safer default — better to over-warn than under-warn for fraud)
        return Pair(ThreatCategory.FRAUD, "model_threat_default")
    }

    // ════════════════════════════════════════════════════════════
    //  HEURISTIC FALLBACK (when TFLite model not available)
    // ════════════════════════════════════════════════════════════

    private val heuristicPatterns: List<Pair<Regex, Float>> = listOf(
        Regex("(?i)\\b(urgent|immediately|right now|asap|expire|suspend)\\b") to 0.3f,
        Regex("(?i)\\b(won|winner|prize|reward|claim|free gift|congrat)") to 0.35f,
        Regex("(?i)\\b(account.*suspend|verify.*account|confirm.*identity)") to 0.4f,
        Regex("(?i)(click|tap|visit|go to|open).*http") to 0.35f,
        Regex("(?i)(bit\\.ly|tinyurl|t\\.co|goo\\.gl)") to 0.3f,
        Regex("(?i)(share|send|provide).*(code|otp|pin|password)") to 0.4f,
        Regex("(?i)(will be|shall be).*(blocked|locked|terminated|deleted)") to 0.3f,
        Regex("(?i)(daily salary|start work|contact for details)") to 0.3f,
        Regex("(?i)(ghanapost|ghapost|ghaposta|turviza|lihi\\.cc|fuiuiuni)") to 0.4f,
        Regex("(?i)\\b(lotto|lottery|jackpot|afriluck|nla)\\b") to 0.25f,
    )

    private fun runHeuristicFallback(messageBody: String): Float {
        if (messageBody.isBlank()) return 0f
        var score = 0f
        for ((pattern, weight) in heuristicPatterns) {
            if (pattern.containsMatchIn(messageBody)) {
                score += weight
            }
        }
        return score.coerceIn(0f, 1f)
    }

    // ════════════════════════════════════════════════════════════
    //  ASSET LOADERS
    // ════════════════════════════════════════════════════════════

    private fun loadModel(): Interpreter? {
        return try {
            val assetFd = context.assets.openFd(MODEL_FILE)
            val inputStream = FileInputStream(assetFd.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = assetFd.startOffset
            val declaredLength = assetFd.declaredLength
            val modelBuffer: MappedByteBuffer = fileChannel.map(
                FileChannel.MapMode.READ_ONLY, startOffset, declaredLength
            )
            val options = Interpreter.Options().apply {
                setNumThreads(1)  // Reduce memory usage on low-RAM devices
            }
            Interpreter(modelBuffer, options).also {
                Log.d(TAG, "TFLite model loaded successfully")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TFLite model not found in assets — using heuristic fallback", e)
            null
        }
    }

    private fun loadVocabulary(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        try {
            context.assets.open(VOCAB_FILE).bufferedReader().useLines { lines ->
                lines.forEachIndexed { index, token ->
                    map[token] = index
                }
            }
            Log.d(TAG, "Vocabulary loaded: ${map.size} tokens")
        } catch (e: Exception) {
            Log.w(TAG, "Vocabulary file not found — tokenizer disabled", e)
        }
        return map
    }

    private fun loadSafeSenders(): Set<String> {
        val senders = mutableSetOf<String>()
        try {
            context.assets.open(SAFE_SENDERS_FILE).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim().lowercase()
                    if (trimmed.isNotEmpty()) senders.add(trimmed)
                }
            }
            Log.d(TAG, "Safe senders loaded: ${senders.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Safe senders file not found", e)
        }
        return senders
    }

    private fun loadSafeChats(): Set<String> {
        val chats = mutableSetOf<String>()
        try {
            context.assets.open(SAFE_CHATS_FILE).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim().lowercase()
                    // Skip CSV header
                    if (trimmed.isNotEmpty() && trimmed != "name") chats.add(trimmed)
                }
            }
            Log.d(TAG, "Safe chats loaded: ${chats.size}")
        } catch (e: Exception) {
            Log.w(TAG, "Safe chats file not found", e)
        }
        return chats
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        Log.d(TAG, "SmishDetector resources released")
    }
}

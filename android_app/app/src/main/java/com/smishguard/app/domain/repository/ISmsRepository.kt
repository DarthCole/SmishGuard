package com.smishguard.app.domain.repository

import com.smishguard.app.domain.model.AnalysisResult
import com.smishguard.app.domain.model.SmsConversation
import com.smishguard.app.domain.model.SmsMessage

/*
 * ISmsRepository.kt — Repository Interface (Contract)
 * =====================================================
 * An "interface" in Kotlin defines a CONTRACT — it says "any class that
 * implements me MUST provide these functions" but doesn't say HOW.
 *
 * WHY use an interface?
 *   1. TESTABILITY: In tests, you can create a fake implementation
 *   2. SEPARATION: The domain layer doesn't know about Android's ContentResolver
 *   3. FLEXIBILITY: You can swap implementations without changing business logic
 *
 * "suspend fun" = a coroutine function. It can be paused and resumed without
 * blocking the main thread. Reading SMS from the Content Provider is slow,
 * so we do it on a background thread via coroutines.
 */
interface ISmsRepository {

    /**
     * Get all SMS conversation threads, each enriched with analysis results
     * showing whether the conversation has been flagged as fraudulent.
     */
    suspend fun getConversations(): List<SmsConversation>

    /**
     * Get all messages in a specific conversation thread.
     */
    suspend fun getMessagesForThread(threadId: Long): List<SmsMessage>

    /**
     * Save the ML model's analysis result for a specific message.
     */
    suspend fun saveAnalysisResult(result: AnalysisResult)

    /**
     * Get stored analysis results for a specific conversation thread.
     */
    suspend fun getAnalysisResultsForThread(threadId: Long): List<AnalysisResult>

    /**
     * Check if a specific message has already been analyzed.
     * Returns null if not yet analyzed.
     */
    suspend fun getAnalysisResultForMessage(messageId: Long): AnalysisResult?
}

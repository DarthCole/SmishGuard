package com.smishguard.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import com.smishguard.app.data.local.SmishGuardDatabase
import com.smishguard.app.data.local.entity.AnalysisResultEntity
import com.smishguard.app.domain.model.AnalysisResult
import com.smishguard.app.domain.model.SmsConversation
import com.smishguard.app.domain.model.SmsMessage
import com.smishguard.app.domain.model.ThreatCategory
import com.smishguard.app.domain.repository.ISmsRepository
import com.smishguard.app.ml.SmishDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsRepository(
    private val context: Context
) : ISmsRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val database: SmishGuardDatabase = SmishGuardDatabase.getInstance(context)
    private val analysisDao = database.analysisResultDao()

    override suspend fun getConversations(): List<SmsConversation> =
        withContext(Dispatchers.IO) {
            val threadMap = mutableMapOf<Long, SmsConversation>()

            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIndex)

                    if (threadId !in threadMap) {
                        val address = cursor.getString(addressIndex) ?: "Unknown"
                        val body = cursor.getString(bodyIndex) ?: ""
                        val date = cursor.getLong(dateIndex)

                        // Determine worst threat category for this thread
                        val analysisResults = analysisDao.getResultsForThread(threadId)
                        val worstCategory = analysisResults
                            .map { parseThreatCategory(it.threatCategory) }
                            .maxByOrNull { it.ordinal }
                            ?: ThreatCategory.SAFE
                        val maxConfidence = analysisResults
                            .filter { parseThreatCategory(it.threatCategory) != ThreatCategory.SAFE }
                            .maxOfOrNull { it.confidenceScore } ?: 0f
                        val threatRule = analysisResults
                            .filter { parseThreatCategory(it.threatCategory) != ThreatCategory.SAFE }
                            .maxByOrNull { it.confidenceScore }
                            ?.matchedRule

                        threadMap[threadId] = SmsConversation(
                            threadId = threadId,
                            senderAddress = address,
                            senderName = resolveContactName(address),
                            lastMessage = body,
                            lastTimestamp = date,
                            messageCount = 0,
                            threatCategory = worstCategory,
                            threatConfidence = maxConfidence,
                            threatReason = threatRule
                        )
                    }
                }
            }

            // Count messages per thread
            threadMap.keys.forEach { threadId ->
                val count = countMessagesInThread(threadId)
                threadMap[threadId] = threadMap[threadId]!!.copy(messageCount = count)
            }

            threadMap.values.sortedByDescending { it.lastTimestamp }
        }

    override suspend fun getMessagesForThread(threadId: Long): List<SmsMessage> =
        withContext(Dispatchers.IO) {
            val messages = mutableListOf<SmsMessage>()

            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(idIndex)
                    val analysisResult = analysisDao.getResultForMessage(messageId)
                    val category = if (analysisResult != null) {
                        parseThreatCategory(analysisResult.threatCategory)
                    } else {
                        ThreatCategory.SAFE
                    }

                    messages.add(
                        SmsMessage(
                            id = messageId,
                            threadId = threadId,
                            address = cursor.getString(addressIndex) ?: "Unknown",
                            body = cursor.getString(bodyIndex) ?: "",
                            timestamp = cursor.getLong(dateIndex),
                            isIncoming = cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            threatCategory = category,
                            threatConfidence = analysisResult?.confidenceScore ?: 0f
                        )
                    )
                }
            }

            messages
        }

    override suspend fun saveAnalysisResult(result: AnalysisResult) {
        withContext(Dispatchers.IO) {
            analysisDao.insertResult(
                AnalysisResultEntity(
                    messageId = result.messageId,
                    threadId = result.threadId,
                    threatCategory = result.category.name,
                    confidenceScore = result.confidenceScore,
                    analyzedAt = result.analyzedAt,
                    matchedRule = result.matchedRule
                )
            )
        }
    }

    override suspend fun getAnalysisResultsForThread(threadId: Long): List<AnalysisResult> =
        withContext(Dispatchers.IO) {
            analysisDao.getResultsForThread(threadId).map { entity ->
                AnalysisResult(
                    messageId = entity.messageId,
                    threadId = entity.threadId,
                    category = parseThreatCategory(entity.threatCategory),
                    confidenceScore = entity.confidenceScore,
                    analyzedAt = entity.analyzedAt,
                    matchedRule = entity.matchedRule
                )
            }
        }

    override suspend fun getAnalysisResultForMessage(messageId: Long): AnalysisResult? =
        withContext(Dispatchers.IO) {
            analysisDao.getResultForMessage(messageId)?.let { entity ->
                AnalysisResult(
                    messageId = entity.messageId,
                    threadId = entity.threadId,
                    category = parseThreatCategory(entity.threatCategory),
                    confidenceScore = entity.confidenceScore,
                    analyzedAt = entity.analyzedAt,
                    matchedRule = entity.matchedRule
                )
            }
        }

    /**
     * Check if a phone number exists in the user's contacts.
     */
    fun isInContacts(phoneNumber: String): Boolean {
        return resolveContactName(phoneNumber) != null
    }

    /**
     * Resolve a phone number to a contact name.
     * Returns null if the number isn't in the user's contacts.
     */
    fun resolveContactName(phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun countMessagesInThread(threadId: Long): Int {
        return contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            null
        )?.use { it.count } ?: 0
    }

    /**
     * Scan all incoming SMS messages that haven't been analyzed yet.
     * Runs SmishDetector on each unanalyzed message and stores results.
     */
    suspend fun scanUnanalyzedMessages(detector: SmishDetector) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.TYPE
        )

        // Only scan incoming messages (TYPE_INBOX = 1)
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.TYPE} = ?",
            arrayOf(Telephony.Sms.MESSAGE_TYPE_INBOX.toString()),
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)

            while (cursor.moveToNext()) {
                val messageId = cursor.getLong(idIndex)

                // Skip if already analyzed
                if (analysisDao.getResultForMessage(messageId) != null) continue

                val threadId = cursor.getLong(threadIdIndex)
                val address = cursor.getString(addressIndex) ?: "Unknown"
                val body = cursor.getString(bodyIndex) ?: ""
                if (body.isBlank()) continue

                val contactName = resolveContactName(address)
                val senderName = contactName ?: address

                // Pass isInContacts=false for batch scanning so contact/whitelist
                // adjustments don't reduce scores — we want to detect dangerous
                // content regardless of who forwarded it
                val (category, confidence, matchedRule) = detector.classify(
                    messageBody = body,
                    senderName = senderName,
                    isInContacts = false
                )

                analysisDao.insertResult(
                    AnalysisResultEntity(
                        messageId = messageId,
                        threadId = threadId,
                        threatCategory = category.name,
                        confidenceScore = confidence,
                        analyzedAt = System.currentTimeMillis(),
                        matchedRule = matchedRule
                    )
                )
            }
        }
    }

    /**
     * Safely parse threat category string from database.
     * Falls back to SAFE on any parse error.
     */
    private fun parseThreatCategory(value: String): ThreatCategory {
        return try {
            ThreatCategory.valueOf(value)
        } catch (e: IllegalArgumentException) {
            ThreatCategory.SAFE
        }
    }
}

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
import com.smishguard.app.domain.repository.ISmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * SmsRepository.kt — Data Layer Implementation
 * ===============================================
 * This class IMPLEMENTS the ISmsRepository interface. It's the only class
 * in the app that directly touches:
 *   1. Android's SMS Content Provider (to read SMS messages)
 *   2. The Room database (to store analysis results)
 *
 * CONTENT PROVIDER explained:
 *   Android stores SMS messages in a system database. Apps access it through
 *   a "Content Provider" — a standardised API identified by a URI like
 *   "content://sms/inbox". You query it using a ContentResolver, similar
 *   to making a SQL query but through Android's security layer.
 *
 * "withContext(Dispatchers.IO)" switches execution to a background thread
 * optimised for I/O operations (disk, network). This is CRITICAL because
 * reading SMS on the main thread would freeze the UI.
 *
 * "Cursor" is Android's way of iterating over query results — like a
 * database cursor that advances row by row.
 *
 * ".use { }" is a Kotlin extension that automatically CLOSES the resource
 * when the block finishes (like try-with-resources in Java).
 */
class SmsRepository(
    private val context: Context
) : ISmsRepository {
    // ": ISmsRepository" means this class IMPLEMENTS that interface

    private val contentResolver: ContentResolver = context.contentResolver
    private val database: SmishGuardDatabase = SmishGuardDatabase.getInstance(context)
    private val analysisDao = database.analysisResultDao()

    override suspend fun getConversations(): List<SmsConversation> =
        withContext(Dispatchers.IO) {
            val conversations = mutableListOf<SmsConversation>()
            // "mutableListOf" creates an empty list you can add items to.
            // A regular "listOf" would be immutable (read-only).

            // Query the SMS Content Provider for conversation threads
            // This groups messages by thread_id and gets the latest message per thread
            val uri = Telephony.Sms.CONTENT_URI
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )

            // Get distinct thread IDs with their latest messages
            val threadMap = mutableMapOf<Long, SmsConversation>()

            contentResolver.query(
                uri,
                projection,
                null,     // selection (WHERE clause) — null means "all rows"
                null,     // selectionArgs — parameters for the WHERE clause
                "${Telephony.Sms.DATE} DESC"  // ORDER BY date descending
            )?.use { cursor ->
                // "?." safe call — if query returns null, skip the block
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)

                while (cursor.moveToNext()) {
                    val threadId = cursor.getLong(threadIdIndex)

                    if (threadId !in threadMap) {
                        // "!in" means "not in" — first time seeing this thread
                        val address = cursor.getString(addressIndex) ?: "Unknown"
                        val body = cursor.getString(bodyIndex) ?: ""
                        val date = cursor.getLong(dateIndex)

                        // Check if this thread has any fraudulent analysis results
                        val analysisResults = analysisDao.getResultsForThread(threadId)
                        val hasFraudulent = analysisResults.any { it.isFraudulent }
                        // ".any { }" returns true if ANY element matches the condition
                        val maxConfidence = analysisResults
                            .filter { it.isFraudulent }
                            // ".filter { }" keeps only elements matching the condition
                            .maxOfOrNull { it.confidenceScore } ?: 0f
                        // ".maxOfOrNull { }" gets the maximum value, or null if empty

                        threadMap[threadId] = SmsConversation(
                            threadId = threadId,
                            senderAddress = address,
                            senderName = resolveContactName(address),
                            lastMessage = body,
                            lastTimestamp = date,
                            messageCount = 0,  // Will be updated below
                            isFlaggedFraudulent = hasFraudulent,
                            fraudConfidence = maxConfidence
                        )
                    }
                }
            }

            // Count messages per thread
            threadMap.keys.forEach { threadId ->
                val count = countMessagesInThread(threadId)
                threadMap[threadId] = threadMap[threadId]!!.copy(messageCount = count)
                // ".copy()" creates a clone of the data class with specified fields changed
                // "!!" asserts non-null — safe here because we know the key exists
            }

            threadMap.values.sortedByDescending { it.lastTimestamp }
            // ".sortedByDescending { }" returns a new list sorted by the given property
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
                "${Telephony.Sms.THREAD_ID} = ?",   // WHERE clause with placeholder
                arrayOf(threadId.toString()),         // Value for the "?" placeholder
                "${Telephony.Sms.DATE} ASC"           // Oldest first
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (cursor.moveToNext()) {
                    val messageId = cursor.getLong(idIndex)
                    val analysisResult = analysisDao.getResultForMessage(messageId)

                    messages.add(
                        SmsMessage(
                            id = messageId,
                            threadId = threadId,
                            address = cursor.getString(addressIndex) ?: "Unknown",
                            body = cursor.getString(bodyIndex) ?: "",
                            timestamp = cursor.getLong(dateIndex),
                            // Telephony.Sms.MESSAGE_TYPE_INBOX = 1 (incoming message)
                            isIncoming = cursor.getInt(typeIndex) == Telephony.Sms.MESSAGE_TYPE_INBOX,
                            isFlaggedFraudulent = analysisResult?.isFraudulent ?: false,
                            fraudConfidence = analysisResult?.confidenceScore ?: 0f
                        )
                    )
                }
            }

            messages  // Last expression = return value
        }

    override suspend fun saveAnalysisResult(result: AnalysisResult) {
        withContext(Dispatchers.IO) {
            analysisDao.insertResult(
                AnalysisResultEntity(
                    messageId = result.messageId,
                    threadId = result.threadId,
                    isFraudulent = result.isFraudulent,
                    confidenceScore = result.confidenceScore,
                    analyzedAt = result.analyzedAt
                )
            )
        }
    }

    override suspend fun getAnalysisResultsForThread(threadId: Long): List<AnalysisResult> =
        withContext(Dispatchers.IO) {
            analysisDao.getResultsForThread(threadId).map { entity ->
                // ".map { }" transforms each element in the list
                AnalysisResult(
                    messageId = entity.messageId,
                    threadId = entity.threadId,
                    isFraudulent = entity.isFraudulent,
                    confidenceScore = entity.confidenceScore,
                    analyzedAt = entity.analyzedAt
                )
            }
        }

    override suspend fun getAnalysisResultForMessage(messageId: Long): AnalysisResult? =
        withContext(Dispatchers.IO) {
            analysisDao.getResultForMessage(messageId)?.let { entity ->
                // "?.let { }" only executes the block if the value is NOT null
                AnalysisResult(
                    messageId = entity.messageId,
                    threadId = entity.threadId,
                    isFraudulent = entity.isFraudulent,
                    confidenceScore = entity.confidenceScore,
                    analyzedAt = entity.analyzedAt
                )
            }
        }

    /**
     * Resolve a phone number to a contact name.
     * Returns null if the number isn't in the user's contacts.
     *
     * "private" means only this class can call this function.
     */
    private fun resolveContactName(phoneNumber: String): String? {
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
            // If contacts permission isn't granted, return null gracefully
            null
        }
    }

    /**
     * Count the number of messages in a specific thread.
     */
    private fun countMessagesInThread(threadId: Long): Int {
        return contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            null
        )?.use { it.count } ?: 0
        // "it.count" is the number of rows; "?: 0" means default to 0 if null
    }
}

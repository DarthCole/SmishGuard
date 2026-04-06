package com.smishguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smishguard.app.data.local.entity.AnalysisResultEntity

/*
 * AnalysisResultDao.kt — Data Access Object
 * ============================================
 * Defines database operations. Room generates the SQL at compile time.
 * Updated for 3-category threat classification (SAFE / SPAM / FRAUD).
 */
@Dao
interface AnalysisResultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: AnalysisResultEntity)

    @Query("SELECT * FROM analysis_results WHERE thread_id = :threadId")
    suspend fun getResultsForThread(threadId: Long): List<AnalysisResultEntity>

    @Query("SELECT * FROM analysis_results WHERE message_id = :messageId LIMIT 1")
    suspend fun getResultForMessage(messageId: Long): AnalysisResultEntity?

    /** Get all messages flagged as threats (SPAM or FRAUD). */
    @Query("SELECT * FROM analysis_results WHERE threat_category != 'SAFE'")
    suspend fun getAllThreatResults(): List<AnalysisResultEntity>

    /** Get all messages flagged as FRAUD specifically. */
    @Query("SELECT * FROM analysis_results WHERE threat_category = 'FRAUD'")
    suspend fun getAllFraudResults(): List<AnalysisResultEntity>

    /** Get all messages flagged as SPAM specifically. */
    @Query("SELECT * FROM analysis_results WHERE threat_category = 'SPAM'")
    suspend fun getAllSpamResults(): List<AnalysisResultEntity>

    @Query("DELETE FROM analysis_results")
    suspend fun deleteAllResults()
}

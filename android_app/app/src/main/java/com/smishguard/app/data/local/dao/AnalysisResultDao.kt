package com.smishguard.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.smishguard.app.data.local.entity.AnalysisResultEntity

/*
 * AnalysisResultDao.kt — Data Access Object
 * ============================================
 * A "DAO" is an interface that defines HOW to interact with the database.
 * Room generates the actual SQL implementation at compile time.
 *
 * "@Dao" tells Room: "This interface defines database operations."
 * "@Query" lets you write raw SQL that Room validates at compile time.
 * "@Insert" auto-generates an INSERT statement.
 *
 * "suspend fun" = coroutine function — database operations run on a
 * background thread so they don't freeze the UI.
 */
@Dao
interface AnalysisResultDao {

    /**
     * Insert or replace an analysis result.
     * OnConflictStrategy.REPLACE means: if a result for this message_id
     * already exists, overwrite it with the new data.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: AnalysisResultEntity)

    /**
     * Get all analysis results for a specific conversation thread.
     * ":threadId" is a parameter placeholder — Room substitutes the
     * actual value at runtime.
     */
    @Query("SELECT * FROM analysis_results WHERE thread_id = :threadId")
    suspend fun getResultsForThread(threadId: Long): List<AnalysisResultEntity>

    /**
     * Get the analysis result for a specific message.
     * Returns null if the message hasn't been analyzed yet.
     * "LIMIT 1" ensures at most one row is returned.
     */
    @Query("SELECT * FROM analysis_results WHERE message_id = :messageId LIMIT 1")
    suspend fun getResultForMessage(messageId: Long): AnalysisResultEntity?

    /**
     * Get ALL messages that have been flagged as fraudulent.
     */
    @Query("SELECT * FROM analysis_results WHERE is_fraudulent = 1")
    suspend fun getAllFraudulentResults(): List<AnalysisResultEntity>

    /**
     * Delete all analysis results. Useful for a "reset" feature.
     */
    @Query("DELETE FROM analysis_results")
    suspend fun deleteAllResults()
}

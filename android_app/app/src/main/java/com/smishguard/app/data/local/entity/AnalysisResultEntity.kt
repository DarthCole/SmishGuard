package com.smishguard.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * AnalysisResultEntity.kt — Room Database Entity
 * =================================================
 * "@Entity" tells Room: "Create a SQLite table based on this class."
 * Each property with "@ColumnInfo" becomes a column in the table.
 *
 * ANNOTATIONS explained:
 *   @Entity(tableName = "...") — Names the SQLite table
 *   @PrimaryKey — This column uniquely identifies each row
 *   @ColumnInfo(name = "...") — Names the column (optional if same as property)
 *
 * WHY a separate Entity vs the domain model?
 *   The domain model (AnalysisResult) is what the rest of the app uses.
 *   The entity is specific to Room/SQLite. This separation means if we
 *   change the database schema, only this file changes — not the whole app.
 */
@Entity(tableName = "analysis_results")
data class AnalysisResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "is_fraudulent")
    val isFraudulent: Boolean,

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long
)

package com.smishguard.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/*
 * AnalysisResultEntity.kt — Room Database Entity
 * =================================================
 * Stores SmishGuard analysis results in SQLite.
 * 
 * "threat_category" stores the string name of the ThreatCategory enum:
 *   "SAFE", "SPAM", or "FRAUD"
 * We store as String (not Int) for readability in database inspection.
 */
@Entity(tableName = "analysis_results")
data class AnalysisResultEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: Long,

    @ColumnInfo(name = "thread_id")
    val threadId: Long,

    @ColumnInfo(name = "threat_category")
    val threatCategory: String,    // "SAFE", "SPAM", or "FRAUD"

    @ColumnInfo(name = "confidence_score")
    val confidenceScore: Float,

    @ColumnInfo(name = "analyzed_at")
    val analyzedAt: Long,

    @ColumnInfo(name = "matched_rule")
    val matchedRule: String? = null
)

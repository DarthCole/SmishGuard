package com.smishguard.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.smishguard.app.data.local.dao.AnalysisResultDao
import com.smishguard.app.data.local.entity.AnalysisResultEntity

/*
 * SmishGuardDatabase.kt — Room Database
 * ========================================
 * This is the MAIN database class. Room uses it to:
 *   1. Create the SQLite database file on the device
 *   2. Provide access to DAOs (Data Access Objects)
 *
 * "@Database" tells Room:
 *   - entities: which tables exist in this database
 *   - version: the schema version (increment when you change the schema)
 *   - exportSchema: whether to export the schema as a JSON file for migrations
 *
 * "abstract class" means this class CANNOT be instantiated directly.
 * Room generates a concrete implementation at compile time.
 *
 * "companion object" is like Java's "static" — it belongs to the CLASS,
 * not to any instance. We use it here to create a SINGLETON (only one
 * database instance for the entire app).
 *
 * "@Volatile" tells the JVM: "Don't cache this variable in CPU registers —
 * always read it from main memory." This is needed for thread safety.
 *
 * "synchronized" ensures that only ONE thread at a time can execute the
 * block inside it — prevents creating two database instances simultaneously.
 */
@Database(
    entities = [AnalysisResultEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SmishGuardDatabase : RoomDatabase() {

    abstract fun analysisResultDao(): AnalysisResultDao

    companion object {
        @Volatile
        private var INSTANCE: SmishGuardDatabase? = null

        fun getInstance(context: Context): SmishGuardDatabase {
            // "?:" is the ELVIS OPERATOR — if the left side is null,
            // use the right side instead.
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmishGuardDatabase::class.java,
                    "smishguard_database"
                )
                    .fallbackToDestructiveMigration()
                    // ^ If the schema version changes and there's no migration,
                    //   delete and recreate the database. Fine for development;
                    //   in production you'd write proper migrations.
                    .build()
                INSTANCE = instance
                instance
                // In Kotlin, the LAST expression in a block is the return value.
                // So this returns 'instance' from the synchronized block.
            }
        }
    }
}

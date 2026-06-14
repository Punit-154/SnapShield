package com.smssentry.deepcheck.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smssentry.learning.data.PersonalLearningDao
import com.smssentry.learning.data.SenderTrustEntity
import com.smssentry.learning.data.UserFeedbackEntity

@Database(
    entities = [
        AllowlistEntry::class,
        HistoryEntry::class,
        UserFeedbackEntity::class,
        SenderTrustEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DeepCheckDatabase : RoomDatabase() {
    abstract fun allowlistDao(): AllowlistDao
    abstract fun historyDao(): HistoryDao
    abstract fun personalLearningDao(): PersonalLearningDao

    companion object {
        @Volatile
        private var INSTANCE: DeepCheckDatabase? = null

        /**
         * Migration from v1 → v2: adds personal learning tables.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create user_feedback table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_feedback` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `address` TEXT NOT NULL,
                        `body` TEXT NOT NULL,
                        `sms_timestamp` INTEGER NOT NULL,
                        `user_label` TEXT NOT NULL,
                        `ai_prediction` TEXT,
                        `ai_confidence` REAL,
                        `was_corrected` INTEGER NOT NULL DEFAULT 0,
                        `labeled_at` INTEGER NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'USER_FEEDBACK'
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_address` ON `user_feedback` (`address`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_user_label` ON `user_feedback` (`user_label`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_source` ON `user_feedback` (`source`)")

                // Create sender_trust table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `sender_trust` (
                        `address` TEXT NOT NULL PRIMARY KEY,
                        `display_name` TEXT,
                        `safe_count` INTEGER NOT NULL DEFAULT 0,
                        `scam_count` INTEGER NOT NULL DEFAULT 0,
                        `suspicious_count` INTEGER NOT NULL DEFAULT 0,
                        `trust_score` REAL NOT NULL DEFAULT 0.5,
                        `is_known_contact` INTEGER NOT NULL DEFAULT 0,
                        `total_messages` INTEGER NOT NULL DEFAULT 0,
                        `last_updated` INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }

        /**
         * Migration from v2 → v3: privacy hardening.
         * Replaces full SMS body with truncated preview + hash.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with updated schema
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `user_feedback_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `address` TEXT NOT NULL,
                        `body_preview` TEXT NOT NULL DEFAULT '',
                        `body_hash` TEXT NOT NULL DEFAULT '',
                        `sms_timestamp` INTEGER NOT NULL,
                        `user_label` TEXT NOT NULL,
                        `ai_prediction` TEXT,
                        `ai_confidence` REAL,
                        `was_corrected` INTEGER NOT NULL DEFAULT 0,
                        `labeled_at` INTEGER NOT NULL,
                        `source` TEXT NOT NULL DEFAULT 'USER_FEEDBACK'
                    )
                """.trimIndent())

                // Copy existing data, truncating body to 50 chars as preview
                db.execSQL("""
                    INSERT INTO `user_feedback_new` 
                        (`id`, `address`, `body_preview`, `body_hash`, `sms_timestamp`, 
                         `user_label`, `ai_prediction`, `ai_confidence`, `was_corrected`,
                         `labeled_at`, `source`)
                    SELECT `id`, `address`, SUBSTR(`body`, 1, 50), '', `sms_timestamp`,
                           `user_label`, `ai_prediction`, `ai_confidence`, `was_corrected`,
                           `labeled_at`, `source`
                    FROM `user_feedback`
                """.trimIndent())

                // Swap tables
                db.execSQL("DROP TABLE `user_feedback`")
                db.execSQL("ALTER TABLE `user_feedback_new` RENAME TO `user_feedback`")

                // Recreate indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_address` ON `user_feedback` (`address`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_user_label` ON `user_feedback` (`user_label`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_feedback_source` ON `user_feedback` (`source`)")
            }
        }

        fun getInstance(context: Context): DeepCheckDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeepCheckDatabase::class.java,
                    "deepcheck.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

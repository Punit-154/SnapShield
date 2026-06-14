package com.smssentry.deepcheck.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smssentry.data.security.DatabaseKeyManager
import com.smssentry.learning.data.PersonalLearningDao
import com.smssentry.learning.data.SenderTrustEntity
import com.smssentry.learning.data.UserFeedbackEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

        private const val TAG = "DeepCheckDatabase"

        fun getInstance(context: Context): DeepCheckDatabase {
            return INSTANCE ?: synchronized(this) {
                // Load native SQLCipher library
                System.loadLibrary("sqlcipher")

                // Get passphrase from Android Keystore
                val passphrase = DatabaseKeyManager.getOrCreatePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)

                // Migrate unencrypted DB to encrypted if needed
                migrateToEncrypted(context, passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DeepCheckDatabase::class.java,
                    "deepcheck.db"
                )
                    .openHelperFactory(factory)
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * If an unencrypted database exists, encrypt it in-place using SQLCipher.
         * This is a one-time migration on upgrade.
         */
        private fun migrateToEncrypted(context: Context, passphrase: ByteArray) {
            val dbFile = context.getDatabasePath("deepcheck.db")
            if (!dbFile.exists()) return

            // Check if DB is already encrypted by trying to open it without a key
            try {
                val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
                )
                // If we get here, the DB is NOT encrypted — migrate it
                db.close()

                Log.i(TAG, "Migrating unencrypted database to SQLCipher...")
                val tempFile = context.getDatabasePath("deepcheck_encrypted.db")
                if (tempFile.exists()) tempFile.delete()

                val encDb = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                    tempFile.absolutePath, passphrase, null, null, null
                )
                encDb.rawExecSQL(
                    "ATTACH DATABASE '${dbFile.absolutePath}' AS plaintext KEY ''"
                )
                encDb.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
                encDb.rawExecSQL("DETACH DATABASE plaintext")
                encDb.close()

                // Swap files
                dbFile.delete()
                tempFile.renameTo(dbFile)
                Log.i(TAG, "Database encryption migration complete")
            } catch (_: Exception) {
                // DB is already encrypted or doesn't exist — nothing to do
            }
        }
    }
}

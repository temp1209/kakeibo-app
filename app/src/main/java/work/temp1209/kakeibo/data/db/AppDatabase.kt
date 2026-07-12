package work.temp1209.kakeibo.data.db

import android.content.Context
import androidx.room.migration.Migration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ReceiptEntity::class,
        ReceiptImageEntity::class,
        ReceiptItemEntity::class,
        GeminiResultEntity::class,
        AnalysisQueueEntity::class,
        AnalysisNotificationEventEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // receipts: add nullable receiptDatetime
                db.execSQL("ALTER TABLE receipts ADD COLUMN receiptDatetime TEXT")

                // receipt_images: add retentionUntil (non-null) and deletedAt (nullable)
                // For existing rows, keep them indefinitely (far future) until we have enough metadata.
                db.execSQL("ALTER TABLE receipt_images ADD COLUMN retentionUntil TEXT NOT NULL DEFAULT '9999-12-31T00:00:00Z'")
                db.execSQL("ALTER TABLE receipt_images ADD COLUMN deletedAt TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // receipts: Phase2 fields (nullable where possible, defaults for non-null)
                db.execSQL("ALTER TABLE receipts ADD COLUMN merchantName TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN totalAmountYen INTEGER")
                db.execSQL("ALTER TABLE receipts ADD COLUMN paymentMethod TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN paymentServiceName TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN analysisStartedAt TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN analysisCompletedAt TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN analysisErrorMessage TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN needsReview INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE receipts ADD COLUMN itemsSubtotalYen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE receipts ADD COLUMN adjustmentYen INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE receipts ADD COLUMN deletedAt TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN deleteReason TEXT")
                db.execSQL("ALTER TABLE receipts ADD COLUMN backupRevision INTEGER NOT NULL DEFAULT 0")

                // receipt_items
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS receipt_items (
                      itemId TEXT NOT NULL PRIMARY KEY,
                      receiptId TEXT NOT NULL,
                      lineIndex INTEGER NOT NULL,
                      itemName TEXT NOT NULL,
                      quantity INTEGER NOT NULL,
                      lineTotalYen INTEGER NOT NULL,
                      categoryMajor TEXT NOT NULL,
                      categoryMinor TEXT NOT NULL,
                      necessityScore INTEGER NOT NULL,
                      confidence REAL NOT NULL,
                      isAdjustment INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_receiptId ON receipt_items(receiptId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_receipt_items_receiptId_lineIndex ON receipt_items(receiptId, lineIndex)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_categoryMajor_categoryMinor ON receipt_items(categoryMajor, categoryMinor)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_receipt_items_necessityScore ON receipt_items(necessityScore)")

                // gemini_results
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gemini_results (
                      resultId TEXT NOT NULL PRIMARY KEY,
                      receiptId TEXT NOT NULL,
                      schemaVersion TEXT NOT NULL,
                      model TEXT NOT NULL,
                      rawJson TEXT NOT NULL,
                      createdAt TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gemini_results_receiptId ON gemini_results(receiptId)")

                // analysis_queue
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS analysis_queue (
                      queueId TEXT NOT NULL PRIMARY KEY,
                      receiptId TEXT NOT NULL,
                      status TEXT NOT NULL,
                      attemptCount INTEGER NOT NULL,
                      lastError TEXT,
                      queuedAt TEXT NOT NULL,
                      startedAt TEXT,
                      finishedAt TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_analysis_queue_receiptId ON analysis_queue(receiptId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_analysis_queue_status_queuedAt ON analysis_queue(status, queuedAt)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE receipts ADD COLUMN inputKind TEXT NOT NULL DEFAULT 'RECEIPT_CAMERA'",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS analysis_notification_events (
                      eventId TEXT NOT NULL PRIMARY KEY,
                      receiptId TEXT NOT NULL,
                      eventType TEXT NOT NULL,
                      occurredAt TEXT NOT NULL,
                      merchantName TEXT,
                      totalAmountYen INTEGER
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_analysis_notification_events_occurredAt " +
                        "ON analysis_notification_events(occurredAt)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_analysis_notification_events_receiptId " +
                        "ON analysis_notification_events(receiptId)",
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kakeibo.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}


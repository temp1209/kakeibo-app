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
    ],
    version = 2,
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

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kakeibo.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}


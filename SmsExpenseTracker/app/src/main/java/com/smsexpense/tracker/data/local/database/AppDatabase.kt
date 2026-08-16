package com.smsexpense.tracker.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsexpense.tracker.data.local.dao.CategoryDao
import com.smsexpense.tracker.data.local.dao.ImportHistoryDao
import com.smsexpense.tracker.data.local.dao.PaymentDao
import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.data.local.entity.ImportHistoryEntity
import com.smsexpense.tracker.data.local.entity.PaymentEntity

@Database(
    entities = [PaymentEntity::class, CategoryEntity::class, ImportHistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importHistoryDao(): ImportHistoryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 → v2: payments.source column + import_history table. No data loss. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE payments ADD COLUMN source TEXT NOT NULL DEFAULT 'SMS_REALTIME'"
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS import_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        importedAt INTEGER NOT NULL,
                        fromDate INTEGER NOT NULL,
                        toDate INTEGER NOT NULL,
                        transactionCount INTEGER NOT NULL,
                        totalAmount REAL NOT NULL,
                        currency TEXT NOT NULL
                    )"""
                )
            }
        }

        /** v2 → v3: categories.parentId for the two-level tree. Existing rows stay roots. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN parentId INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories (parentId)")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_expense.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}

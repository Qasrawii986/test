package com.smsexpense.tracker.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.smsexpense.tracker.data.local.dao.AllocationDao
import com.smsexpense.tracker.data.local.dao.CategoryDao
import com.smsexpense.tracker.data.local.dao.ImportHistoryDao
import com.smsexpense.tracker.data.local.dao.PaymentDao
import com.smsexpense.tracker.data.local.dao.PayerDao
import com.smsexpense.tracker.data.local.entity.AllocationEntity
import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.data.local.entity.ImportHistoryEntity
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.data.local.entity.PayerEntity

@Database(
    entities = [
        PaymentEntity::class,
        CategoryEntity::class,
        ImportHistoryEntity::class,
        PayerEntity::class,
        AllocationEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paymentDao(): PaymentDao
    abstract fun categoryDao(): CategoryDao
    abstract fun importHistoryDao(): ImportHistoryDao
    abstract fun payerDao(): PayerDao
    abstract fun allocationDao(): AllocationDao

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

        /**
         * v3 → v4: expense distribution. Adds the payers list and the allocations
         * that charge part of a payment to someone else.
         *
         * No backfill: an allocation only ever records a charge to *another* payer,
         * so every payment that already exists keeps its full amount as your own
         * share automatically. Nothing to rewrite, nothing to get wrong.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `payers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `emoji` TEXT NOT NULL,
                        `color` INTEGER,
                        `isSelf` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `allocations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `paymentId` INTEGER NOT NULL,
                        `payerId` INTEGER NOT NULL,
                        `amount` REAL NOT NULL,
                        `settled` INTEGER NOT NULL,
                        FOREIGN KEY(`paymentId`) REFERENCES `payments`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE ,
                        FOREIGN KEY(`payerId`) REFERENCES `payers`(`id`)
                            ON UPDATE NO ACTION ON DELETE CASCADE )"""
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_allocations_paymentId` ON `allocations` (`paymentId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_allocations_payerId` ON `allocations` (`payerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_allocations_settled` ON `allocations` (`settled`)")
            }
        }

        val MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sms_expense.db",
                )
                    .addMigrations(*MIGRATIONS)
                    .build()
                    .also { instance = it }
            }
    }
}

package com.smsexpense.tracker.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.local.database.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Real migration coverage: build a database at the previous schema version with
 * data in it, run the app's migrations, and assert that nothing was lost.
 * Migrations are exactly where user data disappears, so this is not optional.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates the v2 schema by hand and seeds it. */
    private fun createV2WithData() {
        val callback = object : SupportSQLiteOpenHelper.Callback(2) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        amount REAL NOT NULL, currency TEXT NOT NULL, merchant TEXT,
                        categoryId INTEGER, sender TEXT NOT NULL, originalMessage TEXT NOT NULL,
                        timestamp INTEGER NOT NULL, status TEXT NOT NULL, syncStatus TEXT NOT NULL,
                        confidence REAL NOT NULL, dedupKey TEXT NOT NULL, createdAt INTEGER NOT NULL,
                        source TEXT NOT NULL DEFAULT 'SMS_REALTIME')"""
                )
                db.execSQL("CREATE UNIQUE INDEX index_payments_dedupKey ON payments (dedupKey)")
                db.execSQL("CREATE INDEX index_payments_timestamp ON payments (timestamp)")
                db.execSQL("CREATE INDEX index_payments_categoryId ON payments (categoryId)")
                db.execSQL("CREATE INDEX index_payments_status ON payments (status)")
                db.execSQL("CREATE INDEX index_payments_syncStatus ON payments (syncStatus)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS categories (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER,
                        sortOrder INTEGER NOT NULL, createdAt INTEGER NOT NULL)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS import_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        importedAt INTEGER NOT NULL, fromDate INTEGER NOT NULL,
                        toDate INTEGER NOT NULL, transactionCount INTEGER NOT NULL,
                        totalAmount REAL NOT NULL, currency TEXT NOT NULL)"""
                )
                db.execSQL(
                    "INSERT INTO categories (name, icon, color, sortOrder, createdAt) " +
                        "VALUES ('Food', '🍔', NULL, 0, 111)"
                )
                db.execSQL(
                    """INSERT INTO payments
                       (amount, currency, merchant, categoryId, sender, originalMessage, timestamp,
                        status, syncStatus, confidence, dedupKey, createdAt, source)
                       VALUES (13.0, 'JOD', 'Abdulraheem Rizk', 1, 'MYBANK', 'cliq msg', 999,
                        'CATEGORIZED', 'PENDING', 1.0, 'key-1', 999, 'SMS_HISTORICAL')"""
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(callback)
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase.close()
    }

    @Test
    fun `migrating v2 to v3 keeps payments and categories and adds parentId`() = runTest {
        createV2WithData()

        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

        // Existing rows survived.
        val categories = db.categoryDao().getAll()
        assertEquals(1, categories.size)
        assertEquals("Food", categories.single().name)
        // Pre-existing categories become root categories.
        assertNull(categories.single().parentId)

        val payment = db.paymentDao().getById(1)
        assertEquals(13.0, payment!!.amount, 0.0001)
        assertEquals("Abdulraheem Rizk", payment.merchant)
        assertEquals("SMS_HISTORICAL", payment.source)
        assertEquals(1L, payment.categoryId)

        // The new column is usable: add a subcategory under the migrated root.
        val childId = db.categoryDao().insert(
            com.smsexpense.tracker.data.local.entity.CategoryEntity(
                name = "Groceries", icon = "🛒", color = null,
                sortOrder = 0, createdAt = 222, parentId = categories.single().id,
            )
        )
        assertTrue(childId > 0)
        assertEquals(1, db.categoryDao().childrenOf(categories.single().id).size)

        db.close()
    }
}

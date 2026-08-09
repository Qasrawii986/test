package com.smsexpense.tracker.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.local.database.AppDatabase
import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.data.repository.RoomPaymentRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class PaymentDaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun epochOf(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun payment(
        amount: Double = 10.0,
        timestamp: Long = epochOf(2026, 8, 9),
        dedupKey: String = "key-${System.nanoTime()}",
        categoryId: Long? = null,
        status: String = "UNCATEGORIZED",
        syncStatus: String = "PENDING",
    ) = PaymentEntity(
        amount = amount,
        currency = "JOD",
        merchant = "Shop",
        categoryId = categoryId,
        sender = "MYBANK",
        originalMessage = "msg",
        timestamp = timestamp,
        status = status,
        syncStatus = syncStatus,
        confidence = 1f,
        dedupKey = dedupKey,
        createdAt = timestamp,
    )

    @Test
    fun `insert and read back`() = runTest {
        val id = db.paymentDao().insertIgnoring(payment())
        assertTrue(id > 0)
        assertEquals(10.0, db.paymentDao().getById(id)!!.amount, 0.0001)
    }

    @Test
    fun `duplicate dedupKey is ignored`() = runTest {
        val first = db.paymentDao().insertIgnoring(payment(dedupKey = "same"))
        val second = db.paymentDao().insertIgnoring(payment(dedupKey = "same"))
        assertTrue(first > 0)
        assertEquals(-1L, second)
    }

    @Test
    fun `update category and status`() = runTest {
        val catId = db.categoryDao().insert(
            CategoryEntity(name = "Food", icon = "🍔", color = null, sortOrder = 0, createdAt = 0)
        )
        val id = db.paymentDao().insertIgnoring(payment())
        db.paymentDao().categorize(id, catId, "PENDING")
        val updated = db.paymentDao().getById(id)!!
        assertEquals(catId, updated.categoryId)
        assertEquals("CATEGORIZED", updated.status)
    }

    @Test
    fun `delete removes the row`() = runTest {
        val id = db.paymentDao().insertIgnoring(payment())
        db.paymentDao().delete(id)
        assertEquals(null, db.paymentDao().getById(id))
    }

    @Test
    fun `monthly total only counts the selected month`() = runTest {
        db.paymentDao().insertIgnoring(payment(amount = 10.0, timestamp = epochOf(2026, 8, 1)))
        db.paymentDao().insertIgnoring(payment(amount = 20.0, timestamp = epochOf(2026, 8, 31, 23)))
        db.paymentDao().insertIgnoring(payment(amount = 99.0, timestamp = epochOf(2026, 7, 31)))
        db.paymentDao().insertIgnoring(payment(amount = 50.0, timestamp = epochOf(2026, 9, 1, 0)))

        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        assertEquals(30.0, db.paymentDao().observeTotalBetween(from, to).first(), 0.0001)
        assertEquals(2, db.paymentDao().observeCountBetween(from, to).first())
    }

    @Test
    fun `category totals group correctly`() = runTest {
        val food = db.categoryDao().insert(
            CategoryEntity(name = "Food", icon = "🍔", color = null, sortOrder = 0, createdAt = 0)
        )
        val transport = db.categoryDao().insert(
            CategoryEntity(name = "Transport", icon = "🚗", color = null, sortOrder = 1, createdAt = 0)
        )
        db.paymentDao().insertIgnoring(payment(amount = 10.0, categoryId = food))
        db.paymentDao().insertIgnoring(payment(amount = 15.0, categoryId = food))
        db.paymentDao().insertIgnoring(payment(amount = 7.0, categoryId = transport))
        db.paymentDao().insertIgnoring(payment(amount = 3.0, categoryId = null))

        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        val totals = db.paymentDao().observeCategoryTotalsBetween(from, to).first()

        assertEquals(25.0, totals.first { it.categoryId == food }.total, 0.0001)
        assertEquals(2, totals.first { it.categoryId == food }.count)
        assertEquals(7.0, totals.first { it.categoryId == transport }.total, 0.0001)
        assertEquals(3.0, totals.first { it.categoryId == null }.total, 0.0001)
    }

    @Test
    fun `pending sync returns PENDING and FAILED only`() = runTest {
        db.paymentDao().insertIgnoring(payment(syncStatus = "PENDING"))
        db.paymentDao().insertIgnoring(payment(syncStatus = "FAILED"))
        db.paymentDao().insertIgnoring(payment(syncStatus = "SYNCED"))
        assertEquals(2, db.paymentDao().pendingSync().size)
    }

    @Test
    fun `clearAll empties the table`() = runTest {
        db.paymentDao().insertIgnoring(payment())
        db.paymentDao().clearAll()
        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        assertEquals(0, db.paymentDao().observeCountBetween(from, to).first())
    }
}

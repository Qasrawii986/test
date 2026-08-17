package com.smsexpense.tracker.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.local.database.AppDatabase
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.data.repository.RoomPaymentRepository
import com.smsexpense.tracker.data.repository.RoomSplitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class SplitRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomSplitRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = RoomSplitRepository(db.payerDao(), db.allocationDao()) { 0L }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun epochOf(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 12, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private suspend fun insertPayment(
        amount: Double = 100.0,
        timestamp: Long = epochOf(2026, 8, 10),
        categoryId: Long? = null,
    ): Long = db.paymentDao().insertIgnoring(
        PaymentEntity(
            amount = amount, currency = "JOD", merchant = "Shop", categoryId = categoryId,
            sender = "MYBANK", originalMessage = "msg", timestamp = timestamp,
            status = "UNCATEGORIZED", syncStatus = "PENDING", confidence = 1f,
            dedupKey = "key-${System.nanoTime()}", createdAt = timestamp,
        )
    )

    @Test
    fun `seeding the self payer is idempotent`() = runTest {
        repo.seedSelfIfEmpty()
        repo.seedSelfIfEmpty()
        val payers = repo.getPayers()
        assertEquals(1, payers.size)
        assertTrue(payers.single().isSelf)
        assertNotNull(repo.selfPayerId())
    }

    @Test
    fun `charging a whole payment to someone makes your share zero`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)

        repo.chargeWholePayment(paymentId, dad, 100.0)

        val split = repo.getSplit(paymentId, 100.0, "JOD")
        assertEquals(100.0, split.chargedToOthers, 0.0001)
        assertEquals(0.0, split.myShare, 0.0001)
    }

    @Test
    fun `charging back to yourself clears the allocations`() = runTest {
        repo.seedSelfIfEmpty()
        val self = repo.selfPayerId()!!
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.chargeWholePayment(paymentId, dad, 100.0)

        repo.chargeWholePayment(paymentId, self, 100.0)

        assertTrue(repo.getSplit(paymentId, 100.0, "JOD").isFullyMine)
    }

    @Test
    fun `the self payer is never stored as an allocation`() = runTest {
        repo.seedSelfIfEmpty()
        val self = repo.selfPayerId()!!
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 90.0)

        repo.setSplit(paymentId, mapOf(self to 30.0, dad to 60.0))

        val split = repo.getSplit(paymentId, 90.0, "JOD")
        assertEquals(1, split.allocations.size)
        assertEquals(dad, split.allocations.single().payerId)
        // Your 30 is implied by the remainder, not stored.
        assertEquals(30.0, split.myShare, 0.0001)
    }

    @Test
    fun `zero and negative amounts are dropped`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val mum = repo.addPayer("Mum", "👩", null)
        val paymentId = insertPayment(amount = 50.0)

        repo.setSplit(paymentId, mapOf(dad to 0.0, mum to -10.0))

        assertTrue(repo.getSplit(paymentId, 50.0, "JOD").isFullyMine)
    }

    @Test
    fun `settling clears what you are owed without changing your share`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 70.0))

        assertEquals(70.0, repo.observeOwedAllTime().first().single().amount, 0.0001)

        repo.settleAllFor(dad)

        assertTrue(repo.observeOwedAllTime().first().isEmpty())
        // Being paid back does not turn his 70 into your expense.
        assertEquals(30.0, repo.getSplit(paymentId, 100.0, "JOD").myShare, 0.0001)
        assertEquals(70.0, repo.observeChargedToOthersBetween(0L, Long.MAX_VALUE).first(), 0.0001)
    }

    @Test
    fun `reopening a settled charge brings the debt back`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 70.0))
        repo.settleAllFor(dad)

        repo.unsettleAllFor(dad)

        assertEquals(70.0, repo.observeOwedAllTime().first().single().amount, 0.0001)
    }

    @Test
    fun `re-saving the same amount keeps a settled charge settled`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val mum = repo.addPayer("Mum", "👩", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 40.0))
        repo.settleAllFor(dad)

        // Editing someone else's share must not silently re-open Dad's paid debt.
        repo.setSplit(paymentId, mapOf(dad to 40.0, mum to 20.0))

        val split = repo.getSplit(paymentId, 100.0, "JOD")
        assertTrue(split.allocations.first { it.payerId == dad }.settled)
        assertFalse(split.allocations.first { it.payerId == mum }.settled)
        assertEquals(20.0, repo.observeOwedAllTime().first().single().amount, 0.0001)
    }

    @Test
    fun `changing the amount re-opens that charge`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 40.0))
        repo.settleAllFor(dad)

        repo.setSplit(paymentId, mapOf(dad to 55.0))

        assertFalse(repo.getSplit(paymentId, 100.0, "JOD").allocations.single().settled)
    }

    @Test
    fun `owed totals are scoped to the requested period`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val august = insertPayment(amount = 100.0, timestamp = epochOf(2026, 8, 10))
        val july = insertPayment(amount = 40.0, timestamp = epochOf(2026, 7, 10))
        repo.setSplit(august, mapOf(dad to 60.0))
        repo.setSplit(july, mapOf(dad to 40.0))

        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        assertEquals(60.0, repo.observeOwedBetween(from, to).first().single().amount, 0.0001)
        assertEquals(60.0, repo.observeChargedToOthersBetween(from, to).first(), 0.0001)
        // All time still counts both months.
        assertEquals(100.0, repo.observeOwedAllTime().first().single().amount, 0.0001)
    }

    @Test
    fun `charged amounts are reported per category`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val bills = db.categoryDao().insert(
            com.smsexpense.tracker.data.local.entity.CategoryEntity(
                name = "Bills", icon = "🧾", color = null, sortOrder = 0, createdAt = 0,
            )
        )
        val billPayment = insertPayment(amount = 100.0, categoryId = bills)
        val ownPayment = insertPayment(amount = 20.0, categoryId = bills)
        repo.setSplit(billPayment, mapOf(dad to 100.0))

        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        val perCategory = repo.observeChargedToOthersByCategoryBetween(from, to).first()
        assertEquals(100.0, perCategory.single { it.categoryId == bills }.total, 0.0001)
        // The unsplit payment contributes nothing to the charged-to-others figure.
        assertTrue(repo.getSplit(ownPayment, 20.0, "JOD").isFullyMine)
    }

    @Test
    fun `deleting a payer returns their expenses to you`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 100.0))
        assertEquals(1, repo.chargedCount(dad))

        repo.deletePayer(dad)

        assertTrue(repo.getSplit(paymentId, 100.0, "JOD").isFullyMine)
        // The payment itself is untouched.
        assertEquals(100.0, db.paymentDao().getById(paymentId)!!.amount, 0.0001)
    }

    @Test
    fun `the self payer cannot be deleted`() = runTest {
        repo.seedSelfIfEmpty()
        val self = repo.selfPayerId()!!

        repo.deletePayer(self)

        assertEquals(self, repo.selfPayerId())
    }

    @Test
    fun `deleting a payment removes its allocations`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val paymentId = insertPayment(amount = 100.0)
        repo.setSplit(paymentId, mapOf(dad to 100.0))

        db.paymentDao().delete(paymentId)

        assertTrue(repo.observeOwedAllTime().first().isEmpty())
        assertEquals(0, repo.chargedCount(dad))
    }

    @Test
    fun `shared payments are identifiable for the list badge`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val shared = insertPayment(amount = 100.0)
        val mine = insertPayment(amount = 30.0)
        repo.setSplit(shared, mapOf(dad to 50.0))

        val (from, to) = RoomPaymentRepository.monthBounds(2026, 8)
        val ids = repo.observeSharedPaymentIdsBetween(from, to).first()
        assertEquals(setOf(shared), ids)
        assertFalse(mine in ids)
    }

    @Test
    fun `updating a payer keeps it a non-self payer`() = runTest {
        repo.seedSelfIfEmpty()
        val dad = repo.addPayer("Dad", "👨", null)
        val payer = repo.getPayers().first { it.id == dad }

        repo.updatePayer(payer.copy(name = "Baba", emoji = "🧔", isSelf = true))

        val updated = repo.getPayers().first { it.id == dad }
        assertEquals("Baba", updated.name)
        assertFalse(updated.isSelf)
        // Still exactly one self payer.
        assertEquals(1, repo.getPayers().count { it.isSelf })
    }
}

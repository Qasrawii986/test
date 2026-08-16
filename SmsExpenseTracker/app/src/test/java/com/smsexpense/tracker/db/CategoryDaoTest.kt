package com.smsexpense.tracker.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.local.database.AppDatabase
import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.data.repository.RoomCategoryRepository
import com.smsexpense.tracker.domain.model.toTree
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CategoryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: RoomCategoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCategoryRepository(db.categoryDao(), db.paymentDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `insert update delete`() = runTest {
        val id = db.categoryDao().insert(
            CategoryEntity(name = "Food", icon = "🍔", color = null, sortOrder = 0, createdAt = 0)
        )
        db.categoryDao().update(
            CategoryEntity(id = id, name = "Groceries", icon = "🥦", color = null, sortOrder = 0, createdAt = 0)
        )
        assertEquals("Groceries", db.categoryDao().getById(id)!!.name)
        db.categoryDao().delete(id)
        assertEquals(null, db.categoryDao().getById(id))
    }

    @Test
    fun `seed defaults only once`() = runTest {
        repository.seedDefaultsIfEmpty()
        val countAfterFirst = db.categoryDao().count()
        repository.seedDefaultsIfEmpty()
        assertEquals(countAfterFirst, db.categoryDao().count())
    }

    @Test
    fun `move swaps ordering among root categories`() = runTest {
        repository.seedDefaultsIfEmpty()
        // getAll() is now a flat list of roots and subcategories; ordering applies
        // within each sibling group, so compare roots.
        val before = repository.getAll().toTree().map { it.category }
        val second = before[1]
        repository.move(second.id, up = true)
        val after = repository.getAll().toTree().map { it.category }
        assertEquals(second.id, after[0].id)
        assertEquals(before[0].id, after[1].id)
    }

    @Test
    fun `deleting a category clears references from payments`() = runTest {
        val id = repository.add("Food", "🍔", null)
        val paymentId = db.paymentDao().insertIgnoring(
            com.smsexpense.tracker.data.local.entity.PaymentEntity(
                amount = 5.0, currency = "JOD", merchant = null, categoryId = id,
                sender = "MYBANK", originalMessage = "m", timestamp = 0,
                status = "CATEGORIZED", syncStatus = "PENDING", confidence = 1f,
                dedupKey = "k1", createdAt = 0,
            )
        )
        repository.delete(id)
        assertEquals(null, db.paymentDao().getById(paymentId)!!.categoryId)
        assertEquals(0, db.categoryDao().observeAll().first().size)
    }
}

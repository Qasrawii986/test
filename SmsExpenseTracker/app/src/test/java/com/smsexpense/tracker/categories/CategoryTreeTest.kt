package com.smsexpense.tracker.categories

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.smsexpense.tracker.data.local.database.AppDatabase
import com.smsexpense.tracker.data.local.entity.PaymentEntity
import com.smsexpense.tracker.data.repository.RoomCategoryRepository
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.rootIdOf
import com.smsexpense.tracker.domain.model.toTree
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

class CategoryTreePureTest {

    private fun category(id: Long, name: String, parentId: Long? = null) =
        Category(id, name, "x", null, 0, 0, parentId)

    @Test
    fun `toTree nests children under their root`() {
        val tree = listOf(
            category(1, "Food"),
            category(2, "Groceries", parentId = 1),
            category(3, "Restaurants", parentId = 1),
            category(4, "Transport"),
        ).toTree()

        assertEquals(2, tree.size)
        assertEquals("Food", tree[0].category.name)
        assertEquals(listOf("Groceries", "Restaurants"), tree[0].children.map { it.name })
        assertTrue(tree[0].hasChildren)
        assertTrue(!tree[1].hasChildren)
    }

    @Test
    fun `rootIdOf maps children to their root and roots to themselves`() {
        val roots = listOf(
            category(1, "Food"),
            category(2, "Groceries", parentId = 1),
            category(4, "Transport"),
        ).rootIdOf()

        assertEquals(1L, roots[1])
        assertEquals(1L, roots[2]) // child rolls up
        assertEquals(4L, roots[4])
    }

    @Test
    fun `orphaned child falls back to itself rather than vanishing from reports`() {
        val roots = listOf(category(5, "Ghost", parentId = 99)).rootIdOf()
        assertEquals(5L, roots[5])
    }
}

@RunWith(RobolectricTestRunner::class)
class CategoryRepositoryTreeTest {

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
    fun tearDown() = db.close()

    @Test
    fun `subcategory is stored under its parent`() = runTest {
        val food = repository.add("Food", "🍔", null)
        val groceries = repository.add("Groceries", "🛒", null, parentId = food)

        val all = repository.getAll()
        assertEquals(food, all.first { it.id == groceries }.parentId)
        assertEquals(1, all.toTree().single { it.category.id == food }.children.size)
    }

    @Test
    fun `tree stays two levels - attaching to a child reparents to its root`() = runTest {
        val food = repository.add("Food", "🍔", null)
        val groceries = repository.add("Groceries", "🛒", null, parentId = food)
        val deep = repository.add("Fruit", "🍎", null, parentId = groceries)

        // "Fruit" must become a sibling of Groceries, not a third level.
        assertEquals(food, repository.getById(deep)!!.parentId)
        assertEquals(2, repository.getAll().toTree().single().children.size)
    }

    @Test
    fun `deleting a root removes its children and unlinks their payments`() = runTest {
        val food = repository.add("Food", "🍔", null)
        val groceries = repository.add("Groceries", "🛒", null, parentId = food)
        val paymentId = db.paymentDao().insertIgnoring(
            PaymentEntity(
                amount = 5.0, currency = "JOD", merchant = null, categoryId = groceries,
                sender = "MYBANK", originalMessage = "m", timestamp = 0,
                status = "CATEGORIZED", syncStatus = "PENDING", confidence = 1f,
                dedupKey = "k1", createdAt = 0,
            )
        )

        repository.delete(food)

        assertTrue(repository.getAll().isEmpty())
        // The payment survives; it just loses its category link.
        assertNull(db.paymentDao().getById(paymentId)!!.categoryId)
    }

    @Test
    fun `deleting a subcategory leaves its parent intact`() = runTest {
        val food = repository.add("Food", "🍔", null)
        val groceries = repository.add("Groceries", "🛒", null, parentId = food)

        repository.delete(groceries)

        val all = repository.getAll()
        assertEquals(1, all.size)
        assertEquals(food, all.single().id)
    }

    @Test
    fun `move reorders within the sibling group only`() = runTest {
        val food = repository.add("Food", "🍔", null)
        val transport = repository.add("Transport", "🚗", null)
        val a = repository.add("A", "1", null, parentId = food)
        val b = repository.add("B", "2", null, parentId = food)

        repository.move(b, up = true)

        val children = repository.getAll().toTree().single { it.category.id == food }.children
        assertEquals(listOf(b, a), children.map { it.id })
        // Roots untouched.
        assertEquals(listOf(food, transport), repository.getAll().toTree().map { it.category.id })
    }

    @Test
    fun `seeded defaults include subcategories`() = runTest {
        repository.seedDefaultsIfEmpty()
        val tree = repository.getAll().toTree()
        assertEquals(5, tree.size)
        assertEquals(listOf("Groceries", "Restaurants"), tree.first().children.map { it.name })
    }
}

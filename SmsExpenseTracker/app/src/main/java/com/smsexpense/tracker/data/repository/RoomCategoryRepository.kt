package com.smsexpense.tracker.data.repository

import com.smsexpense.tracker.data.local.dao.CategoryDao
import com.smsexpense.tracker.data.local.dao.PaymentDao
import com.smsexpense.tracker.data.local.entity.CategoryEntity
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(
    private val categoryDao: CategoryDao,
    private val paymentDao: PaymentDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : CategoryRepository {

    override fun observeAll(): Flow<List<Category>> =
        categoryDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Category> = categoryDao.getAll().map { it.toDomain() }

    override suspend fun getById(id: Long): Category? = categoryDao.getById(id)?.toDomain()

    override suspend fun add(name: String, icon: String, color: Long?): Long =
        categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                icon = icon,
                color = color,
                sortOrder = categoryDao.maxSortOrder() + 1,
                createdAt = clock(),
            )
        )

    override suspend fun update(category: Category) {
        categoryDao.update(
            CategoryEntity(
                id = category.id,
                name = category.name.trim(),
                icon = category.icon,
                color = category.color,
                sortOrder = category.sortOrder,
                createdAt = category.createdAt,
            )
        )
    }

    override suspend fun delete(id: Long) {
        // Payments referencing the deleted category fall back to uncategorized links
        // (categoryId NULL) but keep their CATEGORIZED status history intact.
        paymentDao.clearCategoryRefs(id)
        categoryDao.delete(id)
    }

    override suspend fun move(id: Long, up: Boolean) {
        val all = categoryDao.getAll()
        val index = all.indexOfFirst { it.id == id }
        if (index == -1) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in all.indices) return
        val a = all[index]
        val b = all[swapWith]
        // Normalize orders first so ties (fresh installs) still swap deterministically.
        all.forEachIndexed { i, e ->
            val desired = when (i) {
                index -> swapWith
                swapWith -> index
                else -> i
            }
            if (e.sortOrder != desired) categoryDao.update(e.copy(sortOrder = desired))
        }
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (categoryDao.count() > 0) return
        val defaults = listOf(
            Triple("Food", "🍔", 0xFFEF6C00),
            Triple("Transport", "🚗", 0xFF1565C0),
            Triple("Shopping", "🛍️", 0xFF6A1B9A),
            Triple("Bills", "🧾", 0xFF00695C),
            Triple("Other", "📦", 0xFF546E7A),
        )
        defaults.forEachIndexed { i, (name, icon, color) ->
            categoryDao.insert(
                CategoryEntity(name = name, icon = icon, color = color, sortOrder = i, createdAt = clock())
            )
        }
    }
}

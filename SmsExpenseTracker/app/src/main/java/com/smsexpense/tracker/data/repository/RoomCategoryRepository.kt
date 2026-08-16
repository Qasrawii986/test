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

    override suspend fun add(name: String, icon: String, color: Long?, parentId: Long?): Long {
        // Only two levels: attaching to a subcategory promotes the parent to its root.
        val effectiveParent = parentId?.let { requested ->
            categoryDao.getById(requested)?.let { it.parentId ?: it.id }
        }
        return categoryDao.insert(
            CategoryEntity(
                name = name.trim(),
                icon = icon,
                color = color,
                sortOrder = categoryDao.maxSortOrderIn(effectiveParent) + 1,
                createdAt = clock(),
                parentId = effectiveParent,
            )
        )
    }

    override suspend fun update(category: Category) {
        categoryDao.update(
            CategoryEntity(
                id = category.id,
                name = category.name.trim(),
                icon = category.icon,
                color = category.color,
                sortOrder = category.sortOrder,
                createdAt = category.createdAt,
                parentId = category.parentId,
            )
        )
    }

    override suspend fun delete(id: Long) {
        // Payments referencing the deleted category (or any of its subcategories)
        // fall back to no link rather than pointing at a missing row.
        categoryDao.childrenOf(id).forEach { child -> paymentDao.clearCategoryRefs(child.id) }
        categoryDao.deleteChildrenOf(id)
        paymentDao.clearCategoryRefs(id)
        categoryDao.delete(id)
    }

    override suspend fun move(id: Long, up: Boolean) {
        val target = categoryDao.getById(id) ?: return
        // Reorder only within the same sibling group (same parent).
        val siblings = categoryDao.getAll().filter { it.parentId == target.parentId }
        val index = siblings.indexOfFirst { it.id == id }
        if (index == -1) return
        val swapWith = if (up) index - 1 else index + 1
        if (swapWith !in siblings.indices) return
        // Normalize orders so ties (fresh installs) still swap deterministically.
        siblings.forEachIndexed { i, entity ->
            val desired = when (i) {
                index -> swapWith
                swapWith -> index
                else -> i
            }
            if (entity.sortOrder != desired) categoryDao.update(entity.copy(sortOrder = desired))
        }
    }

    override suspend fun seedDefaultsIfEmpty() {
        if (categoryDao.count() > 0) return
        val defaults = listOf(
            Triple("Food", "🍔", 0xFFEF6C00) to listOf("Groceries" to "🛒", "Restaurants" to "🍽️"),
            Triple("Transport", "🚗", 0xFF1565C0) to listOf("Fuel" to "⛽", "Taxi" to "🚕"),
            Triple("Shopping", "🛍️", 0xFF6A1B9A) to emptyList(),
            Triple("Bills", "🧾", 0xFF00695C) to listOf("Utilities" to "💡", "Internet" to "🌐"),
            Triple("Other", "📦", 0xFF546E7A) to emptyList(),
        )
        defaults.forEachIndexed { index, (root, children) ->
            val (name, icon, color) = root
            val rootId = categoryDao.insert(
                CategoryEntity(
                    name = name, icon = icon, color = color,
                    sortOrder = index, createdAt = clock(), parentId = null,
                )
            )
            children.forEachIndexed { childIndex, (childName, childIcon) ->
                categoryDao.insert(
                    CategoryEntity(
                        name = childName, icon = childIcon, color = color,
                        sortOrder = childIndex, createdAt = clock(), parentId = rootId,
                    )
                )
            }
        }
    }
}

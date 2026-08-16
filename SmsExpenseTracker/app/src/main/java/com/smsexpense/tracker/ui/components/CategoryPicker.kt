package com.smsexpense.tracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.Category
import com.smsexpense.tracker.domain.model.toTree

/**
 * Two-level category chooser used by the bubble, payment details and the
 * historical import so all three behave identically.
 *
 * Root categories are shown first. Tapping a root that has no subcategories
 * selects it immediately — the one-tap path is preserved. Tapping a root that
 * has subcategories drills in, offering the parent itself plus its children,
 * so nothing is ever more than two taps away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    categories: List<Category>,
    onSelected: (Long) -> Unit,
    modifier: Modifier = Modifier,
    selectedId: Long? = null,
) {
    val tree = remember(categories) { categories.toTree() }
    var drilledInto by remember(categories) { mutableStateOf<Long?>(null) }
    val openNode = tree.find { it.category.id == drilledInto }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (openNode == null) {
            tree.forEach { node ->
                AssistChip(
                    onClick = {
                        if (node.hasChildren) drilledInto = node.category.id
                        else onSelected(node.category.id)
                    },
                    label = {
                        Text(
                            "${node.category.icon} ${node.category.name}" +
                                if (node.hasChildren) " ›" else ""
                        )
                    },
                )
            }
        } else {
            AssistChip(
                onClick = { drilledInto = null },
                label = { Text("‹ Back") },
            )
            // Allow assigning the parent itself, not only a leaf.
            AssistChip(
                onClick = { onSelected(openNode.category.id) },
                label = { Text("${openNode.category.icon} ${openNode.category.name}") },
            )
            openNode.children.forEach { child ->
                AssistChip(
                    onClick = { onSelected(child.id) },
                    label = { Text("${child.icon} ${child.name}") },
                )
            }
        }
    }
}

/** Full path of a category for display, e.g. "Food › Groceries". */
fun categoryPath(categories: List<Category>, categoryId: Long?): String? {
    val category = categories.find { it.id == categoryId } ?: return null
    val parent = category.parentId?.let { id -> categories.find { it.id == id } }
    return if (parent != null) "${parent.name} › ${category.name}" else category.name
}

package com.smsexpense.tracker.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.smsexpense.tracker.domain.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    viewModel: CategoriesViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<Category?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addingSubcategoryOf by remember { mutableStateOf<Category?>(null) }
    var deleting by remember { mutableStateOf<Pair<Category, Int>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.tree, key = { it.category.id }) { node ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        CategoryRow(
                            category = node.category,
                            isRoot = true,
                            onMoveUp = { viewModel.move(node.category.id, up = true) },
                            onMoveDown = { viewModel.move(node.category.id, up = false) },
                            onEdit = { editing = node.category },
                            onDelete = { deleting = node.category to node.children.size },
                        )
                        node.children.forEach { child ->
                            CategoryRow(
                                category = child,
                                isRoot = false,
                                onMoveUp = { viewModel.move(child.id, up = true) },
                                onMoveDown = { viewModel.move(child.id, up = false) },
                                onEdit = { editing = child },
                                onDelete = { viewModel.delete(child.id) },
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = { addingSubcategoryOf = node.category },
                            modifier = Modifier.padding(start = 24.dp, bottom = 4.dp),
                        ) { Text("+ Add subcategory") }
                    }
                }
            }
        }
    }

    if (showAdd) {
        CategoryDialog(
            title = "Add main category",
            initialName = "",
            initialIcon = "",
            onConfirm = { name, icon ->
                viewModel.add(name, icon)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
    addingSubcategoryOf?.let { parent ->
        CategoryDialog(
            title = "Add subcategory to ${parent.name}",
            initialName = "",
            initialIcon = "",
            onConfirm = { name, icon ->
                viewModel.add(name, icon, parentId = parent.id)
                addingSubcategoryOf = null
            },
            onDismiss = { addingSubcategoryOf = null },
        )
    }
    deleting?.let { (category, childCount) ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${category.name}?") },
            text = {
                Text(
                    if (childCount > 0) {
                        "This also deletes its $childCount subcategories. Payments in them " +
                            "stay, but lose their category."
                    } else {
                        "Payments in this category stay, but lose their category."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(category.id)
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    editing?.let { category ->
        CategoryDialog(
            title = "Edit category",
            initialName = category.name,
            initialIcon = category.icon,
            onConfirm = { name, icon ->
                viewModel.rename(category, name, icon)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    isRoot: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(
            start = if (isRoot) 12.dp else 32.dp,
            end = 4.dp,
            top = 2.dp,
            bottom = 2.dp,
        ),
    ) {
        Text(
            category.icon,
            style = if (isRoot) MaterialTheme.typography.titleLarge
            else MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            category.name,
            style = if (isRoot) MaterialTheme.typography.bodyLarge
            else MaterialTheme.typography.bodyMedium,
            fontWeight = if (isRoot) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up")
        }
        IconButton(onClick = onMoveDown) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down")
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Edit")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    initialName: String,
    initialIcon: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it.take(4) },
                    label = { Text("Icon (emoji)") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, icon) }, enabled = name.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

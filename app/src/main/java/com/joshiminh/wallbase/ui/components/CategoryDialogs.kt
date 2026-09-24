package com.joshiminh.wallbase.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.wallbase.data.entity.CategoryItem
import com.joshiminh.wallbase.ui.theme.WallBaseShapes

@Composable
fun CategoryPickerDialog(
    categories: List<CategoryItem>,
    selectedCategoryIds: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
    onCreateNewCategory: (String) -> Unit
) {
    var selectedIds by remember(selectedCategoryIds) { mutableStateOf(selectedCategoryIds) }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onConfirm = { name ->
                onCreateNewCategory(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Assign categories", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = "Create category")
                }
            }
        },
        text = {
            if (categories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No categories available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { showCreateDialog = true }) {
                        Text("Create a category")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        val isChecked = category.id in selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedIds = if (isChecked) {
                                        selectedIds - category.id
                                    } else {
                                        selectedIds + category.id
                                    }
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + category.id
                                    } else {
                                        selectedIds - category.id
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${category.wallpaperCount} wallpapers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedIds) }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateCategoryDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "New Category") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Category name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = WallBaseShapes.control
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                },
                enabled = title.trim().isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameCategoryDialog(
    category: CategoryItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(category.title) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Rename Category") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Category name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = WallBaseShapes.control
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = title.trim()
                    if (trimmed.isNotBlank()) onConfirm(trimmed)
                },
                enabled = title.trim().isNotBlank() && title.trim() != category.title
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ManageCategoriesDialog(
    categories: List<CategoryItem>,
    onCreateCategory: (String) -> Unit,
    onRenameCategory: (CategoryItem, String) -> Unit,
    onDeleteCategory: (CategoryItem) -> Unit,
    onDismiss: () -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var categoryToRename by remember { mutableStateOf<CategoryItem?>(null) }
    var categoryToDelete by remember { mutableStateOf<CategoryItem?>(null) }

    if (showCreateDialog) {
        CreateCategoryDialog(
            onConfirm = { name ->
                onCreateCategory(name)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    categoryToRename?.let { cat ->
        RenameCategoryDialog(
            category = cat,
            onConfirm = { newName ->
                onRenameCategory(cat, newName)
                categoryToRename = null
            },
            onDismiss = { categoryToRename = null }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Delete Category?") },
            text = {
                Text("Delete \"${cat.title}\"? Wallpapers in this category will remain in your library.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteCategory(cat)
                    categoryToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Manage Categories", style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { showCreateDialog = true }, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Outlined.Add, contentDescription = "New Category")
                }
            }
        },
        text = {
            if (categories.isEmpty()) {
                Text(
                    text = "No categories available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = category.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${category.wallpaperCount} wallpapers",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { categoryToRename = category }) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Rename",
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { categoryToDelete = category }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

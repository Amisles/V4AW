package org.amisles.v4aw.ui.screen.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.model.VideoInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onNavigateToPlayer: (VideoInfo) -> Unit,
    onNavigateToResourceBrowser: (VideoInfo) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current
    val isInSelectionMode = uiState.selectedItems.isNotEmpty()
    var showSearchBar by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    val groupedHistory = remember(uiState.historyItems, uiState.searchQuery) {
        viewModel.getGroupedHistory()
    }

    val filteredItemCount = remember(uiState.historyItems, uiState.searchQuery) {
        viewModel.getFilteredItems().size
    }

    Scaffold(
        topBar = {
            if (isInSelectionMode) {
                SelectionTopBar(
                    selectedCount = uiState.selectedItems.size,
                    onSelectAll = { viewModel.selectAll() },
                    onDeleteSelected = { showDeleteDialog = true },
                    onCancel = { viewModel.clearSelection() }
                )
            } else {
                HistoryTopBar(
                    showSearchBar = showSearchBar,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onToggleSearch = { showSearchBar = !showSearchBar },
                    onClearAll = { showClearDialog = true },
                    itemCount = filteredItemCount
                )
            }
        }
    ) { paddingValues ->
        if (uiState.historyItems.isEmpty()) {
            EmptyHistoryView(modifier = Modifier.padding(paddingValues))
        } else if (filteredItemCount == 0 && uiState.searchQuery.isNotEmpty()) {
            EmptySearchView(modifier = Modifier.padding(paddingValues))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groupedHistory.forEach { group ->
                    item(key = "header_${group.label}") {
                        GroupHeader(label = group.label)
                    }
                    items(
                        items = group.items,
                        key = { it.id }
                    ) { item ->
                        HistoryCard(
                            item = item,
                            isSelected = uiState.selectedItems.any { it.id == item.id },
                            isInSelectionMode = isInSelectionMode,
                            onClick = {
                                if (isInSelectionMode) {
                                    viewModel.toggleSelection(item)
                                } else {
                                    onNavigateToPlayer(viewModel.getVideoInfo(item))
                                }
                            },
                            onLongClick = {
                                if (!isInSelectionMode) {
                                    viewModel.toggleSelection(item)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        ConfirmDialog(
            title = strings.deleteSelected,
            message = strings.deleteConfirmSelected.format(uiState.selectedItems.size),
            onConfirm = {
                viewModel.deleteSelectedItems()
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }

    if (showClearDialog) {
        ConfirmDialog(
            title = strings.clearAll,
            message = strings.deleteConfirmAll,
            onConfirm = {
                viewModel.clearAllHistory()
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopBar(
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onClearAll: () -> Unit,
    itemCount: Int
) {
    val strings = LocalStrings.current
    TopAppBar(
        title = {
            if (showSearchBar) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = strings.searchHistory,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
            } else {
                Text(strings.historyTitle)
            }
        },
        actions = {
            if (showSearchBar) {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            } else {
                IconButton(onClick = onToggleSearch) {
                    Icon(Icons.Default.Search, contentDescription = null)
                }
                IconButton(onClick = onClearAll) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancel: () -> Unit
) {
    val strings = LocalStrings.current
    TopAppBar(
        title = {
            Text(strings.selectedCount.format(selectedCount))
        },
        actions = {
            TextButton(onClick = onSelectAll) {
                Text(strings.selectAll)
            }
            IconButton(onClick = onDeleteSelected) {
                Icon(Icons.Default.Delete, contentDescription = null)
            }
        },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
            }
        }
    )
}

@Composable
private fun GroupHeader(label: String) {
    val strings = LocalStrings.current
    val displayLabel = when (label) {
        "today" -> strings.today
        "yesterday" -> strings.yesterday
        else -> label
    }
    Text(
        text = displayLabel,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 4.dp),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryCard(
    item: HistoryItem,
    isSelected: Boolean,
    isInSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = remember(item.timestamp) { sdf.format(Date(item.timestamp)) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isInSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            if (!item.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 120.dp, height = 68.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isSelected && !isInSelectionMode) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyHistoryView(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = strings.noHistory,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = strings.noHistoryHint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun EmptySearchView(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = strings.noSearchResult,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(strings.confirm)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

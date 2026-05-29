package org.amisles.v4aw.ui.screen.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import org.amisles.v4aw.model.DownloadStatus
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (DownloadTask) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val strings = LocalStrings.current

    val isSelectionMode = uiState.selectedItems.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(onClick = { viewModel.clearSelection() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.cancelSelection
                    )
                }
                Text(
                    text = strings.selectedCount.format(uiState.selectedItems.size),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )
                Spacer(modifier = Modifier.weight(1f))
                val currentItems = when (uiState.selectedTab) {
                    DownloadTab.DOWNLOADING -> uiState.downloadingItems
                    DownloadTab.COMPLETED -> uiState.completedItems
                    DownloadTab.FAILED -> uiState.failedItems
                }
                if (uiState.selectedItems.size < currentItems.size) {
                    TextButton(onClick = { viewModel.selectAll() }) {
                        Text(strings.selectAll)
                    }
                }
                IconButton(
                    onClick = { showDeleteDialog = true },
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = strings.delete)
                }
            } else {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = strings.downloads,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        if (!isSelectionMode) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Slate50
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        title = strings.downloading,
                        value = uiState.downloadingItems.size.toString(),
                        icon = Icons.Default.Download
                    )
                    StatItem(
                        title = strings.completed,
                        value = uiState.completedItems.size.toString(),
                        icon = Icons.Default.CheckCircle
                    )
                    StatItem(
                        title = strings.storageSpace,
                        value = "${uiState.storageUsed}/${uiState.storageTotal}",
                        icon = Icons.Default.Storage
                    )
                }
            }
        }

        if (!isSelectionMode) {
            TabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                DownloadTab.entries.forEach { tab ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.setSelectedTab(tab) },
                        text = { Text(tab.getDisplayName()) }
                    )
                }
            }
        }

        val currentItems = when (uiState.selectedTab) {
            DownloadTab.DOWNLOADING -> uiState.downloadingItems
            DownloadTab.COMPLETED -> uiState.completedItems
            DownloadTab.FAILED -> uiState.failedItems
        }

        if (currentItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = when (uiState.selectedTab) {
                        DownloadTab.DOWNLOADING -> Icons.Default.Download
                        DownloadTab.COMPLETED -> Icons.Default.CheckCircle
                        DownloadTab.FAILED -> Icons.Default.Error
                    },
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Slate500
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (uiState.selectedTab) {
                        DownloadTab.DOWNLOADING -> strings.noDownloadTasks
                        DownloadTab.COMPLETED -> strings.noCompletedTasks
                        DownloadTab.FAILED -> strings.noFailedTasks
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate700
                )
                Text(
                    text = when (uiState.selectedTab) {
                        DownloadTab.DOWNLOADING -> strings.noDownloadTasksHint
                        DownloadTab.COMPLETED -> strings.noCompletedTasksHint
                        DownloadTab.FAILED -> strings.noFailedTasksHint
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Slate500
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(currentItems) { item ->
                    val isSelected = uiState.selectedItems.any { it.id == item.id }

                    DownloadTaskCard(
                        task = item,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onSelect = { viewModel.toggleSelection(item) },
                        onTogglePause = { viewModel.togglePause(item) },
                        onRetry = { viewModel.retryDownload(item) },
                        onDelete = { viewModel.deleteTask(item) },
                        onPlay = { onNavigateToPlayer(item) }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(strings.deleteDownloadTitle) },
            text = {
                Text(
                    strings.deleteDownloadMessage.format(uiState.selectedItems.size)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelectedItems()
                        showDeleteDialog = false
                    }
                ) {
                    Text(strings.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}

@Composable
fun StatItem(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Slate800
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = Slate500
        )
    }
}

@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelect: () -> Unit,
    onTogglePause: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onPlay: () -> Unit
) {
    val strings = LocalStrings.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelectionMode && task.status == DownloadStatus.COMPLETED) {
                onPlay()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer
            else Slate50
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                IconButton(
                    onClick = onSelect,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected)
                            Icons.Default.CheckCircle
                        else Icons.Outlined.CheckCircle,
                        contentDescription = if (isSelected) strings.cancelSelection else strings.select,
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else Slate500
                    )
                }
            }

            if (task.status == DownloadStatus.COMPLETED) {
                val thumbnailSource = when {
                    task.thumbnailUrl != null -> task.thumbnailUrl
                    task.filePath != null -> {
                        // 使用 Android Content URI 来加载视频缩略图，兼容性更好
                        val file = java.io.File(task.filePath)
                        if (file.exists()) {
                            file.toUri()
                        } else {
                            null
                        }
                    }
                    else -> null
                }
                
                if (thumbnailSource != null) {
                    AsyncImage(
                        model = thumbnailSource,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (task.status) {
                                DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED ->
                                    MaterialTheme.colorScheme.primaryContainer
                                DownloadStatus.COMPLETED ->
                                    MaterialTheme.colorScheme.tertiaryContainer
                                DownloadStatus.FAILED, DownloadStatus.CANCELLED, DownloadStatus.PENDING ->
                                    MaterialTheme.colorScheme.errorContainer
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (task.status) {
                            DownloadStatus.DOWNLOADING -> Icons.Default.Download
                            DownloadStatus.PAUSED -> Icons.Default.Pause
                            DownloadStatus.COMPLETED -> Icons.Default.PlayArrow
                            DownloadStatus.FAILED, DownloadStatus.CANCELLED, DownloadStatus.PENDING -> Icons.Default.Error
                        },
                        contentDescription = null,
                        tint = when (task.status) {
                            DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED ->
                                MaterialTheme.colorScheme.primary
                            DownloadStatus.COMPLETED ->
                                MaterialTheme.colorScheme.tertiary
                            DownloadStatus.FAILED, DownloadStatus.CANCELLED, DownloadStatus.PENDING ->
                                MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else Slate800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else Slate500,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = task.fileSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                    else Slate500
                )

                if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { task.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = when (task.status) {
                            DownloadStatus.DOWNLOADING -> MaterialTheme.colorScheme.primary
                            else -> Slate500
                        }
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(task.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isSelected)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else Slate500
                        )
                        if (task.status == DownloadStatus.DOWNLOADING) {
                            val threadInfo = if (task.threadCount > 1) {
                                strings.threadCount.format(task.threadCount) + " · "
                            } else ""
                            Text(
                                text = threadInfo + "${task.speed} · ${task.remainingTime}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isSelected)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else Slate500
                            )
                        }
                    }
                }

                if (task.status == DownloadStatus.FAILED && task.errorMessage != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                when (task.status) {
                    DownloadStatus.DOWNLOADING -> {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = strings.pause,
                                tint = Slate500
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        IconButton(onClick = onTogglePause) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = strings.resume,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.COMPLETED -> {
                        IconButton(onClick = onPlay) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = strings.play,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = strings.retry,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    else -> {}
                }
                IconButton(
                    onClick = onDelete,
                    colors = IconButtonDefaults.iconButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = strings.delete)
                }
            }
        }
    }
}

package org.amisles.v4aw.ui.screen.resourcebrowser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.ui.components.SiteSearchCard
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceBrowserScreen(
    viewModel: ResourceBrowserViewModel,
    videoInfo: VideoInfo?,
    onNavigateBack: () -> Unit,
    onNavigateToPlayer: (VideoInfo) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val strings = LocalStrings.current

    LaunchedEffect(videoInfo) {
        if (videoInfo != null) {
            viewModel.initialize(videoInfo)
        } else {
            viewModel.initializeFromCache()
        }
    }

    LaunchedEffect(uiState.navigateToPlayer) {
        uiState.navigateToPlayer?.let { info ->
            onNavigateToPlayer(info)
            viewModel.clearNavigateToPlayer()
        }
    }

    BackHandler {
        if (!viewModel.navigateBack()) {
            onNavigateBack()
        }
    }

    val currentVideoInfo = uiState.videoInfo
    if (currentVideoInfo == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Custom Top Bar (without Scaffold to avoid status bar gap)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 4.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                if (!viewModel.navigateBack()) {
                    onNavigateBack()
                }
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backToOriginal,
                    tint = Slate800
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = currentVideoInfo.title.ifEmpty { strings.resourceBrowser },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Slate800
            )
        }

        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            uiState.errorMessage != null && currentVideoInfo.videoEntries.isEmpty() -> {
                ErrorContent(
                    errorMessage = uiState.errorMessage!!,
                    onRetry = { viewModel.initialize(currentVideoInfo) }
                )
            }
            else -> {
                BrowseContent(
                    uiState = uiState,
                    onParseVideoEntry = { viewModel.parseVideoEntry(it) },
                    onSearch = { endpoint, query -> viewModel.searchSite(endpoint, query) },
                    onRestoreOriginalEntries = { viewModel.restoreOriginalEntries() }
                )
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Slate800)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = strings.parsingVideo,
                color = Slate700,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun ErrorContent(
    errorMessage: String,
    onRetry: () -> Unit
) {
    val strings = LocalStrings.current
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry) {
                Text(strings.retry)
            }
        }
    }
}

@Composable
private fun BrowseContent(
    uiState: ResourceBrowserUiState,
    onParseVideoEntry: (VideoEntry) -> Unit,
    onSearch: (org.amisles.v4aw.model.SearchEndpoint, String) -> Unit,
    onRestoreOriginalEntries: () -> Unit
) {
    val strings = LocalStrings.current
    val currentVideoInfo = uiState.videoInfo ?: return
    val displayEntries = currentVideoInfo.videoEntries

    if (currentVideoInfo.searchEndpoints.isNotEmpty()) {
        SiteSearchCard(
            searchEndpoints = currentVideoInfo.searchEndpoints,
            isSearching = uiState.isSearching,
            errorMessage = uiState.searchErrorMessage,
            onSearch = onSearch
        )
    }

    if (uiState.isSearchResultMode) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onRestoreOriginalEntries,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = strings.backToOriginal,
                    tint = Slate800,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = strings.searchResults,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Slate800
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(${displayEntries.size})",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        }
    }

    if (displayEntries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = strings.noResourcesFound,
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(displayEntries) { entry ->
                VideoEntryGridItem(
                    entry = entry,
                    onClick = { onParseVideoEntry(entry) }
                )
            }
        }
    }
}

@Composable
private fun VideoEntryGridItem(
    entry: VideoEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Slate50),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.title.take(1),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Slate500
                    )
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Slate800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

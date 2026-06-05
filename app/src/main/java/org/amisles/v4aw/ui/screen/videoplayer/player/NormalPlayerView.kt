package org.amisles.v4aw.ui.screen.videoplayer.player

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.components.VideoEntryItem
import org.amisles.v4aw.ui.components.SiteSearchCard
import org.amisles.v4aw.ui.screen.videoplayer.PlayerGestureOverlay
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerViewModel
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NormalPlayerView(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    videoInfo: VideoInfo,
    onNavigateBack: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onParseVideoEntry: (VideoEntry) -> Unit,
    onPlayVideo: (String) -> Unit,
    onShowDownloadDialog: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onShowAbLoopDialog: () -> Unit,
    onPlayerViewCreated: (PlayerView) -> Unit,
    onTap: () -> Unit,
    onRestoreOriginalEntries: () -> Unit,
    onNavigateToResourceBrowser: (VideoInfo) -> Unit
) {
    val strings = LocalStrings.current
    val isLocalVideo = videoInfo.videoSources.any { it.startsWith("file://") } ||
            videoInfo.url.startsWith("/") ||
            videoInfo.url.startsWith("file://")

    var isControllerVisible by remember { mutableStateOf(false) }
    var localPlayerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(localPlayerViewRef) {
        val pv = localPlayerViewRef ?: return@LaunchedEffect
        isControllerVisible = pv.isControllerFullyVisible
        while (true) {
            val currentVisible = pv.isControllerFullyVisible
            if (isControllerVisible != currentVisible) {
                isControllerVisible = currentVisible
            }
            val delayMs = if (isControllerVisible) 100L else 500L
            kotlinx.coroutines.delay(delayMs)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.White)
    ) {
        PlayerTopBar(
            uiState = uiState,
            viewModel = viewModel,
            isLocalVideo = isLocalVideo,
            onNavigateBack = onNavigateBack,
            onShowAbLoopDialog = onShowAbLoopDialog,
            onShowDownloadDialog = onShowDownloadDialog
        )

        when {
            uiState.isLoading -> {
                LoadingContent()
            }
            uiState.player != null -> {
                PlayerContent(
                    viewModel = viewModel,
                    uiState = uiState,
                    videoInfo = videoInfo,
                    isControllerVisible = isControllerVisible,
                    onToggleFullscreen = onToggleFullscreen,
                    onParseVideoEntry = onParseVideoEntry,
                    onShowSpeedDialog = onShowSpeedDialog,
                    onTap = onTap,
                    onRestoreOriginalEntries = onRestoreOriginalEntries,
                    onPlayerViewCreated = { pv ->
                        localPlayerViewRef = pv
                        onPlayerViewCreated(pv)
                    },
                    onNavigateToResourceBrowser = onNavigateToResourceBrowser
                )
            }
            uiState.errorMessage != null -> {
                ErrorContent(
                    uiState = uiState,
                    onPlayVideo = onPlayVideo
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTopBar(
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    isLocalVideo: Boolean,
    onNavigateBack: () -> Unit,
    onShowAbLoopDialog: () -> Unit,
    onShowDownloadDialog: () -> Unit
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, start = 4.dp, end = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Slate800
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = strings.resourcePreview,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Slate800
        )
        Spacer(modifier = Modifier.weight(1f))

        if (uiState.isPictureInPictureSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val context = LocalContext.current
            val activity = context as? Activity
            IconButton(
                onClick = {
                    activity?.let { act ->
                        if (uiState.player != null) {
                            viewModel.enterPictureInPictureMode(act)
                        }
                    }
                }
            ) {
                Icon(
                    Icons.Default.PictureInPicture,
                    contentDescription = strings.pictureInPicture,
                    tint = Slate800
                )
            }
        }

        IconButton(onClick = onShowAbLoopDialog) {
            Icon(
                Icons.Default.Autorenew,
                contentDescription = strings.abLoop,
                tint = if (uiState.isAbLoopActive) MaterialTheme.colorScheme.primary else Slate800
            )
        }

        if (!isLocalVideo) {
            IconButton(onClick = onShowDownloadDialog) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = strings.downloads,
                    tint = Slate800
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadingContent() {
    val strings = LocalStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = Slate800)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = strings.parsingVideo,
            color = Slate700,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun ColumnScope.PlayerContent(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    videoInfo: VideoInfo,
    isControllerVisible: Boolean,
    onToggleFullscreen: () -> Unit,
    onParseVideoEntry: (VideoEntry) -> Unit,
    onShowSpeedDialog: () -> Unit,
    onTap: () -> Unit,
    onRestoreOriginalEntries: () -> Unit,
    onPlayerViewCreated: (PlayerView) -> Unit,
    onNavigateToResourceBrowser: (VideoInfo) -> Unit
) {
    val strings = LocalStrings.current
    val currentVideoInfo = uiState.videoInfo ?: videoInfo

    PlayerGestureOverlay(
        viewModel = viewModel,
        isControllerVisible = isControllerVisible,
        onTap = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        ExoPlayerView(
            player = uiState.player,
            uiState = uiState,
            viewModel = viewModel,
            showPip = false,
            onToggleFullscreen = onToggleFullscreen,
            onShowSpeedDialog = onShowSpeedDialog,
            onPlayerViewCreated = onPlayerViewCreated,
            modifier = Modifier.fillMaxSize()
        )
    }

    Text(
        text = currentVideoInfo.title.ifEmpty { strings.videoPlay },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = Slate800,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )

    if (currentVideoInfo.searchEndpoints.isNotEmpty()) {
        SiteSearchCard(
            searchEndpoints = currentVideoInfo.searchEndpoints,
            isSearching = uiState.isSearching,
            errorMessage = uiState.searchErrorMessage,
            onSearch = { endpoint, query ->
                viewModel.searchSite(endpoint, query)
            }
        )
    }

    if (currentVideoInfo.videoEntries.isNotEmpty() || uiState.isSearchResultMode) {
        val displayEntries = currentVideoInfo.videoEntries

        if (displayEntries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                color = Slate50,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    VideoEntriesHeader(
                        isSearchResultMode = uiState.isSearchResultMode,
                        entryCount = displayEntries.size,
                        onRestoreOriginalEntries = onRestoreOriginalEntries
                    )

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(displayEntries) { entry ->
                            VideoEntryItem(
                                entry = entry,
                                onClick = { onParseVideoEntry(entry) }
                            )
                        }
                    }
                }
            }
        }
    } else {
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun VideoEntriesHeader(
    isSearchResultMode: Boolean,
    entryCount: Int,
    onRestoreOriginalEntries: () -> Unit
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearchResultMode) {
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
        }

        Text(
            text = if (isSearchResultMode) strings.searchResults else strings.relatedResources,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Slate800
        )

        if (isSearchResultMode) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "($entryCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = Slate500
            )
        }
    }
}

@Composable
private fun ColumnScope.ErrorContent(
    uiState: VideoPlayerUiState,
    onPlayVideo: (String) -> Unit
) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Slate50
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                if (uiState.availableSources.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = strings.availableSourcesLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Slate700
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    uiState.availableSources.forEach { source ->
                        Text(
                            text = source,
                            style = MaterialTheme.typography.bodySmall,
                            color = Slate500,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlayVideo(source) }
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

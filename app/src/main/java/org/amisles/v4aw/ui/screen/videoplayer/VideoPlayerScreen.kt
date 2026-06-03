package org.amisles.v4aw.ui.screen.videoplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.ui.screen.downloads.DownloadsViewModel
import org.amisles.v4aw.ui.screen.videoplayer.dialog.AbLoopDialog
import org.amisles.v4aw.ui.screen.videoplayer.dialog.DownloadDialog
import org.amisles.v4aw.ui.screen.videoplayer.dialog.PlaybackSpeedDialog
import org.amisles.v4aw.ui.screen.videoplayer.player.FullscreenPlayerView
import org.amisles.v4aw.ui.screen.videoplayer.player.NormalPlayerView
import org.amisles.v4aw.ui.screen.videoplayer.player.PipPlayerView
import org.amisles.v4aw.ui.screen.videoplayer.utils.exitFullscreen

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    videoInfo: VideoInfo,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val downloadsViewModel: DownloadsViewModel = hiltViewModel()
    val context = LocalContext.current
    val activity = context as? Activity
    var showDownloadDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showAbLoopDialog by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val handleNavigateBack: () -> Unit = {
        viewModel.releasePlayer()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        exitFullscreen(activity)
        onNavigateBack()
    }

    BackHandler(onBack = handleNavigateBack)

    LaunchedEffect(videoInfo) {
        viewModel.initializePlayer(videoInfo)
    }

    DisposableEffect(Unit) {
        val mainActivity = activity as? org.amisles.v4aw.MainActivity
        val listener: (Boolean) -> Unit = { isInPip ->
            viewModel.onPictureInPictureModeChanged(isInPip)
        }
        mainActivity?.setPictureInPictureModeChangedListener(listener)

        onDispose {
            mainActivity?.clearPictureInPictureModeChangedListener()
            viewModel.releasePlayer()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            exitFullscreen(activity)
        }
    }

    LaunchedEffect(uiState.isFullscreen) {
        if (uiState.isFullscreen) {
            org.amisles.v4aw.ui.screen.videoplayer.utils.enterFullscreen(activity)
        } else {
            exitFullscreen(activity)
        }
    }

    when {
        uiState.isInPictureInPicture && uiState.player != null -> {
            PipPlayerView(
                uiState = uiState,
                onPlayerViewCreated = { playerViewRef = it }
            )
        }
        uiState.isFullscreen && uiState.player != null -> {
            FullscreenPlayerView(
                viewModel = viewModel,
                uiState = uiState,
                onToggleFullscreen = { viewModel.toggleFullscreen() },
                onShowSpeedDialog = { showSpeedDialog = true },
                onShowAbLoopDialog = { showAbLoopDialog = true },
                onPlayerViewCreated = { playerViewRef = it },
                onTap = {
                    playerViewRef?.let { pv ->
                        if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                    }
                }
            )
        }
        else -> {
            NormalPlayerView(
                viewModel = viewModel,
                uiState = uiState,
                videoInfo = videoInfo,
                onNavigateBack = handleNavigateBack,
                onToggleFullscreen = { viewModel.toggleFullscreen() },
                onParseVideoEntry = { viewModel.parseVideoEntry(it) },
                onPlayVideo = { viewModel.playVideo(it) },
                onShowDownloadDialog = { showDownloadDialog = true },
                onShowSpeedDialog = { showSpeedDialog = true },
                onShowAbLoopDialog = { showAbLoopDialog = true },
                onPlayerViewCreated = { playerViewRef = it },
                onTap = {
                    playerViewRef?.let { pv ->
                        if (pv.isControllerFullyVisible) pv.hideController() else pv.showController()
                    }
                },
                onRestoreOriginalEntries = { viewModel.restoreOriginalEntries() }
            )
        }
    }

    if (showDownloadDialog) {
        val currentVideoInfo = uiState.videoInfo ?: videoInfo
        DownloadDialog(
            videoInfo = currentVideoInfo,
            availableSources = uiState.availableSources.ifEmpty { currentVideoInfo.videoSources },
            onDismiss = { showDownloadDialog = false },
            onDownload = { source ->
                val id = "download_${System.currentTimeMillis()}"
                downloadsViewModel.startDownload(
                    id = id,
                    title = currentVideoInfo.title,
                    url = currentVideoInfo.url,
                    videoSourceUrl = source,
                    thumbnailUrl = currentVideoInfo.thumbnailUrl
                )
                showDownloadDialog = false
            }
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedSelected = { speed ->
                viewModel.setPlaybackSpeed(speed)
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false }
        )
    }

    if (showAbLoopDialog) {
        AbLoopDialog(
            uiState = uiState,
            onSetA = { viewModel.setAbLoopA() },
            onSetB = { viewModel.setAbLoopB() },
            onClear = {
                viewModel.clearAbLoop()
                showAbLoopDialog = false
            },
            onDismiss = { showAbLoopDialog = false }
        )
    }
}

package org.amisles.v4aw.ui.screen.videoplayer

import android.view.LayoutInflater
import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ImageButton
import android.widget.TextView
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.R
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.screen.downloads.DownloadsViewModel
import org.amisles.v4aw.ui.theme.Slate50
import org.amisles.v4aw.ui.theme.Slate500
import org.amisles.v4aw.ui.theme.Slate700
import org.amisles.v4aw.ui.theme.Slate800

private fun setupPlayerViewButtons(
    view: View,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    activity: Activity?,
    onToggleFullscreen: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onShowAbLoopDialog: () -> Unit
) {
    val fullscreenBtn = view.findViewById<View>(R.id.exo_fullscreen_btn)
    val speedBtn = view.findViewById<TextView>(R.id.exo_speed_btn)
    val pipBtn = view.findViewById<View>(R.id.exo_pip_btn)
    val abLoopBtn = view.findViewById<View>(R.id.exo_ab_loop_btn)
    
    fullscreenBtn?.setOnClickListener { onToggleFullscreen() }
    speedBtn?.text = formatSpeedText(uiState.playbackSpeed)
    speedBtn?.setOnClickListener { onShowSpeedDialog() }
    
    pipBtn?.visibility = if (uiState.isPictureInPictureSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        View.VISIBLE
    } else {
        View.GONE
    }
    pipBtn?.setOnClickListener {
        activity?.let { act ->
            if (uiState.player != null) {
                viewModel.enterPictureInPictureMode(act)
            }
        }
    }
    
    abLoopBtn?.setOnClickListener { onShowAbLoopDialog() }
    if (abLoopBtn is ImageButton) {
        abLoopBtn.imageAlpha = if (uiState.isAbLoopActive) 255 else 128
    }
}

private fun updatePlayerViewSpeed(
    view: View,
    uiState: VideoPlayerUiState
) {
    val speedBtn = view.findViewById<TextView>(R.id.exo_speed_btn)
    speedBtn?.text = formatSpeedText(uiState.playbackSpeed)
    
    val pipBtn = view.findViewById<View>(R.id.exo_pip_btn)
    pipBtn?.visibility = if (uiState.isPictureInPictureSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        View.VISIBLE
    } else {
        View.GONE
    }
    
    val abLoopBtn = view.findViewById<View>(R.id.exo_ab_loop_btn)
    if (abLoopBtn is ImageButton) {
        abLoopBtn.imageAlpha = if (uiState.isAbLoopActive) 255 else 128
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
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
    var hasInitialized by remember { mutableStateOf(false) }
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
        if (!hasInitialized) {
            hasInitialized = true
            viewModel.initializePlayer(videoInfo)
        }
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
            enterFullscreen(activity)
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
                }
            )
        }
    }

    if (showDownloadDialog) {
        DownloadDialog(
            videoInfo = videoInfo,
            availableSources = uiState.availableSources.ifEmpty { videoInfo.videoSources },
            onDismiss = { showDownloadDialog = false },
            onDownload = { source ->
                val id = "download_${System.currentTimeMillis()}"
                downloadsViewModel.startDownload(
                    id = id,
                    title = videoInfo.title,
                    url = videoInfo.url,
                    videoSourceUrl = source,
                    thumbnailUrl = videoInfo.thumbnailUrl
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

@Composable
fun DownloadDialog(
    videoInfo: VideoInfo,
    availableSources: List<String>,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit
) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.selectSourceToDownload) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = videoInfo.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Slate800,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Slate50,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = strings.downloadTip,
                        style = MaterialTheme.typography.bodySmall,
                        color = Slate500,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (availableSources.isEmpty()) {
                    Text(strings.noAvailableSources)
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 300.dp)
                    ) {
                        items(availableSources) { source ->
                            val displayName = if (source.length > 50) {
                                source.take(50) + "..."
                            } else {
                                source
                            }
                            val isDownloadable = isDownloadableSource(source)
                            val isHls = isHlsSource(source)
                            val isStreaming = isStreamingSource(source)
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isDownloadable) { 
                                        if (isDownloadable) {
                                            onDownload(source) 
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isDownloadable) {
                                        if (isHls) {
                                            androidx.compose.ui.graphics.Color(0xFFFFF8E1)
                                        } else {
                                            androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                                        }
                                    } else if (isStreaming) {
                                        androidx.compose.ui.graphics.Color(0xFFFFEBEE)
                                    } else {
                                        Slate50
                                    }
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDownloadable) Slate800 else Slate500
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (isDownloadable) {
                                            if (isHls) {
                                                Icon(
                                                    Icons.Default.Download,
                                                    contentDescription = strings.hlsDownloadable,
                                                    tint = androidx.compose.ui.graphics.Color(0xFFFF9800),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.hlsDownloadable,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.ui.graphics.Color(0xFFFF9800)
                                                )
                                            } else {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = strings.downloadable,
                                                    tint = androidx.compose.ui.graphics.Color(0xFF4CAF50),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = strings.downloadable,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                                )
                                            }
                                        } else if (isStreaming) {
                                            Icon(
                                                Icons.Default.Warning,
                                                contentDescription = strings.streamingFormat,
                                                tint = androidx.compose.ui.graphics.Color(0xFFF44336),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = strings.streamingFormat,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = androidx.compose.ui.graphics.Color(0xFFF44336)
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.HelpOutline,
                                                contentDescription = strings.unknownFormat,
                                                tint = Slate500,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = strings.unknownFormat,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Slate500
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

private fun isDownloadableSource(url: String): Boolean {
    val lowerUrl = url.lowercase()
    val downloadableExtensions = listOf(".mp4", ".webm", ".flv", ".mov", ".ts", ".m4v", ".m3u8")
    return downloadableExtensions.any { lowerUrl.contains(it) }
}

private fun isHlsSource(url: String): Boolean {
    return url.lowercase().contains(".m3u8")
}

private fun isStreamingSource(url: String): Boolean {
    val lowerUrl = url.lowercase()
    val streamingExtensions = listOf(".mpd")
    return streamingExtensions.any { lowerUrl.contains(it) }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullscreenPlayerView(
    viewModel: VideoPlayerViewModel,
    uiState: VideoPlayerUiState,
    onToggleFullscreen: () -> Unit,
    onShowSpeedDialog: () -> Unit,
    onShowAbLoopDialog: () -> Unit,
    onPlayerViewCreated: (PlayerView) -> Unit,
    onTap: () -> Unit
) {
    var isControllerVisible by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(playerViewRef) {
        val pv = playerViewRef ?: return@LaunchedEffect
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

    PlayerGestureOverlay(
        viewModel = viewModel,
        isControllerVisible = isControllerVisible,
        onTap = onTap,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.custom_player_view, null, false).also { view ->
                val playerView = view.findViewById<PlayerView>(R.id.player_view)
                playerView.player = uiState.player
                playerView.useController = true
                playerView.controllerShowTimeoutMs = 3000
                playerView.showController()
                playerView.setKeepContentOnPlayerReset(true)
                playerViewRef = playerView
                onPlayerViewCreated(playerView)
                
                setupPlayerViewButtons(
                    view = view,
                    uiState = uiState,
                    viewModel = viewModel,
                    activity = activity,
                    onToggleFullscreen = onToggleFullscreen,
                    onShowSpeedDialog = onShowSpeedDialog,
                    onShowAbLoopDialog = onShowAbLoopDialog
                )
            } as View
        },
        update = { view ->
            updatePlayerViewSpeed(view, uiState)
        },
        modifier = Modifier.fillMaxSize()
    )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NormalPlayerView(
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
    onTap: () -> Unit
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

        when {
            uiState.isLoading -> {
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
            uiState.player != null -> {
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
                    AndroidView(
                        factory = { ctx ->
                            LayoutInflater.from(ctx).inflate(R.layout.custom_player_view, null, false).also { view ->
                                val playerView = view.findViewById<PlayerView>(R.id.player_view)
                                playerView.player = uiState.player
                                playerView.useController = true
                                playerView.controllerShowTimeoutMs = 3000
                                playerView.showController()
                                playerView.setKeepContentOnPlayerReset(true)
                                localPlayerViewRef = playerView
                                onPlayerViewCreated(playerView)
                                
                                val fullscreenBtn = view.findViewById<View>(R.id.exo_fullscreen_btn)
                                fullscreenBtn?.setOnClickListener { onToggleFullscreen() }
                                val speedBtn = view.findViewById<TextView>(R.id.exo_speed_btn)
                                speedBtn?.text = formatSpeedText(uiState.playbackSpeed)
                                speedBtn?.setOnClickListener { onShowSpeedDialog() }
                                
                                val pipBtn = view.findViewById<View>(R.id.exo_pip_btn)
                                pipBtn?.visibility = View.GONE
                            } as View
                        },
                        update = { view ->
                            val speedBtn = view.findViewById<TextView>(R.id.exo_speed_btn)
                            speedBtn?.text = formatSpeedText(uiState.playbackSpeed)
                        },
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

                if (currentVideoInfo.videoEntries.isNotEmpty()) {
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
                            Text(
                                text = strings.relatedResources,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Slate800,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(currentVideoInfo.videoEntries) { entry ->
                                    VideoEntryItem(
                                        entry = entry,
                                        onClick = { onParseVideoEntry(entry) }
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            uiState.errorMessage != null -> {
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
        }
    }
}

private fun enterFullscreen(activity: Activity?) {
    activity?.window?.let { window ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
}

private fun exitFullscreen(activity: Activity?) {
    activity?.window?.let { window ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }
    }
    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
}

@Composable
private fun VideoEntryItem(
    entry: VideoEntry,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Slate50),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (entry.thumbnailUrl != null) {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp, 60.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Slate800,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val PLAYBACK_SPEEDS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

private fun formatSpeedText(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}

@Composable
private fun PlaybackSpeedDialog(
    currentSpeed: Float,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.playbackSpeed) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PLAYBACK_SPEEDS.forEach { speed ->
                    val isSelected = speed == currentSpeed
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSpeedSelected(speed) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) {
                            androidx.compose.ui.graphics.Color(0xFFE8F5E9)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatSpeedText(speed),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) {
                                    androidx.compose.ui.graphics.Color(0xFF2E7D32)
                                } else {
                                    Slate800
                                }
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

@Composable
private fun PipPlayerView(
    uiState: VideoPlayerUiState,
    onPlayerViewCreated: (PlayerView) -> Unit
) {
    AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.custom_player_view, null, false).also { view ->
                val playerView = view.findViewById<PlayerView>(R.id.player_view)
                playerView.player = uiState.player
                playerView.useController = false
                playerView.setKeepContentOnPlayerReset(true)
                onPlayerViewCreated(playerView)
                
                val fullscreenBtn = view.findViewById<View>(R.id.exo_fullscreen_btn)
                val speedBtn = view.findViewById<View>(R.id.exo_speed_btn)
                val pipBtn = view.findViewById<View>(R.id.exo_pip_btn)
                val abLoopBtn = view.findViewById<View>(R.id.exo_ab_loop_btn)
                fullscreenBtn?.visibility = View.GONE
                speedBtn?.visibility = View.GONE
                pipBtn?.visibility = View.GONE
                abLoopBtn?.visibility = View.GONE
            } as View
        },
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    )
}

private fun formatTimeMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@Composable
private fun AbLoopDialog(
    uiState: VideoPlayerUiState,
    onSetA: () -> Unit,
    onSetB: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalStrings.current
    val aText = uiState.abLoopA?.let { strings.abLoopASet.format(formatTimeMs(it)) } ?: strings.abLoopANotSet
    val bText = uiState.abLoopB?.let { strings.abLoopBSet.format(formatTimeMs(it)) } ?: strings.abLoopBNotSet

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.abLoop) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isAbLoopActive) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = androidx.compose.ui.graphics.Color(0xFFE8F5E9),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = strings.abLoopActive,
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = aText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Slate800
                    )
                    OutlinedButton(onClick = onSetA) {
                        Text(strings.abLoopSetA)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = Slate800
                    )
                    OutlinedButton(
                        onClick = onSetB,
                        enabled = uiState.abLoopA != null
                    ) {
                        Text(strings.abLoopSetB)
                    }
                }
            }
        },
        confirmButton = {
            if (uiState.isAbLoopActive) {
                TextButton(onClick = onClear) {
                    Text(strings.abLoopCleared)
                }
            }
            TextButton(onClick = onDismiss) {
                Text(strings.cancel)
            }
        }
    )
}

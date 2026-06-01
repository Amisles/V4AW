package org.amisles.v4aw.ui.screen.videoplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.ui.screen.videoplayer.PlayerGestureOverlay
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun FullscreenPlayerView(
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

    androidx.compose.runtime.LaunchedEffect(playerViewRef) {
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
        ExoPlayerView(
            player = uiState.player,
            uiState = uiState,
            viewModel = viewModel,
            showPip = true,
            onToggleFullscreen = onToggleFullscreen,
            onShowSpeedDialog = onShowSpeedDialog,
            onShowAbLoopDialog = onShowAbLoopDialog,
            onPlayerViewCreated = { pv ->
                playerViewRef = pv
                onPlayerViewCreated(pv)
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

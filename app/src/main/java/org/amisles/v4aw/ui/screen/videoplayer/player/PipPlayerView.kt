package org.amisles.v4aw.ui.screen.videoplayer.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun PipPlayerView(
    uiState: VideoPlayerUiState,
    onPlayerViewCreated: (PlayerView) -> Unit
) {
    ExoPlayerView(
        player = uiState.player,
        uiState = uiState,
        useController = false,
        onPlayerViewCreated = onPlayerViewCreated,
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
    )
}

package org.amisles.v4aw.ui.screen.videoplayer.player

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import org.amisles.v4aw.R
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerViewModel
import org.amisles.v4aw.ui.screen.videoplayer.utils.setupPlayerViewButtons
import org.amisles.v4aw.ui.screen.videoplayer.utils.updatePlayerViewSpeed

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ExoPlayerView(
    player: ExoPlayer?,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel? = null,
    useController: Boolean = true,
    controllerTimeoutMs: Int = 3000,
    showPip: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onShowSpeedDialog: () -> Unit = {},
    onShowAbLoopDialog: () -> Unit = {},
    onPlayerViewCreated: (PlayerView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

    AndroidView(
        factory = { ctx ->
            LayoutInflater.from(ctx).inflate(R.layout.custom_player_view, null, false).also { view ->
                val playerView = view.findViewById<PlayerView>(R.id.player_view)
                playerView.player = player
                playerView.useController = useController
                playerView.controllerShowTimeoutMs = controllerTimeoutMs
                if (useController) playerView.showController()
                playerView.setKeepContentOnPlayerReset(true)
                onPlayerViewCreated(playerView)

                if (useController && viewModel != null) {
                    setupPlayerViewButtons(
                        view = view,
                        uiState = uiState,
                        viewModel = viewModel,
                        activity = activity,
                        onToggleFullscreen = onToggleFullscreen,
                        onShowSpeedDialog = onShowSpeedDialog,
                        onShowAbLoopDialog = onShowAbLoopDialog
                    )
                    if (!showPip) {
                        view.findViewById<View>(R.id.exo_pip_btn)?.visibility = View.GONE
                    }
                } else {
                    view.findViewById<View>(R.id.exo_fullscreen_btn)?.visibility = View.GONE
                    view.findViewById<View>(R.id.exo_speed_btn)?.visibility = View.GONE
                    view.findViewById<View>(R.id.exo_pip_btn)?.visibility = View.GONE
                    view.findViewById<View>(R.id.exo_ab_loop_btn)?.visibility = View.GONE
                }
            } as View
        },
        update = { view ->
            if (useController && viewModel != null) {
                updatePlayerViewSpeed(view, uiState)
            }
        },
        modifier = modifier
    )
}

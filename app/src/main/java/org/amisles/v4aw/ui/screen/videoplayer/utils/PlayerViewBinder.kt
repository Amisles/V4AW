package org.amisles.v4aw.ui.screen.videoplayer.utils

import android.app.Activity
import android.os.Build
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import org.amisles.v4aw.R
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerUiState
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerViewModel

internal fun setupPlayerViewButtons(
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

internal fun updatePlayerViewSpeed(
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

internal fun formatSpeedText(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) {
        "${speed.toInt()}x"
    } else {
        "${speed}x"
    }
}

internal fun formatTimeMs(ms: Long): String {
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

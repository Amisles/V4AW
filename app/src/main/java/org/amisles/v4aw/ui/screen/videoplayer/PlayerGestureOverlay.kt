package org.amisles.v4aw.ui.screen.videoplayer

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class GestureType {
    NONE, BRIGHTNESS, VOLUME, SEEK
}

@Composable
fun PlayerGestureOverlay(
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel,
    enabled: Boolean = true,
    isControllerVisible: Boolean = false,
    onTap: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    var gestureType by remember { mutableStateOf(GestureType.NONE) }
    var brightnessValue by remember { mutableFloatStateOf(0.5f) }
    var volumeValue by remember { mutableFloatStateOf(0.5f) }
    var seekOffsetMs by remember { mutableLongStateOf(0L) }
    var showOverlay by remember { mutableStateOf(false) }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }

    LaunchedEffect(Unit) {
        activity?.window?.attributes?.screenBrightness?.let { brightness ->
            if (brightness > 0) brightnessValue = brightness
        }
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) volumeValue = current.toFloat() / max
        }
    }

    Box(modifier = modifier) {
        content()

        if (enabled && !isControllerVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        val touchSlop = viewConfiguration.touchSlop.toFloat()

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val downPos = down.position
                            var totalDragX = 0f
                            var totalDragY = 0f
                            var isGestureDecided = false
                            var currentGesture = GestureType.NONE
                            var lastPos = downPos
                            var lastEventConsumed = false

                            do {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull() ?: continue
                                lastEventConsumed = change.isConsumed

                                val currentPos = change.position
                                val dx = currentPos.x - lastPos.x
                                val dy = currentPos.y - lastPos.y
                                totalDragX = currentPos.x - downPos.x
                                totalDragY = currentPos.y - downPos.y
                                lastPos = currentPos

                                if (!isGestureDecided) {
                                    val absTotalX = abs(totalDragX)
                                    val absTotalY = abs(totalDragY)

                                    if (absTotalX > touchSlop || absTotalY > touchSlop) {
                                        isGestureDecided = true
                                        if (absTotalX > absTotalY) {
                                            currentGesture = GestureType.SEEK
                                            seekOffsetMs = 0L
                                        } else {
                                            currentGesture = if (downPos.x < size.width / 2f) {
                                                GestureType.BRIGHTNESS
                                            } else {
                                                GestureType.VOLUME
                                            }
                                        }
                                        gestureType = currentGesture
                                        showOverlay = true
                                    }
                                }

                                if (isGestureDecided) {
                                    change.consume()
                                    when (currentGesture) {
                                        GestureType.SEEK -> {
                                            seekOffsetMs = (totalDragX * 60000L / size.width).toLong()
                                        }
                                        GestureType.BRIGHTNESS -> {
                                            val delta = -dy / size.height * 2f
                                            brightnessValue = (brightnessValue + delta).coerceIn(0f, 1f)
                                            activity?.window?.attributes = activity?.window?.attributes?.apply {
                                                screenBrightness = brightnessValue
                                            }
                                        }
                                        GestureType.VOLUME -> {
                                            val delta = -dy / size.height * 2f
                                            volumeValue = (volumeValue + delta).coerceIn(0f, 1f)
                                            audioManager?.let { am ->
                                                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                                val newVol = (volumeValue * max).toInt().coerceIn(0, max)
                                                am.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                            }
                                        }
                                        GestureType.NONE -> {}
                                    }
                                }
                            } while (event.changes.any { it.pressed })

                            if (!isGestureDecided && !lastEventConsumed) {
                                onTap()
                            } else if (isGestureDecided) {
                                if (currentGesture == GestureType.SEEK && seekOffsetMs != 0L) {
                                    val currentPos = viewModel.getCurrentPosition()
                                    val duration = viewModel.getDuration()
                                    val newPos = (currentPos + seekOffsetMs).coerceIn(0L, duration)
                                    viewModel.seekTo(newPos)
                                }
                                scope.launch {
                                    delay(600)
                                    showOverlay = false
                                    gestureType = GestureType.NONE
                                    seekOffsetMs = 0L
                                }
                            }
                        }
                    }
            )
        }

        AnimatedVisibility(
            visible = showOverlay && gestureType != GestureType.NONE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureFeedbackOverlay(
                gestureType = gestureType,
                brightnessValue = brightnessValue,
                volumeValue = volumeValue,
                seekOffsetMs = seekOffsetMs
            )
        }
    }
}

@Composable
private fun GestureFeedbackOverlay(
    gestureType: GestureType,
    brightnessValue: Float,
    volumeValue: Float,
    seekOffsetMs: Long
) {
    Box(
        modifier = Modifier
            .size(width = 160.dp, height = 120.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .background(Color(0xCC000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            when (gestureType) {
                GestureType.BRIGHTNESS -> {
                    val icon = when {
                        brightnessValue < 0.33f -> Icons.Default.BrightnessLow
                        brightnessValue < 0.66f -> Icons.Default.BrightnessMedium
                        else -> Icons.Default.BrightnessHigh
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { brightnessValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.White,
                        trackColor = Color(0x40FFFFFF),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(brightnessValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                GestureType.VOLUME -> {
                    val icon = when {
                        volumeValue == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                        volumeValue < 0.33f -> Icons.AutoMirrored.Filled.VolumeMute
                        volumeValue < 0.66f -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { volumeValue },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.White,
                        trackColor = Color(0x40FFFFFF),
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${(volumeValue * 100).toInt()}%",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                GestureType.SEEK -> {
                    val isForward = seekOffsetMs > 0
                    Icon(
                        imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val seconds = seekOffsetMs / 1000
                    val text = if (isForward) "+${seconds}s" else "${seconds}s"
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                GestureType.NONE -> {}
            }
        }
    }
}

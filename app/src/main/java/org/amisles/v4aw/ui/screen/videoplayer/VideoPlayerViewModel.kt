package org.amisles.v4aw.ui.screen.videoplayer

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.domain.usecase.GetVideoSourceUseCase
import org.amisles.v4aw.domain.usecase.ParseVideoUrlUseCase
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.i18n.Strings
import org.amisles.v4aw.player.PictureInPictureManager
import javax.inject.Inject

data class VideoPlayerUiState(
    val videoInfo: VideoInfo? = null,
    val player: ExoPlayer? = null,
    val errorMessage: String? = null,
    val availableSources: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isFullscreen: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isPictureInPictureSupported: Boolean = false,
    val isInPictureInPicture: Boolean = false
)

@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getVideoSourceUseCase: GetVideoSourceUseCase,
    private val parseVideoUrlUseCase: ParseVideoUrlUseCase,
    private val pipManager: PictureInPictureManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()
    
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var currentUrl: String? = null
    private var isReleasing = false
    
    init {
        _uiState.value = _uiState.value.copy(
            isPictureInPictureSupported = pipManager.isPictureInPictureSupported()
        )
    }
    
    private val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)

    private val dataSourceFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    
    fun initializePlayer(videoInfo: VideoInfo) {
        viewModelScope.launch {

            stopCurrentPlayback()

            if (videoInfo.videoSources.isEmpty()) {
                parseAndPlayVideo(videoInfo.url, videoInfo.title)
            } else {
                _uiState.value = _uiState.value.copy(videoInfo = videoInfo)
                playVideoFromInfo(videoInfo)
            }
        }
    }
    
    fun parseVideoEntry(videoEntry: VideoEntry) {
        viewModelScope.launch {
            stopCurrentPlayback()
            parseAndPlayVideo(videoEntry.url, videoEntry.title)
        }
    }
    
    private suspend fun parseAndPlayVideo(url: String, title: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        
        val result = parseVideoUrlUseCase(url)
        
        if (result is ParseResult.Success) {
            val fullVideoInfo = result.videoInfo
            
            _uiState.value = _uiState.value.copy(
                videoInfo = fullVideoInfo,
                isLoading = false
            )
            
            playVideoFromInfo(fullVideoInfo)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = (result as? ParseResult.Error)?.message ?: Strings.current.errorParseFailed
            )
        }
    }
    
    private suspend fun playVideoFromInfo(videoInfo: VideoInfo) {
        val videoUrl = getVideoSourceUseCase(videoInfo)
        if (videoUrl != null) {
            currentUrl = videoUrl
            setupOrUpdatePlayer(videoUrl)
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = Strings.current.noPlayableSource,
                availableSources = videoInfo.videoSources
            )
        }
    }

    private fun stopCurrentPlayback() {
        player?.let { existingPlayer ->
            existingPlayer.stop()
            existingPlayer.clearMediaItems()
        }
        _uiState.value = _uiState.value.copy(player = null)
    }

    private fun setupOrUpdatePlayer(url: String) {
        val currentPlayer = player ?: run {
            ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
                .build()
                .also { newPlayer ->
                    playerListener = object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            handlePlaybackError(error, url)
                        }
                    }
                    newPlayer.addListener(playerListener!!)
                    player = newPlayer
                    pipManager.setPlayer(newPlayer)
                }
        }

        currentPlayer.stop()
        currentPlayer.clearMediaItems()

        val mediaSource = createMediaSource(url)
        currentPlayer.setMediaSource(mediaSource)
        currentPlayer.prepare()
        currentPlayer.play()

        _uiState.value = _uiState.value.copy(player = currentPlayer)
    }
    
    private fun handlePlaybackError(error: PlaybackException, url: String) {
        if (isReleasing) {
            return
        }
        
        val lowerUrl = url.lowercase()
        
        val fallbackSources = mutableListOf<MediaSource>()
        
        if (lowerUrl.contains(".m3u8")) {
            fallbackSources.add(createProgressiveMediaSource(url))
        }
        
        if (fallbackSources.isNotEmpty()) {
            player?.let { currentPlayer ->
                if (isReleasing) return
                currentPlayer.stop()
                currentPlayer.clearMediaItems()
                currentPlayer.setMediaSource(fallbackSources.first())
                currentPlayer.prepare()
                currentPlayer.play()
                _uiState.value = _uiState.value.copy(errorMessage = Strings.current.tryingFallback)
                return
            }
        }
        
        _uiState.value = _uiState.value.copy(
            errorMessage = Strings.current.playbackError.format(error.message),
            availableSources = _uiState.value.videoInfo?.videoSources ?: emptyList()
        )
    }
    
    private fun createMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        val lowerUrl = url.lowercase()
        
        return when {
            lowerUrl.contains(".m3u8") -> {
                HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            lowerUrl.contains(".mpd") -> {
                DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
            }
            else -> {
                createProgressiveMediaSource(url)
            }
        }
    }
    
    private fun createProgressiveMediaSource(url: String): MediaSource {
        val mediaItem = MediaItem.fromUri(url)
        return ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
    }
    
    fun playVideo(url: String) {
        currentUrl = url
        setupOrUpdatePlayer(url)
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        player?.let { currentPlayer ->
            currentPlayer.setPlaybackSpeed(speed)
        }
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun seekTo(positionMs: Long) {
        player?.let { currentPlayer ->
            currentPlayer.seekTo(positionMs)
        }
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return player?.duration ?: 0L
    }
    
    fun releasePlayer() {
        isReleasing = true

        _uiState.value = _uiState.value.copy(player = null)

        player?.let { currentPlayer ->
            try {
                currentPlayer.stop()
                currentPlayer.clearMediaItems()
                playerListener?.let {
                    currentPlayer.removeListener(it)
                    playerListener = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                currentPlayer.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        player = null
        pipManager.clearPlayer()
        isReleasing = false
    }
    
    fun onPictureInPictureModeChanged(isInPictureInPicture: Boolean) {
        pipManager.onPictureInPictureModeChanged(isInPictureInPicture)
        _uiState.value = _uiState.value.copy(
            isInPictureInPicture = isInPictureInPicture,
            isFullscreen = if (isInPictureInPicture) false else _uiState.value.isFullscreen
        )
    }
    
    fun enterPictureInPictureMode(activity: Activity): Boolean {
        val currentPlayer = player ?: return false
        pipManager.setPlayer(currentPlayer)
        return pipManager.enterPictureInPictureMode(activity)
    }
    
    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
}

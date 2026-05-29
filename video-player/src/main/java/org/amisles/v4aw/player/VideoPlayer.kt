package org.amisles.v4aw.player

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class VideoPlayerState(
    val player: ExoPlayer? = null,
    val errorMessage: String? = null,
    val isPlaying: Boolean = false
)

@UnstableApi @Singleton
class VideoPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _uiState = MutableStateFlow(VideoPlayerState())
    val uiState: StateFlow<VideoPlayerState> = _uiState.asStateFlow()
    
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var isReleasing = false
    
    private val httpDataSourceFactory: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        .setConnectTimeoutMs(15000)
        .setReadTimeoutMs(15000)
        .setAllowCrossProtocolRedirects(true)
    
    private val dataSourceFactory: DefaultDataSource.Factory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    
    fun playVideo(url: String) {
        stopCurrentPlayback()
        setupOrUpdatePlayer(url)
    }
    
    fun stopCurrentPlayback() {
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
                        
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                        }
                    }
                    newPlayer.addListener(playerListener!!)
                    player = newPlayer
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
        if (isReleasing) return
        
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
                return
            }
        }
        
        _uiState.value = _uiState.value.copy(
            errorMessage = error.message
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
    
    fun release() {
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
            }
            try {
                currentPlayer.release()
            } catch (e: Exception) {
            }
        }
        player = null
        isReleasing = false
    }
}

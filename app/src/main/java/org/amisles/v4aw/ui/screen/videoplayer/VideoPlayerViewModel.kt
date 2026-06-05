package org.amisles.v4aw.ui.screen.videoplayer

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.data.cache.ParseResultCache
import org.amisles.v4aw.model.PageType
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.domain.usecase.GetVideoSourceUseCase
import org.amisles.v4aw.domain.usecase.ParseVideoUrlUseCase
import org.amisles.v4aw.domain.usecase.SearchUseCase
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.i18n.Strings
import org.amisles.v4aw.player.PictureInPictureManager
import org.amisles.v4aw.player.VideoPlayer
import android.content.Context
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
    val isInPictureInPicture: Boolean = false,
    val abLoopA: Long? = null,
    val abLoopB: Long? = null,
    val isAbLoopActive: Boolean = false,
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
    val originalVideoEntries: List<VideoEntry> = emptyList(),
    val originalSearchEndpoints: List<SearchEndpoint> = emptyList(),
    val isSearchResultMode: Boolean = false,
    val navigateToResourceBrowser: VideoInfo? = null
)

@UnstableApi
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getVideoSourceUseCase: GetVideoSourceUseCase,
    private val parseVideoUrlUseCase: ParseVideoUrlUseCase,
    private val searchUseCase: SearchUseCase,
    private val pipManager: PictureInPictureManager,
    private val videoPlayer: VideoPlayer,
    private val parseResultCache: ParseResultCache
) : ViewModel() {
    private val _uiState = MutableStateFlow(VideoPlayerUiState())
    val uiState: StateFlow<VideoPlayerUiState> = _uiState.asStateFlow()

    private var currentUrl: String? = null
    private var abLoopJob: Job? = null

    init {
        _uiState.value = _uiState.value.copy(
            isPictureInPictureSupported = pipManager.isPictureInPictureSupported()
        )

        videoPlayer.setErrorListener(object : VideoPlayer.ErrorListener {
            override fun onTryingFallback() {
                _uiState.value = _uiState.value.copy(errorMessage = Strings.current.tryingFallback)
            }

            override fun onPlaybackError(rawMessage: String, availableSources: List<String>) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = Strings.current.playbackError.format(rawMessage),
                    availableSources = availableSources
                )
            }
        })
    }

    fun initializePlayer(videoInfo: VideoInfo) {
        viewModelScope.launch {
            videoPlayer.stopCurrentPlayback()
            _uiState.value = _uiState.value.copy(player = null)

            if (videoInfo.videoSources.isEmpty()) {
                parseAndPlayVideo(videoInfo.url, videoInfo.title)
            } else {
                _uiState.value = _uiState.value.copy(
                    videoInfo = videoInfo,
                    originalVideoEntries = videoInfo.videoEntries,
                    originalSearchEndpoints = videoInfo.searchEndpoints,
                    isSearchResultMode = false
                )
                playVideoFromInfo(videoInfo)
            }
        }
    }

    fun parseVideoEntry(videoEntry: VideoEntry) {
        viewModelScope.launch {
            videoPlayer.stopCurrentPlayback()
            _uiState.value = _uiState.value.copy(player = null)
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = parseVideoUrlUseCase(videoEntry.url)

            if (result is ParseResult.Success) {
                val info = result.videoInfo
                if (info.pageType == PageType.BROWSABLE) {
                    parseResultCache.put(info)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        navigateToResourceBrowser = info
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        videoInfo = info,
                        isLoading = false,
                        isSearchResultMode = false,
                        originalVideoEntries = info.videoEntries,
                        originalSearchEndpoints = info.searchEndpoints
                    )
                    playVideoFromInfo(info)
                }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = (result as? ParseResult.Error)?.message ?: Strings.current.errorParseFailed
                )
            }
        }
    }

    fun clearNavigateToResourceBrowser() {
        _uiState.value = _uiState.value.copy(navigateToResourceBrowser = null)
    }

    private suspend fun parseAndPlayVideo(url: String, title: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        val result = parseVideoUrlUseCase(url)

        if (result is ParseResult.Success) {
            val fullVideoInfo = result.videoInfo

            _uiState.value = _uiState.value.copy(
                videoInfo = fullVideoInfo,
                isLoading = false,
                isSearchResultMode = false
            )

            if (!_uiState.value.isSearchResultMode) {
                _uiState.value = _uiState.value.copy(
                    originalVideoEntries = fullVideoInfo.videoEntries,
                    originalSearchEndpoints = fullVideoInfo.searchEndpoints
                )
            }

            playVideoFromInfo(fullVideoInfo)
        } else {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = (result as? ParseResult.Error)?.message ?: Strings.current.errorParseFailed
            )
        }
    }

    private suspend fun playVideoFromInfo(videoInfo: VideoInfo) {
        Log.i(TAG, "[PLAY] Available sources: ${videoInfo.videoSources.size}")
        videoInfo.videoSources.forEachIndexed { i, src ->
            Log.i(TAG, "[PLAY-SRC-$i] ${src.take(200)}")
        }
        val videoUrl = getVideoSourceUseCase(videoInfo)
        if (videoUrl != null) {
            Log.i(TAG, "[PLAY] Selected URL for playback: ${videoUrl.take(200)}")
            currentUrl = videoUrl
            videoPlayer.playVideo(videoUrl, videoInfo.videoSources)
            val exoPlayer = videoPlayer.getExoPlayer()
            _uiState.value = _uiState.value.copy(player = exoPlayer)
            exoPlayer?.let { pipManager.setPlayer(it) }
        } else {
            Log.w(TAG, "[PLAY] No playable source found from ${videoInfo.videoSources.size} sources")
            _uiState.value = _uiState.value.copy(
                errorMessage = Strings.current.noPlayableSource,
                availableSources = videoInfo.videoSources
            )
        }
    }

    fun playVideo(url: String) {
        Log.i(TAG, "[PLAY] User selected URL for playback: ${url.take(200)}")
        currentUrl = url
        videoPlayer.playVideo(url, _uiState.value.videoInfo?.videoSources ?: emptyList())
        val exoPlayer = videoPlayer.getExoPlayer()
        _uiState.value = _uiState.value.copy(player = exoPlayer)
        exoPlayer?.let { pipManager.setPlayer(it) }
    }

    fun toggleFullscreen() {
        _uiState.value = _uiState.value.copy(
            isFullscreen = !_uiState.value.isFullscreen
        )
    }

    fun setPlaybackSpeed(speed: Float) {
        videoPlayer.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(playbackSpeed = speed)
    }

    fun seekTo(positionMs: Long) {
        videoPlayer.seekTo(positionMs)
    }

    fun getCurrentPosition(): Long {
        return videoPlayer.getCurrentPosition()
    }

    fun getDuration(): Long {
        return videoPlayer.getDuration()
    }

    fun releasePlayer() {
        abLoopJob?.cancel()
        abLoopJob = null

        _uiState.value = _uiState.value.copy(player = null, abLoopA = null, abLoopB = null, isAbLoopActive = false)

        videoPlayer.release()
        pipManager.clearPlayer()
    }

    fun onPictureInPictureModeChanged(isInPictureInPicture: Boolean) {
        pipManager.onPictureInPictureModeChanged(isInPictureInPicture)
        _uiState.value = _uiState.value.copy(
            isInPictureInPicture = isInPictureInPicture,
            isFullscreen = if (isInPictureInPicture) false else _uiState.value.isFullscreen
        )
    }

    fun enterPictureInPictureMode(activity: Activity): Boolean {
        val currentPlayer = videoPlayer.getExoPlayer() ?: return false
        pipManager.setPlayer(currentPlayer)
        return pipManager.enterPictureInPictureMode(activity)
    }

    fun setAbLoopA() {
        val currentPosition = videoPlayer.getCurrentPosition()
        if (currentPosition <= 0L) return
        _uiState.value = _uiState.value.copy(abLoopA = currentPosition)
        if (_uiState.value.abLoopB != null && _uiState.value.abLoopB!! > currentPosition) {
            startAbLoop()
        }
    }

    fun setAbLoopB() {
        val currentPosition = videoPlayer.getCurrentPosition()
        val a = _uiState.value.abLoopA
        if (a != null && currentPosition > a) {
            _uiState.value = _uiState.value.copy(abLoopB = currentPosition)
            startAbLoop()
        } else {
            _uiState.value = _uiState.value.copy(abLoopB = currentPosition)
        }
    }

    private fun startAbLoop() {
        abLoopJob?.cancel()
        val a = _uiState.value.abLoopA ?: return
        val b = _uiState.value.abLoopB ?: return

        _uiState.value = _uiState.value.copy(isAbLoopActive = true)

        if (videoPlayer.getCurrentPosition() < a || videoPlayer.getCurrentPosition() >= b) {
            videoPlayer.seekTo(a)
        }

        abLoopJob = viewModelScope.launch {
            while (true) {
                delay(200)
                val position = videoPlayer.getCurrentPosition()
                if (position <= 0L) break
                if (position >= b) {
                    videoPlayer.seekTo(a)
                }
            }
        }
    }

    fun searchSite(endpoint: SearchEndpoint, query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSearching = true,
                searchErrorMessage = null
            )

            val result = searchUseCase(endpoint, query)

            if (result is ParseResult.Success) {
                val fullVideoInfo = result.videoInfo

                _uiState.value = _uiState.value.copy(
                    videoInfo = fullVideoInfo,
                    isSearching = false,
                    searchErrorMessage = null,
                    isSearchResultMode = true
                )

                playVideoFromInfo(fullVideoInfo)
            } else {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchErrorMessage = (result as? ParseResult.Error)?.message ?: Strings.current.searchNoResult
                )
            }
        }
    }

    fun restoreOriginalEntries() {
        val currentVideoInfo = _uiState.value.videoInfo ?: return
        _uiState.value = _uiState.value.copy(
            videoInfo = currentVideoInfo.copy(
                videoEntries = _uiState.value.originalVideoEntries,
                searchEndpoints = _uiState.value.originalSearchEndpoints
            ),
            isSearchResultMode = false,
            searchErrorMessage = null
        )
    }

    fun clearAbLoop() {
        abLoopJob?.cancel()
        abLoopJob = null
        _uiState.value = _uiState.value.copy(
            abLoopA = null,
            abLoopB = null,
            isAbLoopActive = false
        )
    }

    fun consumeCachedVideoInfo(): VideoInfo? {
        return parseResultCache.consume()
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    companion object {
        private const val TAG = "VideoPlayerVM"
    }
}

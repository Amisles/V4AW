package org.amisles.v4aw.ui.screen.resourcebrowser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.data.cache.ParseResultCache
import org.amisles.v4aw.domain.usecase.ParseVideoUrlUseCase
import org.amisles.v4aw.domain.usecase.SearchUseCase
import org.amisles.v4aw.i18n.Strings
import org.amisles.v4aw.model.PageType
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.model.SearchEndpoint
import org.amisles.v4aw.model.VideoEntry
import org.amisles.v4aw.model.VideoInfo
import javax.inject.Inject

data class BrowseHistoryEntry(
    val url: String,
    val title: String,
    val videoEntries: List<VideoEntry>,
    val searchEndpoints: List<SearchEndpoint>
)

data class ResourceBrowserUiState(
    val videoInfo: VideoInfo? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSearching: Boolean = false,
    val searchErrorMessage: String? = null,
    val isSearchResultMode: Boolean = false,
    val originalVideoEntries: List<VideoEntry> = emptyList(),
    val originalSearchEndpoints: List<SearchEndpoint> = emptyList(),
    val browseStack: List<BrowseHistoryEntry> = emptyList(),
    val navigateToPlayer: VideoInfo? = null
)

@HiltViewModel
class ResourceBrowserViewModel @Inject constructor(
    private val parseVideoUrlUseCase: ParseVideoUrlUseCase,
    private val searchUseCase: SearchUseCase,
    private val parseResultCache: ParseResultCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(ResourceBrowserUiState())
    val uiState: StateFlow<ResourceBrowserUiState> = _uiState.asStateFlow()

    fun initialize(videoInfo: VideoInfo) {
        _uiState.value = ResourceBrowserUiState(
            videoInfo = videoInfo,
            originalVideoEntries = videoInfo.videoEntries,
            originalSearchEndpoints = videoInfo.searchEndpoints
        )
    }

    fun initializeFromCache() {
        val cached = parseResultCache.consume()
        if (cached != null && cached.pageType == PageType.BROWSABLE) {
            initialize(cached)
        }
    }

    fun parseVideoEntry(entry: VideoEntry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val result = parseVideoUrlUseCase(entry.url)

            when {
                result is ParseResult.Success && result.videoInfo.pageType == PageType.PLAYABLE -> {
                    parseResultCache.put(result.videoInfo)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        navigateToPlayer = result.videoInfo
                    )
                }
                result is ParseResult.Success && result.videoInfo.pageType == PageType.BROWSABLE -> {
                    pushToStack()
                    _uiState.value = _uiState.value.copy(
                        videoInfo = result.videoInfo,
                        originalVideoEntries = result.videoInfo.videoEntries,
                        originalSearchEndpoints = result.videoInfo.searchEndpoints,
                        isSearchResultMode = false,
                        isLoading = false,
                        isSearching = false,
                        searchErrorMessage = null
                    )
                }
                result is ParseResult.Success -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = Strings.current.noPlayableSource
                    )
                }
                else -> {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = (result as? ParseResult.Error)?.message
                            ?: Strings.current.errorParseFailed
                    )
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
                val info = result.videoInfo
                _uiState.value = _uiState.value.copy(
                    videoInfo = info,
                    isSearching = false,
                    searchErrorMessage = null,
                    isSearchResultMode = true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSearching = false,
                    searchErrorMessage = (result as? ParseResult.Error)?.message
                        ?: Strings.current.searchNoResult
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

    fun navigateBack(): Boolean {
        val stack = _uiState.value.browseStack
        if (stack.isEmpty()) return false

        val previous = stack.last()
        _uiState.value = _uiState.value.copy(
            videoInfo = VideoInfo(
                url = previous.url,
                title = previous.title,
                videoEntries = previous.videoEntries,
                searchEndpoints = previous.searchEndpoints,
                pageType = PageType.BROWSABLE
            ),
            originalVideoEntries = previous.videoEntries,
            originalSearchEndpoints = previous.searchEndpoints,
            browseStack = stack.dropLast(1),
            isSearchResultMode = false,
            searchErrorMessage = null,
            isLoading = false,
            errorMessage = null
        )
        return true
    }

    fun clearNavigateToPlayer() {
        _uiState.value = _uiState.value.copy(navigateToPlayer = null)
    }

    private fun pushToStack() {
        val currentInfo = _uiState.value.videoInfo ?: return
        val entry = BrowseHistoryEntry(
            url = currentInfo.url,
            title = currentInfo.title,
            videoEntries = _uiState.value.originalVideoEntries,
            searchEndpoints = _uiState.value.originalSearchEndpoints
        )
        _uiState.value = _uiState.value.copy(
            browseStack = _uiState.value.browseStack + entry
        )
    }
}

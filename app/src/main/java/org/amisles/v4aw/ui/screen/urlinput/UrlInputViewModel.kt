package org.amisles.v4aw.ui.screen.urlinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import org.amisles.v4aw.model.HistoryItem
import org.amisles.v4aw.model.ParseResult
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.domain.usecase.ParseVideoUrlUseCase
import org.amisles.v4aw.domain.usecase.HistoryUseCase
import org.amisles.v4aw.i18n.Strings
import javax.inject.Inject

@HiltViewModel
class UrlInputViewModel @Inject constructor(
    private val parseVideoUrlUseCase: ParseVideoUrlUseCase,
    private val historyUseCase: HistoryUseCase
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(UrlInputUiState())
    val uiState: StateFlow<UrlInputUiState> = _uiState.asStateFlow()
    
    fun updateUrl(url: String) {
        _uiState.value = _uiState.value.copy(url = url)
    }
    
    fun parseUrl() {
        parseUrl(_uiState.value.url)
    }
    
    fun parseUrl(url: String) {
        updateUrl(url)
        
        // Validate URL
        val validationError = validateUrl(url)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(
                errorMessage = validationError
            )
            return
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null,
                parseResult = ParseResult.Loading
            )
            
            val result = parseVideoUrlUseCase(url)
            
            if (result is ParseResult.Success) {
                val fullVideoInfo = result.videoInfo.copy(
                    videoEntries = result.videoEntries
                )

                historyUseCase.saveHistory(
                    HistoryItem(
                        url = url,
                        title = result.videoInfo.title,
                        thumbnailUrl = result.videoInfo.thumbnailUrl
                    )
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    parseResult = ParseResult.Success(
                        videoInfo = fullVideoInfo,
                        videoEntries = result.videoEntries
                    )
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    parseResult = result
                )
            }
        }
    }
    
    private fun validateUrl(url: String): String? {
        if (url.isBlank()) {
            return Strings.current.errorEmptyUrl
        }
        
        return try {
            val javaUrl = java.net.URL(url)
            if (javaUrl.protocol !in listOf("http", "https")) {
                Strings.current.errorInvalidUrl
            } else {
                null
            }
        } catch (e: Exception) {
            Strings.current.errorInvalidUrl
        }
    }
    
    fun getVideoInfo(): VideoInfo? {
        return (uiState.value.parseResult as? ParseResult.Success)?.videoInfo
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
    
    fun resetParseResult() {
        _uiState.value = _uiState.value.copy(parseResult = ParseResult.Idle)
    }
}

data class UrlInputUiState(
    val url: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val parseResult: ParseResult = ParseResult.Idle
)

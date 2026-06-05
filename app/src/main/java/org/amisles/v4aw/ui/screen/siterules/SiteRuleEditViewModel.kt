package org.amisles.v4aw.ui.screen.siterules

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.domain.repository.SiteRuleRepository
import org.amisles.v4aw.model.SiteRule
import org.amisles.v4aw.model.VideoSourceRule
import org.amisles.v4aw.model.VideoEntryRule
import org.amisles.v4aw.model.SearchEndpointRule
import org.amisles.v4aw.model.WebViewConfigRule
import javax.inject.Inject

data class SiteRuleEditUiState(
    val rule: SiteRule = SiteRule(),
    val isNewRule: Boolean = true,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val nameError: String? = null,
    val patternError: String? = null
)

@HiltViewModel
class SiteRuleEditViewModel @Inject constructor(
    private val siteRuleRepository: SiteRuleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val ruleId: String? = savedStateHandle["ruleId"]

    private val _uiState = MutableStateFlow(SiteRuleEditUiState())
    val uiState: StateFlow<SiteRuleEditUiState> = _uiState.asStateFlow()

    init {
        if (ruleId != null) {
            loadRule(ruleId)
        }
    }

    private fun loadRule(id: String) {
        viewModelScope.launch {
            val rule = siteRuleRepository.getRuleById(id)
            if (rule != null) {
                _uiState.value = _uiState.value.copy(
                    rule = rule,
                    isNewRule = false
                )
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(name = name),
            nameError = null
        )
    }

    fun updateUrlPattern(pattern: String) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(urlPattern = pattern),
            patternError = null
        )
    }

    fun updatePriority(priority: Int) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(priority = priority)
        )
    }

    fun updateEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(enabled = enabled)
        )
    }

    fun updateVideoSourceRule(rule: VideoSourceRule?) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(videoSourceRule = rule)
        )
    }

    fun updateVideoEntryRule(rule: VideoEntryRule?) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(videoEntryRule = rule)
        )
    }

    fun updateSearchEndpointRule(rule: SearchEndpointRule?) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(searchEndpointRule = rule)
        )
    }

    fun updateWebViewConfig(config: WebViewConfigRule?) {
        _uiState.value = _uiState.value.copy(
            rule = _uiState.value.rule.copy(webViewConfig = config)
        )
    }

    fun saveRule() {
        val rule = _uiState.value.rule
        if (rule.name.isBlank()) {
            _uiState.value = _uiState.value.copy(nameError = "required")
            return
        }
        if (rule.urlPattern.isBlank()) {
            _uiState.value = _uiState.value.copy(patternError = "required")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            siteRuleRepository.saveRule(rule)
            _uiState.value = _uiState.value.copy(isSaving = false, isSaved = true)
        }
    }
}

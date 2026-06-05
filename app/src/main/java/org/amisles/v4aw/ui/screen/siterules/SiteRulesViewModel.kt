package org.amisles.v4aw.ui.screen.siterules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.domain.repository.SiteRuleRepository
import org.amisles.v4aw.model.SiteRule
import javax.inject.Inject

data class SiteRulesUiState(
    val rules: List<SiteRule> = emptyList(),
    val isLoading: Boolean = true,
    val showDeleteDialog: SiteRule? = null
)

@HiltViewModel
class SiteRulesViewModel @Inject constructor(
    private val siteRuleRepository: SiteRuleRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SiteRulesUiState())
    val uiState: StateFlow<SiteRulesUiState> = _uiState.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        viewModelScope.launch {
            siteRuleRepository.getAllRules().collect { rules ->
                _uiState.value = _uiState.value.copy(
                    rules = rules,
                    isLoading = false
                )
            }
        }
    }

    fun deleteRule(rule: SiteRule) {
        viewModelScope.launch {
            siteRuleRepository.deleteRule(rule)
            _uiState.value = _uiState.value.copy(showDeleteDialog = null)
        }
    }

    fun toggleRuleEnabled(rule: SiteRule) {
        viewModelScope.launch {
            siteRuleRepository.saveRule(rule.copy(enabled = !rule.enabled))
        }
    }

    fun showDeleteDialog(rule: SiteRule) {
        _uiState.value = _uiState.value.copy(showDeleteDialog = rule)
    }

    fun dismissDeleteDialog() {
        _uiState.value = _uiState.value.copy(showDeleteDialog = null)
    }
}

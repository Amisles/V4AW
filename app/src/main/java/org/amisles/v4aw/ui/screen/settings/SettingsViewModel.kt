package org.amisles.v4aw.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.amisles.v4aw.data.local.preferences.PreferencesManager
import org.amisles.v4aw.model.LlmConfig
import org.amisles.v4aw.model.LlmModel
import javax.inject.Inject

data class SettingsUiState(
    val config: LlmConfig = LlmConfig(),
    val isSaving: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.llmConfig.collect { config ->
                _uiState.value = _uiState.value.copy(config = config)
            }
        }
    }

    fun updateModel(model: LlmModel) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(model = model)
        )
    }

    fun updateApiKey(apiKey: String) {
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(apiKey = apiKey)
        )
    }

    fun saveConfig() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            preferencesManager.saveLlmConfig(_uiState.value.config)
            _uiState.value = _uiState.value.copy(isSaving = false)
        }
    }
}

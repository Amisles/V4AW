package org.amisles.v4aw.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.amisles.v4aw.data.local.preferences.PreferencesManager
import org.amisles.v4aw.i18n.translations.allTranslations
import javax.inject.Inject

@HiltViewModel
class LanguageViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val language: StateFlow<Language> = preferencesManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Language.ZH)

    fun setLanguage(language: Language) {
        viewModelScope.launch {
            preferencesManager.saveLanguage(language)
        }
    }
}

@Composable
fun LanguageProvider(
    viewModel: LanguageViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val language by viewModel.language.collectAsState()

    I18nProvider(language = language, translations = allTranslations) {
        content()
    }
}

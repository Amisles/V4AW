package org.amisles.v4aw.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.amisles.v4aw.model.LlmConfig
import org.amisles.v4aw.model.LlmModel
import org.amisles.v4aw.i18n.Language
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

object PreferencesKeys {
    val SELECTED_MODEL = stringPreferencesKey("selected_model")
    val API_KEY = stringPreferencesKey("api_key")
    val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
}

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val llmConfig: Flow<LlmConfig> = context.dataStore.data.map { preferences ->
        val modelName = preferences[PreferencesKeys.SELECTED_MODEL] ?: LlmModel.DEEPSEEK_V4_FLASH.name
        val model = try {
            LlmModel.valueOf(modelName)
        } catch (e: IllegalArgumentException) {
            LlmModel.DEEPSEEK_V4_FLASH
        }
        val apiKey = preferences[PreferencesKeys.API_KEY] ?: ""
        LlmConfig(model = model, apiKey = apiKey)
    }

    suspend fun saveLlmConfig(config: LlmConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_MODEL] = config.model.name
            preferences[PreferencesKeys.API_KEY] = config.apiKey
        }
    }

    val language: Flow<Language> = context.dataStore.data.map { preferences ->
        val langCode = preferences[PreferencesKeys.SELECTED_LANGUAGE] ?: Language.ZH.code
        Language.fromCode(langCode)
    }

    suspend fun saveLanguage(language: Language) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_LANGUAGE] = language.code
        }
    }
}

package org.amisles.v4aw.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider

object Strings {
    var current: StringProvider = DefaultStringProvider(emptyMap(), Language.ZH)
        internal set
}

@Composable
fun I18nProvider(
    language: Language,
    translations: Map<Language, Map<String, String>>,
    content: @Composable () -> Unit
) {
    val provider = remember(language, translations) {
        DefaultStringProvider(translations, language)
    }
    Strings.current = provider
    CompositionLocalProvider(
        LocalLanguage provides language,
        LocalStrings provides provider
    ) {
        content()
    }
}

package org.amisles.v4aw.i18n

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

val LocalLanguage = compositionLocalOf { Language.ZH }

val LocalStrings = staticCompositionLocalOf<StringProvider> {
    DefaultStringProvider(emptyMap(), Language.ZH)
}

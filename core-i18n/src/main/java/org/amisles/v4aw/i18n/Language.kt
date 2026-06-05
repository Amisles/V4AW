package org.amisles.v4aw.i18n

enum class Language(val code: String, val displayName: String) {
    ZH("zh", "中文"),
    EN("en", "English");

    companion object {
        fun fromCode(code: String): Language {
            return entries.find { it.code == code } ?: EN
        }

        fun fromSystemLanguage(systemLanguageCode: String): Language {
            val normalizedCode = systemLanguageCode.lowercase().take(2)
            return when (normalizedCode) {
                "zh" -> ZH
                else -> EN
            }
        }
    }
}

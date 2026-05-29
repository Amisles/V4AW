package org.amisles.v4aw.ui.screen.downloads

import androidx.compose.runtime.Composable
import org.amisles.v4aw.i18n.LocalStrings

enum class DownloadTab {
    DOWNLOADING,
    COMPLETED,
    FAILED;

    @Composable
    fun getDisplayName(): String {
        val strings = LocalStrings.current
        return when (this) {
            DOWNLOADING -> strings.downloadingTab
            COMPLETED -> strings.completedTab
            FAILED -> strings.failedTab
        }
    }
}

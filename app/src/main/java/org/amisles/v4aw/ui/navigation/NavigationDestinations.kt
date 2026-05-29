package org.amisles.v4aw.ui.navigation

sealed class Screen(val route: String) {
    data object UrlInput : Screen("url_input")
    data object VideoPlayer : Screen("video_player")
    data object History : Screen("history")
    data object Profile : Screen("profile")
    data object LlmConfig : Screen("llm_config")
    data object About : Screen("about")
    data object Downloads : Screen("downloads")
}

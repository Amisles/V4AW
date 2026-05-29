package org.amisles.v4aw.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.amisles.v4aw.model.VideoInfo
import org.amisles.v4aw.i18n.LanguageViewModel
import org.amisles.v4aw.ui.screen.downloads.DownloadsScreen
import org.amisles.v4aw.ui.screen.downloads.DownloadsViewModel
import org.amisles.v4aw.ui.screen.history.HistoryScreen
import org.amisles.v4aw.ui.screen.profile.AboutScreen
import org.amisles.v4aw.ui.screen.profile.LlmConfigScreen
import org.amisles.v4aw.ui.screen.profile.ProfileScreen
import org.amisles.v4aw.ui.screen.profile.ProfileViewModel
import org.amisles.v4aw.ui.screen.settings.SettingsViewModel
import org.amisles.v4aw.ui.screen.urlinput.UrlInputScreen
import org.amisles.v4aw.ui.screen.urlinput.UrlInputViewModel
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerScreen
import org.amisles.v4aw.ui.screen.videoplayer.VideoPlayerViewModel
import java.net.URLEncoder
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.UrlInput.route,
        modifier = modifier
    ) {
        composable(Screen.UrlInput.route) { backStackEntry ->
            val viewModel: UrlInputViewModel = hiltViewModel(backStackEntry)
            UrlInputScreen(
                viewModel = viewModel,
                onNavigateToPlayer = { videoInfo ->
                    navController.navigateToPlayer(videoInfo)
                },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(
            route = "${Screen.VideoPlayer.route}/{videoInfoJson}",
            arguments = listOf(
                navArgument("videoInfoJson") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val encodedJson = backStackEntry.arguments?.getString("videoInfoJson") ?: ""
            val videoInfoJson = try {
                URLDecoder.decode(encodedJson, StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                encodedJson
            }
            val videoInfo = Json.decodeFromString<VideoInfo>(videoInfoJson)
            val viewModel: VideoPlayerViewModel = hiltViewModel()
            VideoPlayerScreen(
                viewModel = viewModel,
                videoInfo = videoInfo,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateToPlayer = { videoInfo ->
                    navController.navigateToPlayer(videoInfo)
                }
            )
        }

        composable(Screen.Profile.route) {
            val profileViewModel: ProfileViewModel = hiltViewModel()
            val languageViewModel: LanguageViewModel = hiltViewModel()
            val uiState by profileViewModel.uiState.collectAsState()
            ProfileScreen(
                languageViewModel = languageViewModel,
                onNavigateToLlmConfig = { navController.navigate(Screen.LlmConfig.route) },
                onNavigateToHistory = {
                    navController.navigate(Screen.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onNavigateToAbout = { navController.navigate(Screen.About.route) },
                onClearCache = { profileViewModel.clearCache() },
                cacheSize = uiState.cacheSize
            )
        }

        composable(Screen.LlmConfig.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            LlmConfigScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        composable(Screen.Downloads.route) {
            val viewModel: DownloadsViewModel = hiltViewModel()
            DownloadsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToPlayer = { downloadTask ->
                    val videoInfo = org.amisles.v4aw.model.VideoInfo(
                        title = downloadTask.title,
                        url = downloadTask.filePath ?: "",
                        videoSources = listOfNotNull(downloadTask.filePath?.let { "file://$it" })
                    )
                    navController.navigateToPlayer(videoInfo)
                }
            )
        }
    }
}

fun NavHostController.navigateToPlayer(videoInfo: VideoInfo) {
    val videoInfoJson = Json.encodeToString(videoInfo)
    val encoded = URLEncoder.encode(videoInfoJson, StandardCharsets.UTF_8.name())
    navigate("${Screen.VideoPlayer.route}/$encoded")
}

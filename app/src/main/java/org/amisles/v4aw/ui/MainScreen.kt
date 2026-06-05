package org.amisles.v4aw.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import org.amisles.v4aw.data.cache.ParseResultCache
import org.amisles.v4aw.ui.components.BottomNavigationBar
import org.amisles.v4aw.ui.navigation.NavGraph

@Composable
fun MainScreen(
    navController: NavHostController,
    parseResultCache: ParseResultCache,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            BottomNavigationBar(navController = navController)
        }
    ) { paddingValues ->
        NavGraph(
            navController = navController,
            parseResultCache = parseResultCache,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

package org.amisles.v4aw.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import org.amisles.v4aw.i18n.LocalStrings
import org.amisles.v4aw.ui.navigation.Screen

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(Screen.UrlInput.route, Icons.Default.Home)
    data object History : BottomNavItem(Screen.History.route, Icons.Default.History)
    data object Downloads : BottomNavItem(Screen.Downloads.route, Icons.Default.Download)
    data object Profile : BottomNavItem(Screen.Profile.route, Icons.Default.Person)
}

@Composable
fun BottomNavItem.getDisplayTitle(): String {
    val strings = LocalStrings.current
    return when (this) {
        is BottomNavItem.Home -> strings.home
        is BottomNavItem.History -> strings.history
        is BottomNavItem.Downloads -> strings.downloads
        is BottomNavItem.Profile -> strings.profile
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.hierarchy?.any { destination ->
        destination.route in listOf(
            BottomNavItem.Home.route,
            BottomNavItem.History.route,
            BottomNavItem.Downloads.route,
            BottomNavItem.Profile.route
        )
    } == true

    if (showBottomBar) {
        NavigationBar(
            modifier = modifier
        ) {
            NavigationItem(
                item = BottomNavItem.Home,
                isSelected = currentDestination?.hierarchy?.any { it.route == BottomNavItem.Home.route } == true,
                onClick = {
                    navController.navigate(BottomNavItem.Home.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            NavigationItem(
                item = BottomNavItem.History,
                isSelected = currentDestination?.hierarchy?.any { it.route == BottomNavItem.History.route } == true,
                onClick = {
                    navController.navigate(BottomNavItem.History.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            NavigationItem(
                item = BottomNavItem.Downloads,
                isSelected = currentDestination?.hierarchy?.any { it.route == BottomNavItem.Downloads.route } == true,
                onClick = {
                    navController.navigate(BottomNavItem.Downloads.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
            NavigationItem(
                item = BottomNavItem.Profile,
                isSelected = currentDestination?.hierarchy?.any { it.route == BottomNavItem.Profile.route } == true,
                onClick = {
                    navController.navigate(BottomNavItem.Profile.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

@Composable
fun RowScope.NavigationItem(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = isSelected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = item.icon,
                contentDescription = item.getDisplayTitle()
            )
        },
        label = {
            Text(text = item.getDisplayTitle())
        }
    )
}

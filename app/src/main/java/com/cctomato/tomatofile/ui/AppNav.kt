package com.cctomato.tomatofile.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cctomato.tomatofile.ui.browse.BrowseScreen
import com.cctomato.tomatofile.ui.cache.CacheScreen
import com.cctomato.tomatofile.ui.home.HomeScreen
import com.cctomato.tomatofile.ui.nav.Routes
import com.cctomato.tomatofile.ui.player.VlcPlayerScreen
import com.cctomato.tomatofile.ui.preview.PreviewScreen
import com.cctomato.tomatofile.ui.settings.SettingsScreen
import com.cctomato.tomatofile.ui.source.AddSourceScreen
import com.cctomato.tomatofile.ui.viewer.ImageViewerScreen

@Composable
fun AppNav() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            fadeIn(tween(180)) + slideInHorizontally(tween(220)) { full -> full / 12 }
        },
        exitTransition = { fadeOut(tween(130)) },
        popEnterTransition = { fadeIn(tween(180)) },
        popExitTransition = { fadeOut(tween(130)) }
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSource = { source ->
                    navController.navigate(Routes.browse(source.id, "/"))
                },
                onAddSource = { navController.navigate(Routes.addSource()) },
                onEditSource = { source ->
                    navController.navigate(Routes.addSource(source.id))
                },
                onOpenCache = { navController.navigate(Routes.CACHE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(
            route = Routes.ADD_SOURCE,
            arguments = listOf(
                navArgument("editId") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) { entry ->
            val editId = entry.arguments?.getString("editId").orEmpty()
            AddSourceScreen(
                editId = editId,
                onBack = { navController.popBackStack() },
                onSaved = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Routes.BROWSE,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            BrowseScreen(
                onBack = { navController.popBackStack() },
                onOpenPreview = { sourceId, path ->
                    navController.navigate(Routes.preview(sourceId, path))
                },
                onOpenViewer = { sourceId, path ->
                    navController.navigate(Routes.viewer(sourceId, path))
                },
                onOpenPlayer = { sourceId, path ->
                    navController.navigate(Routes.player(sourceId, path))
                }
            )
        }

        composable(
            route = Routes.PLAYER,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            VlcPlayerScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.PREVIEW,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            PreviewScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.VIEWER,
            arguments = listOf(
                navArgument("sourceId") { type = NavType.StringType },
                navArgument("path") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            ImageViewerScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.CACHE) {
            CacheScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenCache = { navController.navigate(Routes.CACHE) }
            )
        }
    }
}
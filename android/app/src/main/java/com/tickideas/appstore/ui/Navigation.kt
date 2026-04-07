package com.tickideas.appstore.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "apps") {
        composable("apps") {
            AppListScreen(
                onAppClick = { appId ->
                    navController.navigate("apps/$appId")
                }
            )
        }

        composable("apps/{appId}") { backStackEntry ->
            val appId = backStackEntry.arguments?.getString("appId") ?: return@composable
            AppDetailScreen(
                appId = appId,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

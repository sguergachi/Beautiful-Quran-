package com.beautifulquran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beautifulquran.ui.AppViewModelFactory
import com.beautifulquran.ui.home.HomeScreen
import com.beautifulquran.ui.home.HomeViewModel
import com.beautifulquran.ui.reader.ReaderScreen
import com.beautifulquran.ui.reader.ReaderViewModel
import com.beautifulquran.ui.settings.SettingsScreen
import com.beautifulquran.ui.settings.SettingsViewModel
import com.beautifulquran.ui.theme.BeautifulQuranTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as QuranApp

        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle()

            BeautifulQuranTheme(themeMode = settings.themeMode) {
                val navController = rememberNavController()
                // Sheets are single planes viewed one at a time: navigation
                // dissolves one sheet into the next, nothing slides or stacks.
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier,
                    enterTransition = { fadeIn(tween(350)) },
                    exitTransition = { fadeOut(tween(350)) },
                    popEnterTransition = { fadeIn(tween(350)) },
                    popExitTransition = { fadeOut(tween(350)) },
                ) {
                    composable("home") {
                        val vm: HomeViewModel = viewModel(factory = AppViewModelFactory)
                        HomeScreen(
                            viewModel = vm,
                            onOpenSurah = { surahId -> navController.navigate("reader/$surahId") },
                        )
                    }
                    composable("reader/{surahId}") { backStackEntry ->
                        val surahId =
                            backStackEntry.arguments?.getString("surahId")?.toIntOrNull() ?: 1
                        val vm: ReaderViewModel = viewModel(factory = AppViewModelFactory)
                        ReaderScreen(
                            surahId = surahId,
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { navController.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        val vm: SettingsViewModel = viewModel(factory = AppViewModelFactory)
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

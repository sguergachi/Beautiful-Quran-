package com.beautifulquran

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
                NavHost(
                    navController = navController,
                    startDestination = "home",
                    modifier = Modifier,
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
                        )
                    }
                }
            }
        }
    }
}

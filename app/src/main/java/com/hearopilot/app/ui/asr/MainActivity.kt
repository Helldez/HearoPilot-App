package com.hearopilot.app.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.hearopilot.app.ui.ui.theme.LibellulaTheme
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.ThemeMode
import com.hearopilot.app.domain.model.OnboardingStep
import com.hearopilot.app.presentation.settings.SettingsViewModel
import com.hearopilot.app.ui.screens.*
import com.hearopilot.app.presentation.setup.SetupViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel

const val TAG = "sherpa-onnx-sim-asr"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Read theme mode from settings
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()

            val isDark = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Re-apply transparent nav bar whenever theme mode changes,
            // so the correct icon color (light/dark) is set.
            LaunchedEffect(isDark) {
                enableEdgeToEdge(
                    navigationBarStyle = if (isDark) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            LibellulaTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LibellulaApp()
                }
            }
        }
    }
}

/**
 * Main navigation composable for Libellula app.
 *
 * Manages navigation flow:
 * 1. Onboarding flow (first-time):
 *    - Welcome screen
 *    - STT model setup
 *    - LLM model download (optional)
 * 2. Sessions screen (list of all transcription sessions)
 * 3. Recording screen (active transcription for a session)
 * 4. Session details screen (view completed session)
 * 5. Settings screen (configuration)
 */
@Composable
fun LibellulaApp() {
    val navController = rememberNavController()
    val setupViewModel: SetupViewModel = hiltViewModel()

    val isOnboardingComplete by setupViewModel.isOnboardingComplete.collectAsStateWithLifecycle()
    val currentStep by setupViewModel.currentStep.collectAsStateWithLifecycle()
    val llmDownloadState by setupViewModel.llmDownloadState.collectAsStateWithLifecycle()
    val sttDownloadState by setupViewModel.sttDownloadState.collectAsStateWithLifecycle()

    // Determine start destination
    val startDestination = if (isOnboardingComplete) "sessions" else "onboarding"

    // Navigate to sessions screen when onboarding completes
    LaunchedEffect(isOnboardingComplete) {
        if (isOnboardingComplete && navController.currentDestination?.route == "onboarding") {
            navController.navigate("sessions") {
                popUpTo("onboarding") { inclusive = true }
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable("onboarding") {
            if (currentStep == OnboardingStep.COMPLETED) {
                // Should not be reached, but handle gracefully
                LaunchedEffect(Unit) {
                    navController.navigate("sessions") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
                return@composable
            }

            OnboardingScreen(
                currentStep = currentStep,
                sttDownloadState = sttDownloadState,
                llmDownloadState = llmDownloadState,
                canGoBack = setupViewModel.canGoBack(),
                onGetStarted = { setupViewModel.proceedToNextStep() },
                onContinue = { setupViewModel.proceedToNextStep() },
                onStartStt = { setupViewModel.startSttDownload() },
                onRetryStt = { setupViewModel.retrySttDownload() },
                onStartLlm = { setupViewModel.startLlmDownload() },
                onRetryLlm = { setupViewModel.retryLlmDownload() },
                onSkipLlm = { setupViewModel.skipLlmDownload() },
                onGoBack = { setupViewModel.goToPreviousStep() }
            )
        }

        // Sessions list screen (new home screen)
        composable("sessions") {
            SessionsScreen(
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToSessionDetails = { sessionId ->
                    navController.navigate("session_details/$sessionId")
                },
                onNavigateToRecording = { sessionId ->
                    navController.navigate("recording/$sessionId")
                },
                onNavigateToSearch = {
                    navController.navigate("search")
                }
            )
        }

        // Search screen
        composable("search") {
            SearchScreen(
                onNavigateBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() },
                onNavigateToSession = { sessionId, highlightId, initialTab ->
                    val route = buildString {
                        append("session_details/$sessionId?")
                        if (highlightId != null) append("highlightId=$highlightId&")
                        append("initialTab=$initialTab")
                    }
                    navController.navigate(route)
                }
            )
        }

        // Recording screen for a specific session
        composable(
            route = "recording/{sessionId}",
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
        ) {
            MainScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToLlmDownload = {
                    navController.navigate("llm_download_standalone")
                }
            )
        }

        // LLM download screen (can be accessed after onboarding if user skipped)
        composable("llm_download_standalone") {
            // Pop back only when a download started FROM this screen completes.
            // llmDownloadState may already be Completed (leftover from onboarding),
            // so we guard with a local flag set by onStartDownload / onRetry.
            var downloadStartedHere by remember { mutableStateOf(false) }

            LaunchedEffect(llmDownloadState) {
                if (downloadStartedHere &&
                    llmDownloadState is DownloadState.Completed &&
                    navController.currentDestination?.route == "llm_download_standalone"
                ) {
                    navController.popBackStack()
                }
            }

            LlmDownloadScreen(
                downloadState = llmDownloadState,
                onStartDownload = {
                    downloadStartedHere = true
                    setupViewModel.startLlmDownload()
                },
                onSkip = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onRetry = {
                    downloadStartedHere = true
                    setupViewModel.retryLlmDownload()
                },
                isStandalone = true
            )
        }

        // Session details screen — optional query params for search deep-linking and initial tab.
        composable(
            route = "session_details/{sessionId}?highlightId={highlightId}&initialTab={initialTab}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.StringType },
                navArgument("highlightId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("initialTab") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) {
            SessionDetailsScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToSession = { newSessionId ->
                    // Navigate to the new copy session and open the AI Insights tab directly.
                    navController.navigate("session_details/$newSessionId?initialTab=1") {
                        popUpTo("session_details/{sessionId}?highlightId={highlightId}&initialTab={initialTab}") {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    if (navController.previousBackStackEntry != null) navController.popBackStack()
                },
                onNavigateToLlmDownload = {
                    navController.navigate("llm_download_standalone")
                },
                onNavigateToLicenses = {
                    navController.navigate("licenses")
                }
            )
        }

        composable("licenses") {
            LicensesScreen(
                onNavigateBack = { if (navController.previousBackStackEntry != null) navController.popBackStack() }
            )
        }
    }
}
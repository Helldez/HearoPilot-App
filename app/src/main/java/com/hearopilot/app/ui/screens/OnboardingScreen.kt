package com.hearopilot.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hearopilot.app.domain.model.DownloadState
import com.hearopilot.app.domain.model.OnboardingStep
import com.hearopilot.app.ui.components.OnboardingPageIndicator
import com.hearopilot.app.ui.icons.AppIcons

private const val ONBOARDING_PAGE_COUNT = 4

/** Maps an [OnboardingStep] to its zero-based dot-indicator page index. */
private fun OnboardingStep.pageIndex(): Int = when (this) {
    OnboardingStep.WELCOME -> 0
    OnboardingStep.LANGUAGES -> 1
    OnboardingStep.STT_DOWNLOAD -> 2
    OnboardingStep.LLM_DOWNLOAD -> 3
    OnboardingStep.COMPLETED -> -1  // No indicator on completed step
}

/**
 * Container for the onboarding flow.
 *
 * Wraps the per-step screens with:
 * - Slide + fade [AnimatedContent] transitions (direction-aware: forward = slide left,
 *   back = slide right)
 * - [OnboardingPageIndicator] dots at the bottom
 * - Back [IconButton] at the top-left when [canGoBack] is true
 *
 * The COMPLETED step is never shown here (navigation happens in MainActivity).
 */
@Composable
fun OnboardingScreen(
    currentStep: OnboardingStep,
    sttDownloadState: DownloadState,
    llmDownloadState: DownloadState,
    canGoBack: Boolean,
    onGetStarted: () -> Unit,
    onContinue: () -> Unit,
    onStartStt: () -> Unit,
    onRetryStt: () -> Unit,
    onStartLlm: () -> Unit,
    onRetryLlm: () -> Unit,
    onSkipLlm: () -> Unit,
    onGoBack: () -> Unit
) {
    // Track previous step to determine slide direction
    var previousStep by remember { mutableStateOf(currentStep) }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.pageIndex() >= initialState.pageIndex()
                val slideIn = slideInHorizontally(
                    initialOffsetX = { width -> if (forward) width else -width }
                ) + fadeIn()
                val slideOut = slideOutHorizontally(
                    targetOffsetX = { width -> if (forward) -width else width }
                ) + fadeOut()
                slideIn togetherWith slideOut
            },
            label = "onboarding_step"
        ) { step ->
            // Update previous step tracker after transition
            previousStep = step

            when (step) {
                OnboardingStep.WELCOME -> WelcomeScreen(onGetStarted = onGetStarted)
                OnboardingStep.LANGUAGES -> SupportedLanguagesScreen(onContinue = onContinue)
                OnboardingStep.STT_DOWNLOAD -> SttDownloadScreen(
                    downloadState = sttDownloadState,
                    onStartDownload = onStartStt,
                    onRetry = onRetryStt,
                    onContinue = onContinue
                )
                OnboardingStep.LLM_DOWNLOAD -> LlmDownloadScreen(
                    downloadState = llmDownloadState,
                    onStartDownload = onStartLlm,
                    onSkip = onSkipLlm,
                    onRetry = onRetryLlm
                )
                OnboardingStep.COMPLETED -> {
                    // Handled by MainActivity — show nothing here
                }
            }
        }

        // Page-dot indicator — shown for all steps except COMPLETED
        val pageIndex = currentStep.pageIndex()
        if (pageIndex >= 0) {
            OnboardingPageIndicator(
                totalPages = ONBOARDING_PAGE_COUNT,
                currentPage = pageIndex,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(bottom = 24.dp)
            )
        }

        // Back arrow — shown when the step allows going back
        if (canGoBack) {
            IconButton(
                onClick = onGoBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .safeDrawingPadding()
                    .padding(start = 8.dp, top = 8.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = AppIcons.Back,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

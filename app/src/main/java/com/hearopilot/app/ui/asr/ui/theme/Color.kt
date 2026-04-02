package com.hearopilot.app.ui.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Material 3 Color Palette for d.ai transcription
 * Generated from brand seed color #5E17EB (purple from logo)
 *
 * Design philosophy:
 * - Color restraint: purple only for primary actions
 * - Warm neutrals for professional, calm interface
 * - High contrast for accessibility (WCAG AA minimum)
 */

// Brand seed color
val BrandPurple = Color(0xFF5E17EB)
val BrandPurpleLight = Color(0xFF8149F2)  // Lighter shade for gradients
val BrandPurpleDark = Color(0xFF4A0FD4)   // Darker shade for depth

// Light Mode - Primary Palette (Purple)
val md_theme_light_primary = Color(0xFF5E17EB)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFE7DEFF)
val md_theme_light_onPrimaryContainer = Color(0xFF21005E)

// Light Mode - Surfaces & Backgrounds
val md_theme_light_background = Color(0xFFF8F9FA)  // Warm white, not pure white
val md_theme_light_onBackground = Color(0xFF1C1B1F)
val md_theme_light_surface = Color(0xFFFFFFFF)
val md_theme_light_onSurface = Color(0xFF1C1B1F)
val md_theme_light_surfaceVariant = Color(0xFFE7E0EC)
val md_theme_light_onSurfaceVariant = Color(0xFF49454F)

// Light Mode - Surface Containers (for layering)
val md_theme_light_surfaceContainer = Color(0xFFF3EDF7)  // Subtle purple tint
val md_theme_light_surfaceContainerHigh = Color(0xFFECE6F0)
val md_theme_light_surfaceContainerHighest = Color(0xFFE6E0E9)
val md_theme_light_surfaceContainerLow = Color(0xFFF7F2FA)
val md_theme_light_surfaceContainerLowest = Color(0xFFFFFFFF)

// Light Mode - Outline & Borders
val md_theme_light_outline = Color(0xFF79747E)
val md_theme_light_outlineVariant = Color(0xFFCAC4D0)

// Light Mode - Error
val md_theme_light_error = Color(0xFFEF4444)
val md_theme_light_errorContainer = Color(0xFFF9DEDC)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_onErrorContainer = Color(0xFF8C1D18)

// Light Mode - Secondary & Tertiary (generated from purple)
val md_theme_light_secondary = Color(0xFF625B71)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFFE8DEF8)
val md_theme_light_onSecondaryContainer = Color(0xFF1D192B)

val md_theme_light_tertiary = Color(0xFF7D5260)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD8E4)
val md_theme_light_onTertiaryContainer = Color(0xFF31111D)

// Dark Mode - Primary Palette
val md_theme_dark_primary = Color(0xFFCFBCFF)
val md_theme_dark_onPrimary = Color(0xFF381E72)
val md_theme_dark_primaryContainer = Color(0xFF4F378B)
val md_theme_dark_onPrimaryContainer = Color(0xFFE7DEFF)

// Dark Mode - Surfaces & Backgrounds (OLED-friendly)
val md_theme_dark_background = Color(0xFF1C1B1F)
val md_theme_dark_onBackground = Color(0xFFE6E1E5)
val md_theme_dark_surface = Color(0xFF1C1B1F)
val md_theme_dark_onSurface = Color(0xFFE6E1E5)
val md_theme_dark_surfaceVariant = Color(0xFF49454F)
val md_theme_dark_onSurfaceVariant = Color(0xFFCAC4D0)

// Dark Mode - Surface Containers
val md_theme_dark_surfaceContainer = Color(0xFF2B2930)
val md_theme_dark_surfaceContainerHigh = Color(0xFF36343B)
val md_theme_dark_surfaceContainerHighest = Color(0xFF413F46)
val md_theme_dark_surfaceContainerLow = Color(0xFF1C1B1F)
val md_theme_dark_surfaceContainerLowest = Color(0xFF0F0D13)

// Dark Mode - Outline & Borders
val md_theme_dark_outline = Color(0xFF938F99)
val md_theme_dark_outlineVariant = Color(0xFF49454F)

// Dark Mode - Error
val md_theme_dark_error = Color(0xFFF87171)
val md_theme_dark_errorContainer = Color(0xFF8C1D18)
val md_theme_dark_onError = Color(0xFF601410)
val md_theme_dark_onErrorContainer = Color(0xFFF9DEDC)

// Dark Mode - Secondary & Tertiary
val md_theme_dark_secondary = Color(0xFFCCC2DC)
val md_theme_dark_onSecondary = Color(0xFF332D41)
val md_theme_dark_secondaryContainer = Color(0xFF4A4458)
val md_theme_dark_onSecondaryContainer = Color(0xFFE8DEF8)

val md_theme_dark_tertiary = Color(0xFFEFB8C8)
val md_theme_dark_onTertiary = Color(0xFF492532)
val md_theme_dark_tertiaryContainer = Color(0xFF633B48)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD8E4)

// Functional colors (custom, not in M3 spec)
val Success = Color(0xFF10B981)
val Warning = Color(0xFFF59E0B)

// Legacy colors (kept for backward compatibility during transition)
// TODO: Replace usages with MaterialTheme.colorScheme equivalents
val White = Color(0xFFFFFFFF)
val Black = Color(0xFF000000)
val OffWhite = Color(0xFFFAFAFA)
val LightGray = Color(0xFFF5F5F5)
val Gray100 = Color(0xFFE8E8E8)
val Gray200 = Color(0xFFD0D0D0)
val Gray300 = Color(0xFFB8B8B8)
val Gray400 = Color(0xFF909090)
val Gray500 = Color(0xFF6B6B6B)
val Gray600 = Color(0xFF4A4A4A)
val Gray700 = Color(0xFF2E2E2E)
val Gray800 = Color(0xFF1A1A1A)
val Gray900 = Color(0xFF0D0D0D)
val AccentPrimary = Gray700
val AccentSecondary = Gray500
val AccentSuccess = Color(0xFF4CAF50)
val AccentError = Color(0xFFE53935)
val AccentWarning = Color(0xFFFFA726)
val BrandPrimary = BrandPurple  // Alias

/**
 * Gradient brushes for brand elements
 * Subtle gradients add depth while maintaining minimal aesthetic
 */

// Primary gradient (top-left to bottom-right, violet to lighter violet)
val PrimaryGradient = Brush.linearGradient(
    colors = listOf(BrandPurpleLight, BrandPurple),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Primary vertical gradient (top to bottom)
val PrimaryGradientVertical = Brush.verticalGradient(
    colors = listOf(BrandPurpleLight, BrandPurple)
)

// Accent gradient with more contrast (for FAB, CTAs)
val AccentGradient = Brush.linearGradient(
    colors = listOf(BrandPurple, BrandPurpleDark),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// ── Recording mode colors ────────────────────────────────────────────────────
// Each mode has a distinct identity: icon background gradient + flat tint for labels.

// Simple Listening — sky blue (calm, passive, listening)
val ModeSkyBlueLight = Color(0xFF38BDF8)
val ModeSkyBlueDark  = Color(0xFF0284C7)
val ModeSkyBlueTint  = Color(0xFF0EA5E9)
val ModeSimpleListeningGradient = Brush.linearGradient(
    colors = listOf(ModeSkyBlueLight, ModeSkyBlueDark),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Short Meeting — brand purple (meetings are the core use case)
val ModeShortMeetingGradient = PrimaryGradient
val ModeShortMeetingTint = BrandPrimary

// Long Meeting — amber/orange (extended, high-intensity, important)
val ModeAmberLight = Color(0xFFFBBF24)
val ModeAmberDark  = Color(0xFFD97706)
val ModeAmberTint  = Color(0xFFF59E0B)
val ModeLongMeetingGradient = Brush.linearGradient(
    colors = listOf(ModeAmberLight, ModeAmberDark),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// Real-Time Translation — emerald green (bridge between languages)
val ModeEmeraldLight = Color(0xFF34D399)
val ModeEmeraldDark  = Color(0xFF059669)
val ModeEmeraldTint  = Color(0xFF10B981)
val ModeTranslationGradient = Brush.linearGradient(
    colors = listOf(ModeEmeraldLight, ModeEmeraldDark),
    start = Offset(0f, 0f),
    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
)

// ── Download immersive screen ────────────────────────────────────────────────
/** Deep navy used as the bottom stop of the full-screen download gradient. */
val DownloadImmersiveNavy = Color(0xFF0A0A1E)

/** Full-screen vertical gradient for the immersive downloading state. */
val DownloadImmersiveGradient = Brush.verticalGradient(
    colors = listOf(BrandPurpleDark, DownloadImmersiveNavy)
)

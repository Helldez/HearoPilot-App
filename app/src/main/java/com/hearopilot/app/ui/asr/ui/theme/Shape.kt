package com.hearopilot.app.ui.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Centralised shape scale for HearoPilot.
 *
 * Bumps Material 3 defaults upward to match the modern, rounded aesthetic
 * seen in contemporary productivity apps (2025-2026):
 *   medium   12 dp → 16 dp  (cards, dialogs, text fields)
 *   large    16 dp → 20 dp  (bottom sheets, larger containers)
 *
 * All other tiers keep the M3 defaults.
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

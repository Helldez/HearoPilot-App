package com.hearopilot.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.hearopilot.app.ui.ui.theme.BrandPurpleDark
import com.hearopilot.app.ui.ui.theme.White

/**
 * Primary button with gradient background
 * Uses brand purple gradient for premium look
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush = SolidColor(BrandPurpleDark),
    textColor: Color = White,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .background(
                brush = if (enabled) gradient else Brush.linearGradient(
                    colors = listOf(Color.Gray, Color.Gray)
                ),
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = textColor,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = textColor.copy(alpha = 0.6f)
            ),
            contentPadding = PaddingValues(vertical = 16.dp),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                disabledElevation = 0.dp
            )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

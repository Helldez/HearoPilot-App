package com.hearopilot.app.ui

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Help : NavRoutes("help")
}
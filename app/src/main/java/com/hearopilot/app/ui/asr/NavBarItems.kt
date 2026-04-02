package com.hearopilot.app.ui

import com.hearopilot.app.ui.icons.AppIcons

object NavBarItems {
    val BarItems = listOf(
        BarItem(
            title = "Home",
            image = AppIcons.Home,
            route = "home",
        ),
        BarItem(
            title = "Help",
            image = AppIcons.Info,
            route = "help",
        ),
    )
}
package com.mimora.app.navigation

import kotlinx.serialization.Serializable

sealed interface Screen {
    val route: String

    data object Home : Screen { override val route = "home" }
    data object Calendar : Screen { override val route = "calendar" }
    data object Tasks : Screen { override val route = "tasks" }
    data object Statistics : Screen { override val route = "statistics" }
    data object Settings : Screen { override val route = "settings" }
}

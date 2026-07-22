package com.focusgate.launcher.schedule

import kotlinx.serialization.Serializable

/** The app categories the daily schedule reasons about. */
@Serializable
enum class Category {
    CALLS, MESSAGING, MUSIC, WORK, SOCIAL, VIDEO, GAMES, OTHER;

    val label: String
        get() = when (this) {
            CALLS -> "Calls"
            MESSAGING -> "Messaging"
            MUSIC -> "Music"
            WORK -> "Work"
            SOCIAL -> "Social"
            VIDEO -> "Video"
            GAMES -> "Games"
            OTHER -> "Other"
        }
}

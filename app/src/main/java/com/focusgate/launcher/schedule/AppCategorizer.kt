package com.focusgate.launcher.schedule

import android.content.Context
import android.content.pm.ApplicationInfo

/**
 * Suggests a default [Category] for an app from well-known package names and the
 * app's declared [ApplicationInfo.category], so the user only *adjusts* tags on
 * the categorize screen rather than assigning every app from scratch.
 */
object AppCategorizer {

    // Substring → category. First match wins; ordered most-specific first.
    private val packageHints: List<Pair<String, Category>> = listOf(
        "whatsapp" to Category.MESSAGING,
        "telegram" to Category.MESSAGING,
        "messenger" to Category.MESSAGING,
        "signal" to Category.MESSAGING,
        "messaging" to Category.MESSAGING,
        "dialer" to Category.CALLS,
        "incallui" to Category.CALLS,
        "contacts" to Category.CALLS,
        "spotify" to Category.MUSIC,
        ".music" to Category.MUSIC,
        "youtube" to Category.VIDEO,
        "netflix" to Category.VIDEO,
        "primevideo" to Category.VIDEO,
        "disney" to Category.VIDEO,
        "instagram" to Category.SOCIAL,
        "facebook" to Category.SOCIAL,
        "snapchat" to Category.SOCIAL,
        "twitter" to Category.SOCIAL,
        "reddit" to Category.SOCIAL,
        "tiktok" to Category.SOCIAL,
        "gmail" to Category.WORK,
        "outlook" to Category.WORK,
        "docs" to Category.WORK,
        "slack" to Category.WORK,
        "teams" to Category.WORK,
        "calendar" to Category.WORK,
        "notion" to Category.WORK,
    )

    /** Best-effort default with no PackageManager lookup (used as the runtime fallback). */
    fun defaultFor(pkg: String): Category {
        val lower = pkg.lowercase()
        return packageHints.firstOrNull { lower.contains(it.first) }?.second ?: Category.OTHER
    }

    /** Richer suggestion that also consults the app's declared store category. */
    fun suggest(context: Context, pkg: String): Category {
        val lower = pkg.lowercase()
        packageHints.firstOrNull { lower.contains(it.first) }?.let { return it.second }
        val declared = runCatching {
            context.packageManager.getApplicationInfo(pkg, 0).category
        }.getOrDefault(ApplicationInfo.CATEGORY_UNDEFINED)
        return when (declared) {
            ApplicationInfo.CATEGORY_GAME -> Category.GAMES
            ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
            ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> Category.SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.WORK
            else -> Category.OTHER
        }
    }
}

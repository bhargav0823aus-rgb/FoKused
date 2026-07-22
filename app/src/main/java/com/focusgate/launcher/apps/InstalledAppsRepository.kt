package com.focusgate.launcher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlin.math.abs

data class InstalledApp(val label: String, val packageName: String)

/**
 * Enumerates launchable apps and fuzzy-matches the agent's free-text "app"
 * field back to a real package.
 */
class InstalledAppsRepository(private val context: Context) {

    fun launchableApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        // MATCH_ALL + the QUERY_ALL_PACKAGES permission: without both, Android 11+
        // package-visibility filtering hides most third-party apps from us.
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                launcherIntent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
        }
        return resolved.asSequence()
            .map { InstalledApp(it.loadLabel(pm).toString().trim(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName && it.label.isNotEmpty() }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    /**
     * Match order: exact package name, exact label, prefix, substring, package
     * fragment, then edit distance <= 2 (catches model typos like "Yotube").
     * Below the 55-score floor we return null rather than guess.
     */
    fun match(query: String, apps: List<InstalledApp> = launchableApps()): InstalledApp? {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return null
        apps.firstOrNull { it.packageName.equals(trimmed, ignoreCase = true) }?.let { return it }

        val q = normalize(trimmed)
        if (q.isEmpty()) return null

        var best: InstalledApp? = null
        var bestScore = 0
        for (app in apps) {
            val label = normalize(app.label)
            val score = when {
                label == q -> 100
                label.startsWith(q) || q.startsWith(label) -> 85
                label.contains(q) || q.contains(label) -> 70
                app.packageName.lowercase().contains(q) -> 60
                editDistance(label, q) <= 2 -> 55
                else -> 0
            }
            if (score > bestScore) {
                bestScore = score
                best = app
            }
        }
        return if (bestScore >= 55) best else null
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** Levenshtein with a single rolling row; early-out when lengths differ by >2. */
    private fun editDistance(a: String, b: String): Int {
        if (abs(a.length - b.length) > 2) return 3
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + if (a[i - 1] == b[j - 1]) 0 else 1
                )
                prev = tmp
            }
        }
        return dp[b.length]
    }
}

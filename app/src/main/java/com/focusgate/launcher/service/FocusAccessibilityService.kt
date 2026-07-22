package com.focusgate.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.focusgate.launcher.MainActivity
import com.focusgate.launcher.schedule.Category
import com.focusgate.launcher.schedule.ScheduleRepository

/**
 * Total lockdown. The chat + paying coins is the ONLY door into any app, so ANY
 * foreground app that isn't the currently-approved (paid) session gets ejected
 * straight back to FoKused — catching recents, notifications, and other launchers.
 * A small safe-list (system UI, Settings, dialer/telecom/emergency, home launchers,
 * FoKused, and CALLS-category apps) is never touched, so the user can't be trapped
 * or blocked from a phone call.
 *
 * Runs in the same process as [ScheduleRepository], so it reads the live session
 * state directly — no IPC.
 */
class FocusAccessibilityService : AccessibilityService() {

    private val repo by lazy { ScheduleRepository.getInstance(this) }

    // Every home/launcher app on the device — never eject these, or Home would
    // just bounce back into an eject loop.
    private val homePackages: Set<String> by lazy {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching {
            packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    private var lastEjectedPkg: String? = null
    private var lastEjectAt: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        // Use the ACTIVE window's package — the real foreground app — NOT the event's
        // package. WINDOW_STATE_CHANGED also fires for the keyboard, popups and system
        // overlays that sit on top of the app you are actually using; reacting to those
        // was ejecting the user out of a legit app (and cascading via the Home action).
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return

        // Anti-lockout exemptions.
        if (pkg == packageName) return
        if (pkg in SAFE_PACKAGES || pkg in homePackages) return
        if (repo.categoryOf(pkg) == Category.CALLS) return       // never block phone/emergency
        if (repo.isActiveSession(pkg)) return                    // a session the gate granted

        // Debounce repeated events for the same package.
        val now = System.currentTimeMillis()
        if (pkg == lastEjectedPkg && now - lastEjectAt < 1500L) return

        // Total lockdown: anything that reached here isn't a paid session, so eject.
        lastEjectedPkg = pkg
        lastEjectAt = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        // Land the user back on the gate rather than a bare home screen.
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
        }
    }

    override fun onInterrupt() {}

    companion object {
        // Core system surfaces we must never eject from.
        private val SAFE_PACKAGES = setOf(
            "com.android.settings",
            "com.android.systemui",
            "com.google.android.dialer",
            "com.samsung.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.emergency",
        )
    }
}

package com.focusgate.launcher

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.focusgate.launcher.timer.FocusTimerService
import com.focusgate.launcher.ui.ChatScreen
import com.focusgate.launcher.ui.ChatViewModel
import com.focusgate.launcher.ui.theme.FocusGateTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Optional: the timer runs either way; without it the countdown
            // notification just stays hidden.
        }

    private val homeRoleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshHomeStatus()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // A home screen must never close on Back — swallow the gesture entirely.
        onBackPressedDispatcher.addCallback(this) { /* no-op */ }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        handleExpiry(intent)

        setContent {
            FocusGateTheme {
                ChatScreen(
                    viewModel = viewModel,
                    onRequestDefaultHome = ::requestDefaultHome,
                    onEnableBlocking = ::openAccessibilitySettings,
                )
            }
        }
    }

    // launchMode="singleTask": the expiry notification's full-screen intent
    // re-enters the existing instance here rather than creating a new one.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExpiry(intent)
    }

    override fun onResume() {
        super.onResume()
        // The user may have just come back from the role dialog / Settings. Also the
        // first resume of a new day is where we decide to ask for a fresh plan.
        viewModel.refreshHomeStatus()
        viewModel.refreshBlockingStatus()
        viewModel.refreshSetup()
        // Bank coins earned while the screen was off and celebrate them in chat.
        viewModel.settleEarnings()
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    override fun onStop() {
        super.onStop()
        // Left the launcher (opened an app, pressed Home, or the screen locked).
        // Wipe the chat so returning to FocusGate always starts a clean gate.
        // Resetting on leave (not on return) keeps any "time's up" message the
        // expiry intent adds when it brings us back.
        viewModel.reset()
    }

    private fun handleExpiry(intent: Intent?) {
        val label = intent?.getStringExtra(FocusTimerService.EXTRA_EXPIRED_LABEL) ?: return
        intent.removeExtra(FocusTimerService.EXTRA_EXPIRED_LABEL)
        viewModel.onTimerExpired(label)
    }

    private fun requestDefaultHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                // System sheet: "Set FocusGate as your default home app?" —
                // the smoothest path on One UI 7.
                homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                return
            }
        }
        // Fallback: open the Default home app settings screen directly.
        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }
}

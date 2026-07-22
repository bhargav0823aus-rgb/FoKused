package com.focusgate.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.focusgate.launcher.schedule.ScheduleRepository

/**
 * Hosts the coin-earning clock. While the process is alive we watch the screen:
 * going OFF starts the timer, coming back ON banks 1 coin per whole minute it was
 * off. The timestamp is persisted, so even if the process dies mid-sleep the coins
 * are settled on the next screen-on or app resume.
 */
class FoKusedApplication : Application() {

    private val repo by lazy { ScheduleRepository.getInstance(this) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> repo.onScreenOff()
                Intent.ACTION_SCREEN_ON -> repo.settleScreenOff()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
    }
}

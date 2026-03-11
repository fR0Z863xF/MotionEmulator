package com.zhufucdev.motion_emulator

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Application
import android.os.Build
import com.zhufucdev.motion_emulator.data.MotionRecorder
import com.zhufucdev.motion_emulator.data.TelephonyRecorder
import com.zhufucdev.motion_emulator.plugin.Plugins
import com.zhufucdev.motion_emulator.provider.EmulationMonitorWorker

class MeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Plugins.init(this)
        MotionRecorder.init(this)
        TelephonyRecorder.init(this)
        registerNotificationChannels()
    }

    private fun registerNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            EmulationMonitorWorker.CHANNEL_ID,
            getString(R.string.title_channel_emulation),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.text_channel_emulation)
        }
        manager.createNotificationChannel(channel)
    }
}
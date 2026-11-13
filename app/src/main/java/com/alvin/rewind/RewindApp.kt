package com.alvin.rewind

import android.app.Application
import com.alvin.rewind.background.WorkScheduler
import com.alvin.rewind.di.AppContainer
import com.alvin.rewind.ui.notification.DigestNotifier

class RewindApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DigestNotifier.createChannel(this)
        WorkScheduler.scheduleCollection(this)
        WorkScheduler.scheduleWeeklyDigest(this, container.clock)
    }
}

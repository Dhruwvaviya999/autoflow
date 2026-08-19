package com.dhruw.autoflow

import android.app.Application
import com.dhruw.autoflow.di.AppContainer

class AutoFlowApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        // Passive receiver/callback registration only — cheap, no polling,
        // no services. Needed so system triggers work however the process
        // comes up (activity, notification listener, boot, workers).
        container.systemMonitorHub.start()
    }
}

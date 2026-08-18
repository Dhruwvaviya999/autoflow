package com.dhruw.autoflow

import android.app.Application
import com.dhruw.autoflow.di.AppContainer

class AutoFlowApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
}

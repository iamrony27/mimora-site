package com.mimora.app

import android.app.Application
import com.mimora.app.core.di.AppContainer

class MimoraApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

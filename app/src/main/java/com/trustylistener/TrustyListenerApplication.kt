package com.trustylistener

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Application class with Hilt dependency injection
 */
@HiltAndroidApp
class TrustyListenerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize any app-wide components here
    }
}

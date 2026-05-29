package com.indagalab.agentos

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

/**
 * Application entry point. Boots the embedded Python (Chaquopy) runtime once,
 * in the main app process, before any service or activity needs it.
 */
class AgentOSApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
    }
}

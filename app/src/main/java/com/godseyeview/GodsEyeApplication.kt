package com.godseyeview

import android.app.Application
import com.godseyeview.crash.CrashHandler

class GodsEyeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}

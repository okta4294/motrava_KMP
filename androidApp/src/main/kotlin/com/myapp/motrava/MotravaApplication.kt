package com.myapp.motrava

import android.app.Application
import android.content.pm.ApplicationInfo
import com.myapp.motrava.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MotravaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        System.setProperty("java.net.preferIPv4Stack", "true")
        
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        initKoin {
            if (isDebuggable) {
                androidLogger()
            }
            androidContext(this@MotravaApplication)
        }
    }
}

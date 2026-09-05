package com.myapp.motrava

import android.app.Application
import com.myapp.motrava.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class MotravaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        System.setProperty("java.net.preferIPv4Stack", "true")
        
        initKoin {
            androidLogger()
            androidContext(this@MotravaApplication)
        }
    }
}

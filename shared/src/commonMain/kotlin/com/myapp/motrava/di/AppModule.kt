package com.myapp.motrava.di

import com.myapp.motrava.data.local.TokenManager
import com.myapp.motrava.presentation.notification.NotificationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
// import androidx.room.Room
// import com.myapp.motrava.data.local.MotravaDatabase
// import org.koin.android.ext.koin.androidContext

import com.russhwolf.settings.Settings

val appModule = module {
    single { TokenManager(get()) }
    
    single { get<com.myapp.motrava.data.local.MotravaDatabase>().notificationDao }
    single { get<com.myapp.motrava.data.local.MotravaDatabase>().locationPointDao }
    
    // viewModel { NotificationViewModel(get()) }
}


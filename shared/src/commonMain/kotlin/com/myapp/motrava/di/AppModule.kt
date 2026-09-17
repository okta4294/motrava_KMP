package com.myapp.motrava.di

import com.myapp.motrava.data.local.MotravaDatabase
import com.myapp.motrava.data.local.TokenManager
import org.koin.dsl.module

val appModule = module {
    single { TokenManager(get()) }
    single { get<MotravaDatabase>().notificationDao }
    single { get<MotravaDatabase>().locationPointDao }
}

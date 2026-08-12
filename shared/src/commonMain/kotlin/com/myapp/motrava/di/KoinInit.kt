package com.myapp.motrava.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    startKoin {
        appDeclaration()
        modules(
            platformModule(),
            networkModule,
            repositoryModule,
            appModule,
            viewModelModule
        )
    }
}

// iOS initialization
fun initKoin() = initKoin {}

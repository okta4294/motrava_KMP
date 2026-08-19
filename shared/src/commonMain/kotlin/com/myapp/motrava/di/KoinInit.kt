package com.myapp.motrava.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

private var isKoinStarted = false

fun initKoin(appDeclaration: KoinAppDeclaration = {}) {
    if (!isKoinStarted) {
        isKoinStarted = true
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
}

// iOS initialization
fun doInitKoin() = initKoin {}
fun initKoin() = initKoin {}

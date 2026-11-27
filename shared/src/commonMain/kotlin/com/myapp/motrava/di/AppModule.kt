package com.myapp.motrava.di

import com.myapp.motrava.data.local.LocationPointDao
import com.myapp.motrava.data.local.MotravaDatabase
import com.myapp.motrava.data.local.NotificationDao
import com.myapp.motrava.data.local.TokenManager
import com.myapp.motrava.domain.manager.TripSessionManager
import com.myapp.motrava.presentation.trip.clearTripServiceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.dsl.module

val appModule = module {
    single {
        TokenManager(
            settings = get(),
            onClearLocalData = {
                clearTripServiceSession()
                get<TripSessionManager>().setTripInactive()
                val locationPointDao = get<LocationPointDao>()
                val notificationDao = get<NotificationDao>()
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { locationPointDao.deleteAll() }
                    runCatching { notificationDao.clearAll() }
                }
            }
        )
    }
    single { get<MotravaDatabase>().notificationDao }
    single { get<MotravaDatabase>().locationPointDao }
}

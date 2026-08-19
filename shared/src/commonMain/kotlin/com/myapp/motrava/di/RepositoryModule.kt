package com.myapp.motrava.di

import com.myapp.motrava.data.repository.AuthRepository
import com.myapp.motrava.data.repository.TripRepository
import com.myapp.motrava.data.repository.VehicleRepository
import com.myapp.motrava.data.repository.ServiceReminderRepository
import com.myapp.motrava.domain.manager.TripSessionManager
import org.koin.dsl.module

val repositoryModule = module {
    single { AuthRepository(get(), get()) }
    single { TripRepository(get()) }
    single { VehicleRepository(get()) }
    single { ServiceReminderRepository(get()) }
    single { TripSessionManager() }
}

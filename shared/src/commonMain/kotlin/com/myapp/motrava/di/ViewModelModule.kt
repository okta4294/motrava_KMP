package com.myapp.motrava.di

import com.myapp.motrava.presentation.auth.AuthViewModel
import com.myapp.motrava.presentation.dashboard.DashboardViewModel
import com.myapp.motrava.presentation.notification.NotificationViewModel
import com.myapp.motrava.presentation.profile.ProfileViewModel
import com.myapp.motrava.presentation.service.ServiceReminderViewModel
import com.myapp.motrava.presentation.trip.TripDetailViewModel
import com.myapp.motrava.presentation.trip.TripViewModel
import com.myapp.motrava.presentation.vehicle.VehicleViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val viewModelModule = module {
    viewModelOf(::AuthViewModel)
    viewModelOf(::DashboardViewModel)
    viewModelOf(::NotificationViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::ServiceReminderViewModel)
    viewModelOf(::TripDetailViewModel)
    viewModelOf(::TripViewModel)
    viewModelOf(::VehicleViewModel)
}

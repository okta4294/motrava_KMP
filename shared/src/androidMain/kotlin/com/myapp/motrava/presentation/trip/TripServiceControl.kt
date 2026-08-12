package com.myapp.motrava.presentation.trip

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import org.koin.core.context.GlobalContext
import com.myapp.motrava.service.LocationTrackingService

actual fun startTripService(tripId: String, vehicleId: String) {
    val context = GlobalContext.get().get<Context>()
    val intent = Intent(context, LocationTrackingService::class.java).apply {
        action = LocationTrackingService.ACTION_START
        putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId)
        putExtra(LocationTrackingService.EXTRA_VEHICLE_ID, vehicleId)
    }
    try {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ContextCompat.startForegroundService(context, intent)
        } else {
            android.util.Log.e("TripServiceControl", "Location permission denied. Cannot start service.")
        }
    } catch (e: Exception) {
        android.util.Log.e("TripServiceControl", "Failed to start service: ${e.message}")
    }
}

actual fun stopTripService() {
    val context = GlobalContext.get().get<Context>()
    val intent = Intent(context, LocationTrackingService::class.java).apply {
        action = LocationTrackingService.ACTION_STOP
    }
    context.startService(intent)
}

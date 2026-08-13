package com.myapp.motrava.presentation.trip

import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLLocationAccuracyBestForNavigation

private val locationManager by lazy {
    CLLocationManager().apply {
        desiredAccuracy = kCLLocationAccuracyBestForNavigation
        allowsBackgroundLocationUpdates = true
        pausesLocationUpdatesAutomatically = false
    }
}

actual fun startTripService(tripId: String, vehicleId: String) {
    locationManager.requestAlwaysAuthorization()
    locationManager.startUpdatingLocation()
}

actual fun stopTripService() {
    locationManager.stopUpdatingLocation()
}

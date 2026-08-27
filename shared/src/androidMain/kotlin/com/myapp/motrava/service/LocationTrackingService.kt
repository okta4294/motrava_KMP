package com.myapp.motrava.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import com.myapp.motrava.data.remote.dto.WsLocationMessage
import com.myapp.motrava.domain.manager.TripSessionManager
import com.myapp.motrava.data.remote.ApiService
import com.myapp.motrava.data.local.LocationPointDao
import com.myapp.motrava.data.local.LocationPointEntity
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject
import java.text.SimpleDateFormat
import java.util.*

class LocationTrackingService : Service() {

    private val apiService: ApiService by inject()
    private val locationPointDao: LocationPointDao by inject()
    private val tripSessionManager: TripSessionManager by inject()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private var tripId: String? = null

    private var lastLocation: Location? = null
    private var currentDistanceMeters: Float = 0f

    private val isoDateFormat = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs by lazy { getSharedPreferences("trip_service_prefs", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Restore state if service is recreated by OS (START_STICKY)
        currentDistanceMeters = tripSessionManager.distanceMeters.value
        val latLng = tripSessionManager.currentLatLng.value
        if (latLng != null) {
            lastLocation = Location("restored").apply {
                latitude = latLng.first
                longitude = latLng.second
                time = System.currentTimeMillis()
            }
        }
        tripId = tripSessionManager.activeTripId.value ?: prefs.getString("active_trip_id", null)

        serviceScope.launch {
            syncAllPendingTrips()
        }
    }

    private suspend fun syncAllPendingTrips() {
        val tripIds = locationPointDao.getTripsWithUnsyncedPoints()
        for (tId in tripIds) {
            val batch = locationPointDao.getUnsyncedByTrip(tId, 500)
            if (batch.isNotEmpty()) {
                val messages = batch.map { pt ->
                    WsLocationMessage(
                        tripId = tId,
                        latitude = pt.latitude, longitude = pt.longitude,
                        speed = pt.speed, heading = pt.heading,
                        accuracy = pt.accuracy, altitude = pt.altitude,
                        battery = pt.battery, timestamp = pt.timestamp
                    )
                }
                try {
                    val resp = apiService.batchUploadLocations(tId, messages)
                    if (resp.isSuccessful) {
                        locationPointDao.markAsSynced(batch.map { it.id })
                    }
                } catch (e: Exception) {
                    Log.e("LocationService", "Failed to sync pending trip $tId", e)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val newTripId = intent.getStringExtra(EXTRA_TRIP_ID)
                val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID)
                if (newTripId != null) {
                    val isResume = (tripId == newTripId && currentDistanceMeters > 0f)
                    tripId = newTripId

                    // Persist tripId and vehicleId so START_STICKY can restore
                    prefs.edit()
                        .putString("active_trip_id", newTripId)
                        .putString("active_vehicle_id", vehicleId)
                        .apply()

                    if (!isResume) {
                        stopLocationUpdates()
                        lastLocation = null
                        currentDistanceMeters = 0f
                        tripSessionManager.setTripInactive()
                        tripSessionManager.setTripActive(newTripId, vehicleId)
                    }

                    startForegroundService()
                    requestLocationUpdates()
                } else {
                    // START_STICKY restart without intent extras — restore from prefs
                    val savedTrip = prefs.getString("active_trip_id", null)
                    val savedVehicle = prefs.getString("active_vehicle_id", null)
                    if (savedTrip != null) {
                        tripId = savedTrip
                        
                        // Restore state into TripSessionManager so the UI knows we are still tracking!
                        tripSessionManager.setTripActive(savedTrip, savedVehicle)
                        
                        startForegroundService()
                        requestLocationUpdates()
                    } else {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }

            ACTION_STOP -> {
                stopLocationUpdates()

                val currentTrip = tripId
                tripId = null
                prefs.edit().remove("active_trip_id").apply()

                serviceScope.launch(kotlinx.coroutines.NonCancellable) {
                    if (currentTrip != null) {
                        // Flush all pending location points BEFORE ending the trip
                        // so the server has complete data to calculate distance
                        try {
                            val pending = locationPointDao.getUnsyncedByTrip(currentTrip, 500)
                            if (pending.isNotEmpty()) {
                                println("LocationService: [DIAG] Flushing ${pending.size} pending points before endTrip")
                                val messages = pending.map { pt ->
                                    WsLocationMessage(
                                        tripId = currentTrip,
                                        latitude = pt.latitude, longitude = pt.longitude,
                                        speed = pt.speed, heading = pt.heading,
                                        accuracy = pt.accuracy, altitude = pt.altitude,
                                        battery = pt.battery, timestamp = pt.timestamp
                                    )
                                }
                                val syncResp = apiService.batchUploadLocations(currentTrip, messages)
                                if (syncResp.isSuccessful) {
                                    locationPointDao.markAsSynced(pending.map { it.id })
                                    println("LocationService: [DIAG] Final batch sync successful (${pending.size} points)")
                                } else {
                                    println("LocationService: [DIAG] Final batch sync failed: ${syncResp.code()}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("LocationService", "Final batch sync failed", e)
                        }

                        // Now end the trip — server has all points to calculate distance
                        try {
                            apiService.endTrip(currentTrip, tripSessionManager.distanceMeters.value.toDouble())
                            println("LocationService: [DIAG] REST endTrip called successfully")
                        } catch (e: Exception) {
                            Log.e("LocationService", "REST endTrip failed, flagging for later sync", e)
                            prefs.edit().putString("pending_end_trip", currentTrip).apply()
                        }
                    }
                    tripSessionManager.setTripInactive()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        createNotificationChannel()

        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Motrava")
            .setContentText("Tracking sedang berjalan...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
            .setMinUpdateIntervalMillis(1000)
            .build()

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        // Fallback for emulators where FusedLocationProviderClient gets stuck
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                fallbackLocationListener
            )
        } catch (e: Exception) {
            Log.e("LocationService", "Failed to request LocationManager updates", e)
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        try {
            locationManager.removeUpdates(fallbackLocationListener)
        } catch (e: Exception) {}
    }

    private val fallbackLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            processLocation(location)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                processLocation(location)
            }
        }
    }

    private fun processLocation(location: Location) {
        println("LocationService: [DIAG] processLocation called. lat=${location.latitude}, lng=${location.longitude}, provider=${location.provider}")
        // Filter out 0.0, 0.0 (Maps Afrika bug)
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            println("LocationService: [DIAG] Filtered out 0,0 coordinate")
            return
        }

                // Record all points, even with low accuracy (indoors)

                var speedMps = if (location.hasSpeed()) location.speed else {
                    lastLocation?.let { last ->
                        val dist = last.distanceTo(location)
                        val timeDiff = (location.time - last.time) / 1000f
                        if (timeDiff > 0) dist / timeDiff else 0f
                    } ?: 0f
                }

                lastLocation?.let { last ->
                    currentDistanceMeters += last.distanceTo(location)
                }
                lastLocation = location

                val speedKmh = speedMps * 3.6f

                tripSessionManager.updateStats(currentDistanceMeters, speedKmh, Pair(location.latitude, location.longitude))

        println("LocationService: [DIAG] Sending to WS. distance=${currentDistanceMeters}m, speed=${speedKmh}km/h, tripId=$tripId")
        sendLocationToWebSocket(location, speedKmh)
    }

    private fun sendLocationToWebSocket(location: Location, speedKmh: Float) {
        val tid = tripId ?: return
        val timestamp = isoDateFormat.get()?.format(Date(location.time)) ?: ""

        val msg = WsLocationMessage(
            tripId = tid,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = speedKmh,
            heading = if (location.hasBearing()) location.bearing else 0f,
            accuracy = if (location.hasAccuracy()) location.accuracy else 0f,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            battery = 100,
            timestamp = timestamp
        )
        
        serviceScope.launch {
            try {
                // Save to Room
                val entity = LocationPointEntity(
                    tripId = tid,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    speed = speedKmh,
                    heading = msg.heading,
                    accuracy = msg.accuracy,
                    altitude = msg.altitude,
                    battery = msg.battery,
                    timestamp = timestamp,
                    isSynced = false
                )
                locationPointDao.insert(entity)
                
                // Immediately try to sync batch if internet is available
                val pending = locationPointDao.getUnsyncedByTrip(tid, 100)
                if (pending.isNotEmpty()) {
                    val messages = pending.map { pt ->
                        WsLocationMessage(
                            tripId = tid,
                            latitude = pt.latitude, longitude = pt.longitude,
                            speed = pt.speed, heading = pt.heading,
                            accuracy = pt.accuracy, altitude = pt.altitude,
                            battery = pt.battery, timestamp = pt.timestamp
                        )
                    }
                    val resp = apiService.batchUploadLocations(tid, messages)
                    if (resp.isSuccessful) {
                        locationPointDao.markAsSynced(pending.map { it.id })
                    }
                }
            } catch (e: Exception) {
                Log.e("LocationService", "Failed to insert and sync point", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopLocationUpdates()
        // Don't cancel immediately — give the stop coroutine time to flush & endTrip
        // serviceScope uses NonCancellable for the stop flow, so cancel is safe
        // but we add a small delay to let any in-flight work finish
        try {
            kotlinx.coroutines.runBlocking {
                withTimeout(10_000) {
                    serviceScope.coroutineContext[kotlinx.coroutines.Job]?.children?.forEach { it.join() }
                }
            }
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Trip Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "ACTION_START_TRIP"
        const val ACTION_STOP = "ACTION_STOP_TRIP"
        const val EXTRA_TRIP_ID = "EXTRA_TRIP_ID"
        const val EXTRA_VEHICLE_ID = "EXTRA_VEHICLE_ID"
        private const val CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1
    }
}

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
    private var isUsingFallbackListener = false

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
        if (currentDistanceMeters == 0f) {
            currentDistanceMeters = prefs.getFloat("saved_distance_meters", 0f)
        }
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
                        prefs.edit().putFloat("saved_distance_meters", 0f).apply()
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
                val finalDistance = currentDistanceMeters.toDouble()
                tripId = null
                prefs.edit()
                    .remove("active_trip_id")
                    .remove("active_vehicle_id")
                    .remove("saved_distance_meters")
                    .apply()

                serviceScope.launch {
                    if (currentTrip != null) {
                        try {
                            apiService.endTrip(currentTrip, finalDistance)
                            println("LocationService: [DIAG] REST endTrip called successfully. distance=${finalDistance}m")
                        } catch (e: Exception) {
                            android.util.Log.e("LocationService", "REST endTrip failed, flagging for later sync", e)
                            prefs.edit().putString("pending_end_trip", currentTrip).apply()
                        }
                    }
                    withContext(kotlinx.coroutines.NonCancellable) {
                        tripSessionManager.setTripInactive()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
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
            .setMinUpdateDistanceMeters(2f)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
            isUsingFallbackListener = false
        } catch (e: Exception) {
            Log.e("LocationService", "FusedLocationProviderClient failed, using LocationManager fallback", e)
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    2f,
                    fallbackLocationListener
                )
                isUsingFallbackListener = true
            } catch (ex: Exception) {
                Log.e("LocationService", "LocationManager fallback also failed", ex)
            }
        }
    }

    private fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {}

        if (isUsingFallbackListener) {
            try {
                locationManager.removeUpdates(fallbackLocationListener)
            } catch (e: Exception) {}
            isUsingFallbackListener = false
        }
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
        println("LocationService: [DIAG] processLocation called. lat=${location.latitude}, lng=${location.longitude}, provider=${location.provider}, accuracy=${if (location.hasAccuracy()) location.accuracy else -1f}")
        
        // 1. Filter out 0.0, 0.0 (Maps Afrika bug)
        if (location.latitude == 0.0 && location.longitude == 0.0) {
            println("LocationService: [DIAG] Filtered out 0,0 coordinate")
            return
        }

        // 2. Accuracy Filter: ignore points with inaccurate readings (> 25m)
        if (location.hasAccuracy() && location.accuracy > 25f) {
            println("LocationService: [DIAG] Filtered out inaccurate location: accuracy=${location.accuracy}m")
            return
        }

        val last = lastLocation
        var distanceDelta = 0f

        if (last != null) {
            val dist = last.distanceTo(location)
            // 3. Stationary drift filter: require minimum movement threshold to prevent noise/jitter when stationary
            val minMovementThreshold = if (location.hasAccuracy()) (location.accuracy / 3f).coerceIn(2.5f, 5f) else 2.5f
            if (dist >= minMovementThreshold) {
                distanceDelta = dist
                currentDistanceMeters += distanceDelta
                lastLocation = location
            } else {
                println("LocationService: [DIAG] Filtered out stationary jitter (dist=${dist}m < threshold=${minMovementThreshold}m)")
            }
        } else {
            lastLocation = location
        }

        // Calculate speed
        val speedMps = if (location.hasSpeed() && location.speed > 0.3f) {
            location.speed
        } else if (last != null && distanceDelta > 0f) {
            val timeDiff = (location.time - last.time) / 1000f
            if (timeDiff > 0) distanceDelta / timeDiff else 0f
        } else {
            0f
        }

        val speedKmh = speedMps * 3.6f

        // Periodically persist distance in prefs
        prefs.edit().putFloat("saved_distance_meters", currentDistanceMeters).apply()

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

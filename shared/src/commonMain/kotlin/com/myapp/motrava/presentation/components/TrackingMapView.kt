package com.myapp.motrava.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun TrackingMapView(
    liveLatLng: Pair<Double, Double>?,
    currentRoute: List<Pair<Double, Double>>,
    centerTrigger: Int,
    hasLocationPermission: Boolean,
    modifier: Modifier = Modifier
)

package com.solumetals.metrovision

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class GpsTracker(context: Context) : LocationListener {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var running by mutableStateOf(false); private set
    var distanceMeters by mutableDoubleStateOf(0.0); private set
    var accuracyMeters by mutableStateOf<Float?>(null); private set
    private var previous: Location? = null

    @SuppressLint("MissingPermission")
    fun start() {
        distanceMeters = 0.0; previous = null; running = true
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0.5f, this)
    }

    fun stop() {
        running = false
        manager.removeUpdates(this)
    }

    override fun onLocationChanged(location: Location) {
        accuracyMeters = location.accuracy
        // Reject uncertain fixes and impossible walking jumps.
        if (location.accuracy > 20f) return
        previous?.let { old ->
            val delta = old.distanceTo(location)
            val seconds = (location.elapsedRealtimeNanos - old.elapsedRealtimeNanos) / 1e9
            if (delta >= 0.8f && seconds > 0 && delta / seconds < 4.5f) distanceMeters += delta
        }
        previous = location
    }

    override fun onProviderEnabled(provider: String) = Unit
    override fun onProviderDisabled(provider: String) = Unit
    @Deprecated("Deprecated in Android") override fun onStatusChanged(p: String?, s: Int, e: Bundle?) = Unit
}

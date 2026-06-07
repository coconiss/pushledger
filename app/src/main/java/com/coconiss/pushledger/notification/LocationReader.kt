package com.coconiss.pushledger.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import com.coconiss.pushledger.data.LocationSnapshot

class LocationReader(private val context: Context) {
    fun lastKnownSnapshot(): LocationSnapshot? {
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse && !hasFine) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val candidates = manager.getProviders(true).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val best = candidates.maxByOrNull(Location::getTime) ?: return null
        return LocationSnapshot(best.latitude, best.longitude, best.accuracy.takeIf { it > 0f })
    }
}

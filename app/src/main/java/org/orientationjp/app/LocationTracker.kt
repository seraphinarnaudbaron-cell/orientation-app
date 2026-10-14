package org.orientationjp.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper

/** GPS via LocationManager (aucune dépendance Google Play Services). */
class LocationTracker(
    context: Context,
    private val onLocation: (Location) -> Unit,
    private val onProviderOff: () -> Unit,
) : LocationListener {

    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var running = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, 2000L, 2f, this, Looper.getMainLooper())
                }
            } catch (e: Exception) {
                // fournisseur indisponible : on essaie le suivant
            }
        }
        lastKnown()?.let { onLocation(it) }
    }

    fun stop() {
        running = false
        try {
            manager.removeUpdates(this)
        } catch (e: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(): Location? {
        var best: Location? = null
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                val l = manager.getLastKnownLocation(provider)
                if (l != null && (best == null || l.time > best.time)) best = l
            } catch (e: Exception) {
            }
        }
        return best
    }

    override fun onLocationChanged(location: Location) = onLocation(location)

    override fun onProviderDisabled(provider: String) = onProviderOff()

    override fun onProviderEnabled(provider: String) {}

    // Requis sur Android < 11 (méthode abstraite avant l'API 30)
    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
}

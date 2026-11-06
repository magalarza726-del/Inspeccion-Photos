package ec.espol.inspeccionphotos.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import ec.espol.inspeccionphotos.survey.CaptureProfile
import ec.espol.inspeccionphotos.survey.LocationSnapshot
import ec.espol.inspeccionphotos.survey.PositionReference

class LocationTracker(private val context: Context) {
    private val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var listener: LocationListener? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(profile: CaptureProfile, onLocation: (LocationSnapshot) -> Unit) {
        stop()
        if (!hasPermission()) return
        val minTime = if (profile == CaptureProfile.HIGH_ACCURACY) 1_000L else 3_000L
        val minDistance = if (profile == CaptureProfile.HIGH_ACCURACY) 0.5f else 1.5f
        val created = object : LocationListener {
            override fun onLocationChanged(location: Location) = onLocation(location.toSnapshot())
            @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        listener = created
        listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).forEach { provider ->
            runCatching {
                if (manager.isProviderEnabled(provider)) {
                    manager.requestLocationUpdates(provider, minTime, minDistance, created, Looper.getMainLooper())
                }
            }
        }
        runCatching {
            val last = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
                .maxByOrNull { it.time }
            if (last != null) onLocation(last.toSnapshot())
        }
    }

    fun stop() {
        listener?.let { runCatching { manager.removeUpdates(it) } }
        listener = null
    }

    private fun Location.toSnapshot(): LocationSnapshot = LocationSnapshot(
        timestampEpochMs = if (time > 0) time else System.currentTimeMillis(),
        latitude = latitude,
        longitude = longitude,
        altitudeMeters = if (hasAltitude()) altitude else null,
        horizontalAccuracyMeters = if (hasAccuracy()) accuracy else null,
        verticalAccuracyMeters = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy()) verticalAccuracyMeters else null,
        provider = provider,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
        positionReference = PositionReference.GNSS_ABSOLUTE
    )
}

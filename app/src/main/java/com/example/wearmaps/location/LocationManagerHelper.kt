package com.example.wearmaps.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import org.json.JSONArray
import org.json.JSONObject

data class MapPin(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

data class GnssSatelliteInfo(
    val usedInFixCount: Int = 0,
    val totalInViewCount: Int = 0
)

class LocationManagerHelper(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val prefs = context.getSharedPreferences("wear_maps_prefs", Context.MODE_PRIVATE)

    var currentLocation: Location? = null
        private set

    var gnssInfo: GnssSatelliteInfo = GnssSatelliteInfo()
        private set

    private var gnssCallback: Any? = null

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(
        onLocationChanged: (Location) -> Unit,
        onGnssStatusChanged: ((GnssSatelliteInfo) -> Unit)? = null
    ) {
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                currentLocation = location
                onLocationChanged(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Register GNSS Satellite Status Listener
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val callback = object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    val total = status.satelliteCount
                    var used = 0
                    for (i in 0 until total) {
                        if (status.usedInFix(i)) {
                            used++
                        }
                    }
                    gnssInfo = GnssSatelliteInfo(usedInFixCount = used, totalInViewCount = total)
                    onGnssStatusChanged?.invoke(gnssInfo)
                }
            }
            gnssCallback = callback
            try {
                locationManager.registerGnssStatusCallback(callback, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        try {
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            currentLocation = lastGps ?: lastNet
            currentLocation?.let { onLocationChanged(it) }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 1f, listener)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000L, 2f, listener)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopGnssUpdates() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssCallback != null) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssCallback as GnssStatus.Callback)
            } catch (e: Exception) {}
        }
    }

    fun savePin(name: String, lat: Double, lon: Double): MapPin {
        val pins = getSavedPins().toMutableList()
        val newPin = MapPin(
            id = System.currentTimeMillis().toString(),
            name = name,
            latitude = lat,
            longitude = lon,
            timestamp = System.currentTimeMillis()
        )
        pins.add(newPin)
        savePinsToPrefs(pins)
        return newPin
    }

    fun getSavedPins(): List<MapPin> {
        val jsonStr = prefs.getString("saved_pins", "[]") ?: "[]"
        val list = mutableListOf<MapPin>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    MapPin(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        latitude = obj.getDouble("latitude"),
                        longitude = obj.getDouble("longitude"),
                        timestamp = obj.getLong("timestamp")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun deletePin(id: String) {
        val pins = getSavedPins().filter { it.id != id }
        savePinsToPrefs(pins)
    }

    fun clearAllPins() {
        prefs.edit().remove("saved_pins").apply()
    }

    private fun savePinsToPrefs(pins: List<MapPin>) {
        val array = JSONArray()
        pins.forEach { pin ->
            val obj = JSONObject().apply {
                put("id", pin.id)
                put("name", pin.name)
                put("latitude", pin.latitude)
                put("longitude", pin.longitude)
                put("timestamp", pin.timestamp)
            }
            array.put(obj)
        }
        prefs.edit().putString("saved_prefs", array.toString()).apply()
        prefs.edit().putString("saved_pins", array.toString()).apply()
    }
}

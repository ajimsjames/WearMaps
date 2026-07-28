package com.ajimsjames.wearmaps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.ajimsjames.wearmaps.location.LocationManagerHelper
import com.ajimsjames.wearmaps.map.MapTileFetcher
import com.ajimsjames.wearmaps.presentation.MapViewScreen
import com.ajimsjames.wearmaps.sensor.CompassHeadingListener

class MainActivity : ComponentActivity() {

    private lateinit var tileFetcher: MapTileFetcher
    private lateinit var locationHelper: LocationManagerHelper
    private lateinit var compassListener: CompassHeadingListener

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Continue loading map with available permissions
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tileFetcher = MapTileFetcher(this)
        locationHelper = LocationManagerHelper(this)
        compassListener = CompassHeadingListener(this)

        requestPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent {
            MapViewScreen(
                tileFetcher = tileFetcher,
                locationHelper = locationHelper,
                compassListener = compassListener
            )
        }
    }
}

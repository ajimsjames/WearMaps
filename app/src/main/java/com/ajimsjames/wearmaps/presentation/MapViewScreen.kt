package com.ajimsjames.wearmaps.presentation

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.material.Text
import com.ajimsjames.wearmaps.location.GnssSatelliteInfo
import com.ajimsjames.wearmaps.location.LocationManagerHelper
import com.ajimsjames.wearmaps.location.MapPin
import com.ajimsjames.wearmaps.map.MapTileFetcher
import com.ajimsjames.wearmaps.sensor.CompassHeadingListener
import kotlinx.coroutines.launch
import kotlin.math.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MapViewScreen(
    tileFetcher: MapTileFetcher,
    locationHelper: LocationManagerHelper,
    compassListener: CompassHeadingListener
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Default center
    var centerLat by remember { mutableStateOf(9.9312) }
    var centerLon by remember { mutableStateOf(76.2673) }
    var zoomLevel by remember { mutableStateOf(15) }

    // Auto-Center / Tracking Mode: Keeps map centered on user GPS location as they move!
    var isAutoCenterMode by remember { mutableStateOf(true) }

    var headingAngle by remember { mutableStateOf(0f) }
    var isHeadingUpMode by remember { mutableStateOf(false) }

    var userGpsLocation by remember { mutableStateOf(locationHelper.currentLocation) }
    var gnssSatelliteInfo by remember { mutableStateOf(locationHelper.gnssInfo) }
    var savedPins by remember { mutableStateOf(locationHelper.getSavedPins()) }

    var showPinDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    // Active Navigation Target
    var activeNavDestination by remember { mutableStateOf<MapPin?>(null) }

    // Focus requester for physical rotary bezel
    val focusRequester = remember { FocusRequester() }

    // Color Matrix filter for sleek OLED Dark Mode OSM maps
    val darkColorFilter = remember {
        val matrix = ColorMatrix(
            floatArrayOf(
                -0.8f, 0f, 0f, 0f, 240f,
                0f, -0.8f, 0f, 0f, 240f,
                0f, 0f, -0.8f, 0f, 240f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        ColorMatrixColorFilter(matrix)
    }

    val paint = remember {
        Paint().apply {
            colorFilter = darkColorFilter
            isAntiAlias = true
            isFilterBitmap = true
        }
    }

    // Hardware sensors lifecycle
    DisposableEffect(Unit) {
        compassListener.start { azimuth ->
            if (isHeadingUpMode) {
                headingAngle = azimuth
            } else {
                headingAngle = 0f
            }
        }

        locationHelper.startLocationUpdates(
            onLocationChanged = { loc ->
                userGpsLocation = loc
                if (isAutoCenterMode) {
                    centerLat = loc.latitude
                    centerLon = loc.longitude
                }
            },
            onGnssStatusChanged = { info ->
                gnssSatelliteInfo = info
            }
        )

        onDispose {
            compassListener.stop()
            locationHelper.stopGnssUpdates()
        }
    }

    // Tile cache state map
    val tileBitmapMap = remember { mutableStateMapOf<String, android.graphics.Bitmap?>() }

    // Load tiles for current view bounds
    LaunchedEffect(centerLat, centerLon, zoomLevel) {
        val centerTile = tileFetcher.latLonToTile(centerLat, centerLon, zoomLevel)
        val radius = 2
        for (dx in -radius..radius) {
            for (dy in -radius..radius) {
                val tileKey = "${zoomLevel}_${centerTile.x + dx}_${centerTile.y + dy}"
                if (!tileBitmapMap.containsKey(tileKey)) {
                    scope.launch {
                        val bitmap = tileFetcher.getTileBitmap(com.ajimsjames.wearmaps.map.TileCoord(zoomLevel, centerTile.x + dx, centerTile.y + dy))
                        if (bitmap != null) {
                            tileBitmapMap[tileKey] = bitmap
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .onRotaryScrollEvent { event ->
                if (event.verticalScrollPixels > 0) {
                    if (zoomLevel < 18) zoomLevel += 1
                } else if (event.verticalScrollPixels < 0) {
                    if (zoomLevel > 3) zoomLevel -= 1
                }
                true
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    isAutoCenterMode = false

                    val metersPerPixel = 156543.03392 * cos(Math.toRadians(centerLat)) / 2.0.pow(zoomLevel.toDouble())
                    val latChange = (dragAmount.y * metersPerPixel) / 111111.0
                    val lonChange = -(dragAmount.x * metersPerPixel) / (111111.0 * cos(Math.toRadians(centerLat)))

                    centerLat = (centerLat + latChange).coerceIn(-85.0, 85.0)
                    centerLon = (centerLon + lonChange).coerceIn(-180.0, 180.0)
                }
            }
    ) {
        // Main 2D Skia Map Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()

            if (isHeadingUpMode && headingAngle != 0f) {
                nativeCanvas.rotate(-headingAngle, canvasWidth / 2f, canvasHeight / 2f)
            }

            val centerTile = tileFetcher.latLonToTile(centerLat, centerLon, zoomLevel)
            val tileSize = 256f

            val n = 2.0.pow(zoomLevel.toDouble())
            val exactX = (centerLon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(centerLat)
            val exactY = (1.0 - asinh(tan(latRad)) / PI) / 2.0 * n

            val centerPixelX = exactX * tileSize
            val centerPixelY = exactY * tileSize

            val screenCenterX = canvasWidth / 2f
            val screenCenterY = canvasHeight / 2f

            val radius = 2
            for (dx in -radius..radius) {
                for (dy in -radius..radius) {
                    val tileX = centerTile.x + dx
                    val tileY = centerTile.y + dy
                    val key = "${zoomLevel}_${tileX}_${tileY}"
                    val bitmap = tileBitmapMap[key]

                    val tileLeftPixel = tileX * tileSize
                    val tileTopPixel = tileY * tileSize

                    val drawX = screenCenterX + (tileLeftPixel - centerPixelX).toFloat()
                    val drawY = screenCenterY + (tileTopPixel - centerPixelY).toFloat()

                    if (bitmap != null) {
                        nativeCanvas.drawBitmap(bitmap, drawX, drawY, paint)
                    }
                }
            }

            // Draw User GPS Location Marker
            userGpsLocation?.let { gpsLoc ->
                val gpsExactX = (gpsLoc.longitude + 180.0) / 360.0 * n
                val gpsLatRad = Math.toRadians(gpsLoc.latitude)
                val gpsExactY = (1.0 - asinh(tan(gpsLatRad)) / PI) / 2.0 * n

                val gpsPixelX = gpsExactX * tileSize
                val gpsPixelY = gpsExactY * tileSize

                val userScreenX = screenCenterX + (gpsPixelX - centerPixelX).toFloat()
                val userScreenY = screenCenterY + (gpsPixelY - centerPixelY).toFloat()

                val pulsePaint = Paint().apply {
                    color = android.graphics.Color.argb(80, 0, 230, 118)
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                nativeCanvas.drawCircle(userScreenX, userScreenY, 22f, pulsePaint)

                val dotPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#00E676")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                nativeCanvas.drawCircle(userScreenX, userScreenY, 9f, dotPaint)

                val corePaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                nativeCanvas.drawCircle(userScreenX, userScreenY, 3.5f, corePaint)
            }

            // Draw Saved Pins
            savedPins.forEach { pin ->
                val pinExactX = (pin.longitude + 180.0) / 360.0 * n
                val pinLatRad = Math.toRadians(pin.latitude)
                val pinExactY = (1.0 - asinh(tan(pinLatRad)) / PI) / 2.0 * n

                val pinPixelX = pinExactX * tileSize
                val pinPixelY = pinExactY * tileSize

                val pinScreenX = screenCenterX + (pinPixelX - centerPixelX).toFloat()
                val pinScreenY = screenCenterY + (pinPixelY - centerPixelY).toFloat()

                val pinPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#FF9800")
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                nativeCanvas.drawCircle(pinScreenX, pinScreenY, 9f, pinPaint)

                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 22f
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                nativeCanvas.drawText(pin.name, pinScreenX, pinScreenY - 14f, textPaint)
            }

            // Draw Route Navigation Line if active
            activeNavDestination?.let { navDest ->
                userGpsLocation?.let { gpsLoc ->
                    val userExactX = (gpsLoc.longitude + 180.0) / 360.0 * n
                    val userLatRad = Math.toRadians(gpsLoc.latitude)
                    val userExactY = (1.0 - asinh(tan(userLatRad)) / PI) / 2.0 * n
                    val userPixelX = userExactX * tileSize
                    val userPixelY = userExactY * tileSize
                    val userScreenX = screenCenterX + (userPixelX - centerPixelX).toFloat()
                    val userScreenY = screenCenterY + (userPixelY - centerPixelY).toFloat()

                    val destExactX = (navDest.longitude + 180.0) / 360.0 * n
                    val destLatRad = Math.toRadians(navDest.latitude)
                    val destExactY = (1.0 - asinh(tan(destLatRad)) / PI) / 2.0 * n
                    val destPixelX = destExactX * tileSize
                    val destPixelY = destExactY * tileSize
                    val destScreenX = screenCenterX + (destPixelX - centerPixelX).toFloat()
                    val destScreenY = screenCenterY + (destPixelY - centerPixelY).toFloat()

                    val linePaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#00E676")
                        strokeWidth = 6f
                        style = Paint.Style.STROKE
                        pathEffect = android.graphics.DashPathEffect(floatArrayOf(12f, 12f), 0f)
                        isAntiAlias = true
                    }
                    nativeCanvas.drawLine(userScreenX, userScreenY, destScreenX, destScreenY, linePaint)
                }
            }

            nativeCanvas.restore()
        }

        // Circular Smartwatch Overlay Controls & Bezel Alignment
        Box(modifier = Modifier.fillMaxSize()) {

            // Top Status Bar: GNSS Satellite Count, Zoom Level & Heading Mode
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val satColor = when {
                    gnssSatelliteInfo.usedInFixCount >= 6 -> Color(0xFF00E676)
                    gnssSatelliteInfo.usedInFixCount >= 3 -> Color(0xFFFFD600)
                    else -> Color(0xFFFF5252)
                }

                Text(
                    text = "📡 ${gnssSatelliteInfo.usedInFixCount}/${gnssSatelliteInfo.totalInViewCount}",
                    color = satColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " | Z:$zoomLevel | ${if (isHeadingUpMode) "🗺️ Track" else "🧭 North"}",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right Edge Controls: Zoom + / -
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD1C1C1E))
                        .clickable { if (zoomLevel < 18) zoomLevel += 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xDD1C1C1E))
                        .clickable { if (zoomLevel > 3) zoomLevel -= 1 },
                    contentAlignment = Alignment.Center
                ) {
                    Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Left Edge Controls: Auto-Center GPS & Toggle Heading Mode
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isAutoCenterMode) Color(0xDD00E676) else Color(0xDD1565C0))
                        .clickable {
                            userGpsLocation?.let { loc ->
                                isAutoCenterMode = true
                                centerLat = loc.latitude
                                centerLon = loc.longitude
                                Toast.makeText(context, "🎯 Auto-Center ON", Toast.LENGTH_SHORT).show()
                            } ?: run {
                                Toast.makeText(context, "Acquiring GPS...", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🎯", fontSize = 12.sp)
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (isHeadingUpMode) Color(0xDD00E676) else Color(0xDD333336))
                        .clickable { isHeadingUpMode = !isHeadingUpMode },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (isHeadingUpMode) "🗺️" else "🧭", fontSize = 12.sp)
                }
            }

            // Navigation HUD Overlay
            activeNavDestination?.let { navDest ->
                userGpsLocation?.let { gpsLoc ->
                    val results = FloatArray(2)
                    android.location.Location.distanceBetween(
                        gpsLoc.latitude, gpsLoc.longitude,
                        navDest.latitude, navDest.longitude,
                        results
                    )
                    val distanceMeters = results[0]
                    val targetBearing = results[1]
                    val currentHeading = headingAngle
                    val relativeSteer = (targetBearing - currentHeading + 360) % 360

                    val formattedDist = if (distanceMeters >= 1000) {
                        "%.1f km".format(distanceMeters / 1000f)
                    } else {
                        "${distanceMeters.toInt()}m"
                    }

                    // Steer direction arrow and text
                    val steerArrow = when {
                        relativeSteer < 22.5 || relativeSteer > 337.5 -> "⬆️ Straight"
                        relativeSteer in 22.5..67.5 -> "↗️ Steer Right"
                        relativeSteer in 67.5..157.5 -> "➡️ Turn Right"
                        relativeSteer in 157.5..202.5 -> "⬇️ Turn Around"
                        relativeSteer in 202.5..292.5 -> "⬅️ Turn Left"
                        else -> "↖️ Steer Left"
                    }

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp) // Sits above bottom bezel buttons
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFA1A237E)) // Dark blue premium alert style
                            .border(1.dp, Color(0xFF00E676), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "🎯 Nav: ${navDest.name}",
                                color = Color.White,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$steerArrow • $formattedDist",
                                color = Color(0xFF00E676),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD32F2F))
                                .clickable { activeNavDestination = null },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Bottom Control Bar: Offline Download & Save Pin
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp) // raised bottom margin to align with bezel
                    .fillMaxWidth(0.72f), // narrowed width to fit round screen
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDDFF9800))
                        .clickable {
                            val newPin = locationHelper.savePin("Pin ${savedPins.size + 1}", centerLat, centerLon)
                            savedPins = locationHelper.getSavedPins()
                            Toast.makeText(context, "Saved ${newPin.name}!", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("+ Pin", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xDD29B6F6))
                        .clickable {
                            scope.launch {
                                Toast.makeText(context, "Caching Offline Area...", Toast.LENGTH_SHORT).show()
                                tileFetcher.predownloadOfflineRegion(centerLat, centerLon, zoomLevel)
                                Toast.makeText(context, "Offline Map Ready!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("⬇️ Offline", color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Top Bezel Curved Navigation Bar
            CurvedLayout(
                anchor = 270f,
                modifier = Modifier.fillMaxSize()
            ) {
                curvedComposable {
                    BezelPill("🗺️ Map", selected = true) { }
                }
                curvedComposable {
                    Spacer(modifier = Modifier.width(3.dp))
                }
                curvedComposable {
                    BezelPill("📌 Pins (${savedPins.size})", selected = false) { showPinDialog = true }
                }
                curvedComposable {
                    Spacer(modifier = Modifier.width(3.dp))
                }
                curvedComposable {
                    BezelPill("⚙️ About", selected = false) { showAboutDialog = true }
                }
            }
        }

        // Saved Pins Dialog
        if (showPinDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0000000))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📌 Saved Pins (${savedPins.size})", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF333336))
                                .clickable { showPinDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (savedPins.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No saved pins yet", color = Color.Gray, fontSize = 11.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(savedPins) { pin ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF2C2C2E))
                                        .clickable {
                                            isAutoCenterMode = false
                                            centerLat = pin.latitude
                                            centerLon = pin.longitude
                                            showPinDialog = false
                                            Toast.makeText(context, "Jumped to ${pin.name}", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(pin.name, color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("%.4f, %.4f".format(pin.latitude, pin.longitude), color = Color.LightGray, fontSize = 9.sp)
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // Navigate Button
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF00E676))
                                                .clickable {
                                                    activeNavDestination = pin
                                                    isAutoCenterMode = true
                                                    showPinDialog = false
                                                    Toast.makeText(context, "Navigating to ${pin.name}!", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🧭", fontSize = 10.sp)
                                        }

                                        // Delete Button
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFD32F2F))
                                                .clickable {
                                                    locationHelper.deletePin(pin.id)
                                                    savedPins = locationHelper.getSavedPins()
                                                    if (activeNavDestination?.id == pin.id) {
                                                        activeNavDestination = null
                                                    }
                                                    Toast.makeText(context, "Pin Deleted", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("🗑️", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD32F2F))
                                .clickable {
                                    locationHelper.clearAllPins()
                                    savedPins = locationHelper.getSavedPins()
                                    Toast.makeText(context, "All Pins Deleted", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🗑️ Delete All Pins", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // About App Dialog
        if (showAboutDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0000000))
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF1C1C1E))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚙️ About WearMaps", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF333336))
                                .clickable { showAboutDialog = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        item {
                            Text("🗺️ WearMaps v1.2.0", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("By Aju George", color = Color.Gray, fontSize = 10.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF2C2C2E))
                                    .padding(8.dp)
                            ) {
                                Text("• Skia 2D Raster & Vector Engine", color = Color.LightGray, fontSize = 9.sp)
                                Text("• Dynamic GPS Auto-Center Tracking", color = Color.LightGray, fontSize = 9.sp)
                                Text("• GNSS Satellite Counter Overlay", color = Color.LightGray, fontSize = 9.sp)
                                Text("• Region Offline Pre-downloader", color = Color.LightGray, fontSize = 9.sp)
                                Text("• Target: Samsung Galaxy Watch 6", color = Color(0xFF0288D1), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun BezelPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF00E676) else Color(0xFF2C2C2E))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = if (selected) Color.Black else Color.Gray,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

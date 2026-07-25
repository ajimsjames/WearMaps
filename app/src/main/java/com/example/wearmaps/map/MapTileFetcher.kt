package com.example.wearmaps.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.*

data class TileCoord(val zoom: Int, val x: Int, val y: Int)

class MapTileFetcher(private val context: Context) {

    private val tileCacheDir = File(context.cacheDir, "osm_tiles").apply { mkdirs() }

    fun latLonToTile(lat: Double, lon: Double, zoom: Int): TileCoord {
        val n = 2.0.pow(zoom.toDouble())
        val latRad = Math.toRadians(lat)
        val x = floor((lon + 180.0) / 360.0 * n).toInt()
        var y = floor((1.0 - asinh(tan(latRad)) / PI) / 2.0 * n).toInt()
        val maxTile = n.toInt() - 1
        return TileCoord(zoom, x.coerceIn(0, maxTile), y.coerceIn(0, maxTile))
    }

    fun tileToLatLon(x: Int, y: Int, zoom: Int): Pair<Double, Double> {
        val n = 2.0.pow(zoom.toDouble())
        val lon = x / n * 360.0 - 180.0
        val latRad = atan(sinh(PI * (1.0 - 2.0 * y / n)))
        val lat = Math.toDegrees(latRad)
        return Pair(lat, lon)
    }

    suspend fun getTileBitmap(tile: TileCoord): Bitmap? = withContext(Dispatchers.IO) {
        val tileFile = File(tileCacheDir, "${tile.zoom}_${tile.x}_${tile.y}.png")
        if (tileFile.exists() && tileFile.length() > 0) {
            return@withContext BitmapFactory.decodeFile(tileFile.absolutePath)
        }

        // Download from OpenStreetMap tile server
        val urlString = "https://tile.openstreetmap.org/${tile.zoom}/${tile.x}/${tile.y}.png"
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "WearMaps/1.0 (Wear OS Smartwatch Map Navigator)")
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.connect()

            if (connection.responseCode == 200) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    FileOutputStream(tileFile).use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                    }
                }
                return@withContext bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun predownloadOfflineRegion(centerLat: Double, centerLon: Double, zoom: Int, radiusTiles: Int = 2) {
        withContext(Dispatchers.IO) {
            val centerTile = latLonToTile(centerLat, centerLon, zoom)
            for (dx in -radiusTiles..radiusTiles) {
                for (dy in -radiusTiles..radiusTiles) {
                    val tile = TileCoord(zoom, centerTile.x + dx, centerTile.y + dy)
                    getTileBitmap(tile)
                }
            }
        }
    }
}

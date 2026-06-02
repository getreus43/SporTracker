package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import com.example.data.model.RoutePoint
import com.example.data.model.Waypoint
import kotlin.math.*

private fun tileToLon(x: Int, z: Int): Double = x.toDouble() / (1 shl z).toDouble() * 360.0 - 180.0

private fun tileToLat(y: Int, z: Int): Double {
    val n = PI - 2.0 * PI * y.toDouble() / (1 shl z).toDouble()
    return 180.0 / PI * atan(0.5 * (exp(n) - exp(-n)))
}

private fun lonToTileX(lon: Double, z: Int): Int {
    val totalTiles = 1 shl z
    val x = floor((lon.coerceIn(-180.0, 180.0) + 180.0) / 360.0 * totalTiles).toInt()
    return x.coerceIn(0, totalTiles - 1)
}

private fun latToTileY(lat: Double, z: Int): Int {
    val clippedLat = lat.coerceIn(-85.05112878, 85.05112878)
    val latRad = Math.toRadians(clippedLat)
    val yVal = 1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / PI)
    val totalTiles = 1 shl z
    val y = floor(yVal / 2.0 * totalTiles).toInt()
    return y.coerceIn(0, totalTiles - 1)
}

private data class ProjectedBounds(
    val minX: Double,
    val maxX: Double,
    val minY: Double,
    val maxY: Double
)

private fun projectedYToLat(yMerc: Double): Double {
    val n = PI - 2.0 * PI * yMerc
    return 180.0 / PI * atan(sinh(n))
}

private fun getTileUrl(x: Int, y: Int, z: Int, type: String, isAmoled: Boolean): String {
    val subdomains = listOf("a", "b", "c")
    val sub = subdomains[abs(x + y) % subdomains.size]
    return when (type) {
        "Satelital" -> {
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/$z/$y/$x"
        }
        "Topográfico" -> {
            "https://server.arcgisonline.com/ArcGIS/rest/services/World_Topo_Map/MapServer/tile/$z/$y/$x"
        }
        else -> { // Carreteras Base
            if (isAmoled) {
                "https://$sub.basemaps.cartocdn.com/dark_all/$z/$x/$y.png"
            } else {
                "https://$sub.basemaps.cartocdn.com/light_all/$z/$x/$y.png"
            }
        }
    }
}

@Composable
fun RouteMapCanvas(
    points: List<RoutePoint>,
    waypoints: List<Waypoint> = emptyList(),
    currentPointIndex: Int? = null,
    routeColor: Color = Color(0xFF007AFF),
    isAmoled: Boolean = false,
    mapType: String = "Carreteras Base",
    showTransportOverlay: Boolean = false,
    centerOn: Pair<Double, Double>? = null,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    isOffRoute: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Zoom and Pan States
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset offset/scale when points or searched location changes
    LaunchedEffect(points, centerOn) {
        scale = 1f
        offset = Offset.Zero
    }

    // Prepare central bounding box points
    val mapPoints = remember(points, centerOn) {
        if (centerOn != null) {
            listOf(
                RoutePoint(latitude = centerOn.first - 0.005, longitude = centerOn.second - 0.005, altitude = 0.0, timestamp = 0L),
                RoutePoint(latitude = centerOn.first + 0.005, longitude = centerOn.second + 0.005, altitude = 0.0, timestamp = 0L)
            )
        } else if (points.isNotEmpty()) {
            points
        } else {
            listOf(
                RoutePoint(latitude = 40.416775, longitude = -3.703790, altitude = 650.0, timestamp = 0L),
                RoutePoint(latitude = 40.426775, longitude = -3.693790, altitude = 650.0, timestamp = 1000L)
            )
        }
    }

    val minLat = remember(mapPoints) { mapPoints.minOfOrNull { it.latitude } ?: 40.416775 }
    val maxLat = remember(mapPoints) { mapPoints.maxOfOrNull { it.latitude } ?: 40.426775 }
    val minLon = remember(mapPoints) { mapPoints.minOfOrNull { it.longitude } ?: -3.703790 }
    val maxLon = remember(mapPoints) { mapPoints.maxOfOrNull { it.longitude } ?: -3.693790 }

    val latSpan = remember(minLat, maxLat) { if (maxLat - minLat == 0.0) 0.001 else maxLat - minLat }
    val lonSpan = remember(minLon, maxLon) { if (maxLon - minLon == 0.0) 0.001 else maxLon - minLon }

    val resolvedType = remember(mapType) {
        when {
            mapType.lowercase().contains("topo") -> "Topográfico"
            mapType.lowercase().contains("sate") || mapType.lowercase().contains("satélite") -> "Satelital"
            else -> "Carreteras Base"
        }
    }

    // Determine target zoom level for tiles
    val zoomLevel = remember(latSpan, lonSpan) {
        val maxSpan = max(latSpan, lonSpan)
        when {
            maxSpan > 10.0 -> 5
            maxSpan > 5.0 -> 7
            maxSpan > 2.0 -> 9
            maxSpan > 1.0 -> 10
            maxSpan > 0.5 -> 11
            maxSpan > 0.2 -> 12
            maxSpan > 0.1 -> 13
            maxSpan > 0.05 -> 14
            maxSpan > 0.02 -> 15
            maxSpan > 0.01 -> 16
            maxSpan > 0.005 -> 17
            else -> 18
        }
    }

    val density = LocalDensity.current
    val context = LocalContext.current

    val bounds = remember(mapPoints) {
        val minX = mapPoints.minOf { (it.longitude + 180.0) / 360.0 }
        val maxX = mapPoints.maxOf { (it.longitude + 180.0) / 360.0 }
        
        val minY = mapPoints.minOf { 
            val latRad = Math.toRadians(it.latitude.coerceIn(-85.05112878, 85.05112878))
            (1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / PI)) / 2.0
        }
        val maxY = mapPoints.maxOf { 
            val latRad = Math.toRadians(it.latitude.coerceIn(-85.05112878, 85.05112878))
            (1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / PI)) / 2.0
        }
        ProjectedBounds(minX, maxX, minY, maxY)
    }

    val xSpan = remember(bounds) {
        val span = bounds.maxX - bounds.minX
        if (span == 0.0) 0.0001 else span
    }
    
    val ySpan = remember(bounds) {
        val span = bounds.maxY - bounds.minY
        if (span == 0.0) 0.0001 else span
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.15f, 60.0f)
                    offset += pan
                }
            }
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        val baseScale = remember(bounds, xSpan, ySpan, widthPx, heightPx) {
            val padding = 150f
            val mapWidth = widthPx - (padding * 2)
            val mapHeight = heightPx - (padding * 2)
            if (mapWidth <= 0 || mapHeight <= 0) 1f else min(mapWidth / xSpan, mapHeight / ySpan).toFloat()
        }

        // Dynamic zoom level calculation based on pinch zoom gestures
        val currentZ = remember(zoomLevel, scale) {
            val extraZoom = Math.round(ln(scale) / ln(2.0)).toInt()
            (zoomLevel + extraZoom).coerceIn(1, 19)
        }

        // Project dynamic screen coordinates to LatLng
        fun screenToGeo(sx: Float, sy: Float): Pair<Double, Double> {
            val centerX = widthPx / 2f
            val centerY = heightPx / 2f
            val px = (sx - centerX - offset.x) / scale + centerX
            val py = (sy - centerY - offset.y) / scale + centerY
            
            val padding = 150f
            val mapWidth = widthPx - (padding * 2)
            val mapHeight = heightPx - (padding * 2)
            
            val x = ((px - padding - (mapWidth - xSpan * baseScale) / 2) / baseScale) + bounds.minX
            val y = ((py - padding - (mapHeight - ySpan * baseScale) / 2) / baseScale) + bounds.minY
            
            val longitude = x * 360.0 - 180.0
            val latitude = projectedYToLat(y)
            return Pair(latitude, longitude)
        }

        // Calculate visible geo bounding box of the screen dynamically!
        val visibleGeoBounds = remember(bounds, xSpan, ySpan, baseScale, scale, offset, widthPx, heightPx) {
            if (widthPx <= 0f || heightPx <= 0f) {
                Triple(minLat, maxLat, Pair(minLon, maxLon))
            } else {
                val (topLeftLat, topLeftLon) = screenToGeo(0f, 0f)
                val (bottomRightLat, bottomRightLon) = screenToGeo(widthPx, heightPx)
                
                val screenMinLat = min(topLeftLat, bottomRightLat).coerceIn(-85.0511, 85.0511)
                val screenMaxLat = max(topLeftLat, bottomRightLat).coerceIn(-85.0511, 85.0511)
                val screenMinLon = min(topLeftLon, bottomRightLon).coerceIn(-180.0, 180.0)
                val screenMaxLon = max(topLeftLon, bottomRightLon).coerceIn(-180.0, 180.0)
                
                Triple(screenMinLat, screenMaxLat, Pair(screenMinLon, screenMaxLon))
            }
        }

        // Calculate only the visible tile indices dynamically!
        val tileIndices = remember(visibleGeoBounds, currentZ) {
            val (screenMinLat, screenMaxLat, lonPair) = visibleGeoBounds
            val (screenMinLon, screenMaxLon) = lonPair
            
            var minX = lonToTileX(screenMinLon, currentZ)
            var maxX = lonToTileX(screenMaxLon, currentZ)
            var minY = latToTileY(screenMaxLat, currentZ)
            var maxY = latToTileY(screenMinLat, currentZ)
            
            if (minX > maxX) { val temp = minX; minX = maxX; maxX = temp }
            if (minY > maxY) { val temp = minY; minY = maxY; maxY = temp }
            
            // Constrain number of tiles to avoid overloading memory and rendering
            var safeZ = currentZ
            while ((maxX - minX + 1) * (maxY - minY + 1) > 25 && safeZ > 2) {
                safeZ--
                minX = lonToTileX(screenMinLon, safeZ)
                maxX = lonToTileX(screenMaxLon, safeZ)
                minY = latToTileY(screenMaxLat, safeZ)
                maxY = latToTileY(screenMinLat, safeZ)
                if (minX > maxX) { val temp = minX; minX = maxX; maxX = temp }
                if (minY > maxY) { val temp = minY; minY = maxY; maxY = temp }
            }
            
            val list = mutableListOf<Triple<Int, Int, Int>>() // X, Y, Z
            for (x in minX..maxX) {
                for (y in minY..maxY) {
                    list.add(Triple(x, y, safeZ))
                }
            }
            list
        }

        // Load visible tiles dynamically through Coil inside a proper Composable context
        val tilePainters = ArrayList<Triple<Triple<Int, Int, Int>, String, coil.compose.AsyncImagePainter>>(tileIndices.size)
        for (triple in tileIndices) {
            val (x, y, z) = triple
            val url = getTileUrl(x, y, z, resolvedType, isAmoled)
            androidx.compose.runtime.key(x, y, z, resolvedType, isAmoled) {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .setHeader("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    .crossfade(true)
                    .build()
                val painter = coil.compose.rememberAsyncImagePainter(model = request)
                
                // Read painter's state to force recomposition, ensuring the map updates dynamically as images arrive
                val painterState = painter.state
                
                tilePainters.add(Triple(triple, url, painter))
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            if (width == 0f || height == 0f) return@Canvas

            // 1. Draw Map background base color
            val bgColor = when (resolvedType) {
                "Topográfico" -> if (isAmoled) Color(0xFF0D1C11) else Color(0xFFEAF0E8)
                "Satelital" -> Color(0xFF06101D)
                else -> if (isAmoled) Color(0xFF101010) else Color(0xFFF4F3F0)
            }
            drawRect(color = bgColor, topLeft = Offset.Zero, size = size)

            // Coordinate to pixel projection function
            fun getCanvasPos(latitude: Double, longitude: Double): Offset {
                val x = (longitude + 180.0) / 360.0
                val latRad = Math.toRadians(latitude.coerceIn(-85.05112878, 85.05112878))
                val y = (1.0 - (ln(tan(latRad) + 1.0 / cos(latRad)) / PI)) / 2.0

                val padding = 150f
                val mapWidth = width - (padding * 2)
                val mapHeight = height - (padding * 2)

                val mappedX = padding + (x - bounds.minX) * baseScale + (mapWidth - xSpan * baseScale) / 2
                val mappedY = padding + (y - bounds.minY) * baseScale + (mapHeight - ySpan * baseScale) / 2

                val centerX = width / 2f
                val centerY = height / 2f

                val scaledX = (mappedX - centerX) * scale + centerX + offset.x
                val scaledY = (mappedY - centerY) * scale + centerY + offset.y
                return Offset(scaledX.toFloat(), scaledY.toFloat())
            }

            // 1.1 Draw Real downloaded Map Imagery Tiles!
            tilePainters.forEach { (coords, url, painter) ->
                val (tx, ty, tz) = coords
                val lonLeft = tileToLon(tx, tz)
                val latTop = tileToLat(ty, tz)
                val lonRight = tileToLon(tx + 1, tz)
                val latBottom = tileToLat(ty + 1, tz)

                val pTopLeft = getCanvasPos(latTop, lonLeft)
                val pBottomRight = getCanvasPos(latBottom, lonRight)

                val tileW = pBottomRight.x - pTopLeft.x
                val tileH = pBottomRight.y - pTopLeft.y

                translate(pTopLeft.x, pTopLeft.y) {
                    with(painter) {
                        draw(size = androidx.compose.ui.geometry.Size(tileW, tileH))
                    }
                }
            }

            // 1.2 Add subtle vector enhancements depending on selection
            if (resolvedType == "Topográfico") {
                val contourColor = (if (isAmoled) Color(0xFFC0DEC0) else Color(0xFF3F6443)).copy(alpha = 0.15f)
                val topoCenter = Offset(width / 2f + offset.x, height / 2f + offset.y)
                for (r in 1..10) {
                    drawCircle(
                        color = contourColor,
                        radius = (r * 120f * scale),
                        center = topoCenter,
                        style = Stroke(width = 1f * scale, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f * scale, 12f * scale), 0f))
                    )
                }
            }

            // 1.3 Draw Public and Private Transport Overlay (extra optional Google style layer)
            if (showTransportOverlay) {
                val ptOffset = offset
                // Draw Green Metro Transit Line
                val lineGreenStart = Offset(-400f * scale + ptOffset.x, -100f * scale + ptOffset.y)
                val lineGreenEnd = Offset(width + 400f * scale + ptOffset.x, height + 100f * scale + ptOffset.y)
                drawLine(
                    color = Color(0xFF00B050), // Metro Vibrant Green
                    start = lineGreenStart,
                    end = lineGreenEnd,
                    strokeWidth = 4f * scale
                )

                // Draw Purple Subway Transit Line
                val linePurpleStart = Offset(-200f * scale + ptOffset.x, height + 200f * scale + ptOffset.y)
                val linePurpleEnd = Offset(width + 200f * scale + ptOffset.x, -200f * scale + ptOffset.y)
                drawLine(
                    color = Color(0xFF7030A0), // Subway Purple
                    start = linePurpleStart,
                    end = linePurpleEnd,
                    strokeWidth = 4f * scale
                )

                // Draw Transit Station Circles
                val stationOffsets = listOf(
                    lineGreenStart + (lineGreenEnd - lineGreenStart) * 0.25f,
                    lineGreenStart + (lineGreenEnd - lineGreenStart) * 0.55f,
                    lineGreenStart + (lineGreenEnd - lineGreenStart) * 0.85f,
                    linePurpleStart + (linePurpleEnd - linePurpleStart) * 0.35f,
                    linePurpleStart + (linePurpleEnd - linePurpleStart) * 0.75f
                )
                
                stationOffsets.forEach { stat ->
                    drawCircle(color = Color.White, radius = 6f * scale, center = stat)
                    drawCircle(color = Color.DarkGray, radius = 4f * scale, center = stat, style = Stroke(width = 1.5f * scale))
                }

                // Traffic Flow overlays (Private transport status red/yellow/green)
                if (resolvedType == "Carreteras Base") {
                    val dy = height / 2f + ptOffset.y
                    // Paint traffic speeds on the horizontal highway
                    drawLine(
                        color = Color(0xFF4CD964), // Fluid Green
                        start = Offset(0f, dy),
                        end = Offset(width * 0.35f, dy),
                        strokeWidth = 4f * scale
                    )
                    drawLine(
                        color = Color(0xFFFFCC00), // Moderate Orange-Yellow
                        start = Offset(width * 0.35f, dy),
                        end = Offset(width * 0.65f, dy),
                        strokeWidth = 4f * scale
                    )
                    drawLine(
                        color = Color(0xFFFF3B30), // Heavy Red Congestion
                        start = Offset(width * 0.65f, dy),
                        end = Offset(width, dy),
                        strokeWidth = 4f * scale
                    )
                }
            }

            // 2. Draw GPS line path if points exist
            if (points.isNotEmpty()) {
                // Draw Track Lines
                val path = Path()
                val startPos = getCanvasPos(points[0].latitude, points[0].longitude)
                path.moveTo(startPos.x, startPos.y)

                val limit = currentPointIndex ?: (points.size - 1)
                
                for (i in 1..limit.coerceIn(0, points.size - 1)) {
                    val pos = getCanvasPos(points[i].latitude, points[i].longitude)
                    path.lineTo(pos.x, pos.y)
                }

                // Draw Path glowing underlay
                drawPath(
                    path = path,
                    color = routeColor.copy(alpha = 0.25f),
                    style = Stroke(width = 16f, pathEffect = PathEffect.cornerPathEffect(15f))
                )

                // Draw Path line
                drawPath(
                    path = path,
                    color = routeColor,
                    style = Stroke(width = 6f, pathEffect = PathEffect.cornerPathEffect(15f))
                )

                // Draw remaining track in gray if in playback mode
                if (currentPointIndex != null && currentPointIndex < points.size - 1) {
                    val unreachedPath = Path()
                    val resumePos = getCanvasPos(points[currentPointIndex].latitude, points[currentPointIndex].longitude)
                    unreachedPath.moveTo(resumePos.x, resumePos.y)
                    for (i in (currentPointIndex + 1) until points.size) {
                        val pos = getCanvasPos(points[i].latitude, points[i].longitude)
                        unreachedPath.lineTo(pos.x, pos.y)
                    }
                    drawPath(
                        path = unreachedPath,
                        color = Color.LightGray.copy(alpha = 0.5f),
                        style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                    )
                }

                // 3. Draw Waypoints / Pins
                for (wp in waypoints) {
                    val pos = getCanvasPos(wp.latitude, wp.longitude)
                    // Outer point marker
                    drawCircle(
                        color = Color.White,
                        radius = 12f * scale,
                        center = pos
                    )
                    drawCircle(
                        color = Color(0xFFFF9500), // iOS Coral for Waypoints
                        radius = 8f * scale,
                        center = pos
                    )
                }

                // Also draw start and end markers
                val startPointPos = getCanvasPos(points.first().latitude, points.first().longitude)
                drawCircle(
                    color = Color(0xFF4CD964), // Green for start
                    radius = 9f * scale,
                    center = startPointPos
                )
                
                val endPointPos = getCanvasPos(points.last().latitude, points.last().longitude)
                drawCircle(
                    color = Color(0xFFFF3B30), // Red for end
                    radius = 9f * scale,
                    center = endPointPos
                )

                // 4. Draw User Pointer (pulsing overlay)
                val userPoint = if (userLatitude != null && userLongitude != null) {
                    RoutePoint(userLatitude, userLongitude, 0.0, 0L)
                } else if (currentPointIndex != null && currentPointIndex < points.size) {
                    points[currentPointIndex]
                } else {
                    points.last()
                }

                val userPos = getCanvasPos(userPoint.latitude, userPoint.longitude)

                // Pulsing dot
                drawCircle(
                    color = if (isOffRoute) Color(0xFFFF3B30).copy(alpha = 0.4f) else routeColor.copy(alpha = 0.4f),
                    radius = 20f * scale,
                    center = userPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 9f * scale,
                    center = userPos
                )
                drawCircle(
                    color = if (isOffRoute) Color(0xFFFF3B30) else routeColor,
                    radius = 6f * scale,
                    center = userPos
                )

                // Draw redirect line if off route
                if (isOffRoute) {
                    val closestPt = points.minByOrNull { pt: RoutePoint ->
                        com.example.utils.GpxParser.calculateDistanceKm(userPoint, pt)
                    }
                    if (closestPt != null) {
                        val routePos = getCanvasPos(closestPt.latitude, closestPt.longitude)
                        drawLine(
                            color = Color(0xFFFF9500), // Vibrant Orange redirection line
                            start = userPos,
                            end = routePos,
                            strokeWidth = 5f * scale,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f * scale, 15f * scale), 0f)
                        )
                    }
                }
            }

            // 5. Draw searched location marker pin if provided
            if (centerOn != null) {
                val pinPos = getCanvasPos(centerOn.first, centerOn.second)
                // Draw drop shadow
                drawCircle(
                    color = Color.Black.copy(alpha = 0.35f),
                    radius = 8f * scale,
                    center = pinPos + Offset(2f * scale, 3f * scale)
                )
                // Draw outer beautiful crimson ring
                drawCircle(
                    color = Color(0xFFC30010),
                    radius = 12f * scale,
                    center = pinPos
                )
                // Draw inner bright white center dot
                drawCircle(
                    color = Color.White,
                    radius = 5f * scale,
                    center = pinPos
                )
                // Draw a matching glowing aura
                drawCircle(
                    color = Color(0xFFC30010).copy(alpha = 0.2f),
                    radius = 28f * scale,
                    center = pinPos
                )
            }
        }
    }
}


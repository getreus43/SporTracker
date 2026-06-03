package com.example.utils

import com.example.data.model.Route
import com.example.data.model.RoutePoint
import com.example.data.model.Waypoint
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

object GpxParser {

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Converts a Route object to a standard GPX 1.1 XML string.
     */
    fun routeToGpx(route: Route): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"Rutas GPX\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        
        // Metadata
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(route.name)}</name>\n")
        if (route.description.isNotEmpty()) {
            sb.append("    <desc>${escapeXml(route.description)}</desc>\n")
        }
        sb.append("    <time>${isoDateFormat.format(Date(route.timestamp))}</time>\n")
        sb.append("  </metadata>\n")

        // Waypoints (Points of Interest)
        val waypoints = route.getPointsOfInterest()
        for (wp in waypoints) {
            sb.append("  <wpt lat=\"${wp.latitude}\" lon=\"${wp.longitude}\">\n")
            sb.append("    <name>${escapeXml(wp.name)}</name>\n")
            if (wp.description.isNotEmpty()) {
                sb.append("    <desc>${escapeXml(wp.description)}</desc>\n")
            }
            if (wp.frontPhotoPath != null || wp.backPhotoPath != null) {
                // Custom extension for our app's dual photos
                sb.append("    <extensions>\n")
                if (wp.frontPhotoPath != null) sb.append("      <front_photo>${escapeXml(wp.frontPhotoPath)}</front_photo>\n")
                if (wp.backPhotoPath != null) sb.append("      <back_photo>${escapeXml(wp.backPhotoPath)}</back_photo>\n")
                sb.append("    </extensions>\n")
            }
            sb.append("    <time>${isoDateFormat.format(Date(wp.timestamp))}</time>\n")
            sb.append("  </wpt>\n")
        }

        // Track
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(route.name)}</name>\n")
        sb.append("    <type>${escapeXml(route.sportType)}</type>\n")
        sb.append("    <trkseg>\n")

        val points = route.getPoints()
        for (pt in points) {
            sb.append("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">\n")
            if (pt.altitude != 0.0) {
                sb.append("        <ele>${pt.altitude}</ele>\n")
            }
            sb.append("        <time>${isoDateFormat.format(Date(pt.timestamp))}</time>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        
        sb.append("</gpx>")
        return sb.toString()
    }

    /**
     * Converts a Route object to a standard KML 2.2 string.
     */
    fun routeToKml(route: Route): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<kml xmlns=\"http://www.opengis.net/kml/2.2\">\n")
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(route.name)}</name>\n")
        if (route.description.isNotEmpty()) {
            sb.append("    <description>${escapeXml(route.description)}</description>\n")
        }

        // Waypoints (Points of Interest)
        val waypoints = route.getPointsOfInterest()
        for (wp in waypoints) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(wp.name)}</name>\n")
            val ptDescStr = buildString {
                if (wp.description.isNotEmpty()) {
                    append(wp.description)
                }
                if (wp.frontPhotoPath != null || wp.backPhotoPath != null) {
                    if (isNotEmpty()) append("\n")
                    append("Fotos duales:\n")
                    if (wp.frontPhotoPath != null) append("- Delantera: ${wp.frontPhotoPath}\n")
                    if (wp.backPhotoPath != null) append("- Trasera: ${wp.backPhotoPath}\n")
                }
            }
            if (ptDescStr.isNotEmpty()) {
                sb.append("      <description>${escapeXml(ptDescStr)}</description>\n")
            }
            sb.append("      <Point>\n")
            sb.append("        <coordinates>${wp.longitude},${wp.latitude},0</coordinates>\n")
            sb.append("      </Point>\n")
            sb.append("    </Placemark>\n")
        }

        // Track Path as LineString
        val points = route.getPoints()
        if (points.isNotEmpty()) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(route.name)} (Trayecto)</name>\n")
            sb.append("      <LineString>\n")
            sb.append("        <extrude>1</extrude>\n")
            sb.append("        <tessellate>1</tessellate>\n")
            sb.append("        <coordinates>\n")
            for (pt in points) {
                sb.append("          ${pt.longitude},${pt.latitude},${pt.altitude}\n")
            }
            sb.append("        </coordinates>\n")
            sb.append("      </LineString>\n")
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    /**
     * Parses a GPX 1.1 XML string into a Route object.
     */
    fun gpxToRoute(gpxString: String, sportType: String = "Senderismo"): Route {
        var name = "Ruta Importada"
        var description = ""
        var timestamp = System.currentTimeMillis()
        
        val points = mutableListOf<RoutePoint>()
        val waypoints = mutableListOf<Waypoint>()
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(ByteArrayInputStream(gpxString.toByteArray()), "UTF-8")
            
            var eventType = parser.eventType
            
            var currentTag: String? = null
            var inMetadata = false
            var inTrk = false
            var inWpt = false
            
            // Temporary parsing fields
            var ptLat = 0.0
            var ptLon = 0.0
            var ptEle = 0.0
            var ptTime: Long = 0
            
            var wpLat = 0.0
            var wpLon = 0.0
            var wpName = ""
            var wpDesc = ""
            var wpFront: String? = null
            var wpBack: String? = null
            var wpTime: Long = 0

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val tagName = parser.name
                
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = tagName
                        when (tagName) {
                            "metadata" -> inMetadata = true
                            "trk" -> inTrk = true
                            "trkpt" -> {
                                val latAttr = parser.getAttributeValue(null, "lat") ?: "0"
                                val lonAttr = parser.getAttributeValue(null, "lon") ?: "0"
                                ptLat = latAttr.toDoubleOrNull() ?: 0.0
                                ptLon = lonAttr.toDoubleOrNull() ?: 0.0
                                ptEle = 0.0
                                ptTime = System.currentTimeMillis()
                            }
                            "wpt" -> {
                                inWpt = true
                                val latAttr = parser.getAttributeValue(null, "lat") ?: "0"
                                val lonAttr = parser.getAttributeValue(null, "lon") ?: "0"
                                wpLat = latAttr.toDoubleOrNull() ?: 0.0
                                wpLon = lonAttr.toDoubleOrNull() ?: 0.0
                                wpName = "Punto de Interés"
                                wpDesc = ""
                                wpFront = null
                                wpBack = null
                                wpTime = System.currentTimeMillis()
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text.trim()
                        if (text.isNotEmpty()) {
                            when (currentTag) {
                                "name" -> {
                                    if (inMetadata) {
                                        name = text
                                    } else if (inWpt) {
                                        wpName = text
                                    }
                                }
                                "desc" -> {
                                    if (inMetadata) {
                                        description = text
                                    } else if (inWpt) {
                                        wpDesc = text
                                    }
                                }
                                "ele" -> {
                                    ptEle = text.toDoubleOrNull() ?: 0.0
                                }
                                "time" -> {
                                    val parsedTime = try {
                                        isoDateFormat.parse(text)?.time
                                    } catch (e: Exception) {
                                        null
                                    }
                                    if (parsedTime != null) {
                                        if (inMetadata) {
                                            timestamp = parsedTime
                                        } else if (inWpt) {
                                            wpTime = parsedTime
                                        } else {
                                            ptTime = parsedTime
                                        }
                                    }
                                }
                                "front_photo" -> {
                                    if (inWpt) wpFront = text
                                }
                                "back_photo" -> {
                                    if (inWpt) wpBack = text
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        currentTag = null
                        when (tagName) {
                            "metadata" -> inMetadata = false
                            "trk" -> inTrk = false
                            "trkpt" -> {
                                points.add(RoutePoint(ptLat, ptLon, ptEle, ptTime))
                            }
                            "wpt" -> {
                                inWpt = false
                                waypoints.add(
                                    Waypoint(
                                        latitude = wpLat,
                                        longitude = wpLon,
                                        name = wpName,
                                        description = wpDesc,
                                        frontPhotoPath = wpFront,
                                        backPhotoPath = wpBack,
                                        timestamp = wpTime
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // recalculate stats if we have points
        var totalDistance = 0.0
        var durationSeconds = 0L
        if (points.size > 1) {
            for (i in 0 until points.size - 1) {
                totalDistance += calculateDistanceKm(points[i], points[i + 1])
            }
            durationSeconds = (points.last().timestamp - points.first().timestamp) / 1000
        }
        if (durationSeconds <= 0) {
            // Simulated duration of 1 hour if it is 0
            durationSeconds = 3600
        }
        
        // dynamic index calculation for safety index (clima, tráfico, etc.)
        val difficultyScore = calculateSimulatedScore()

        return Route(
            name = name,
            description = description,
            pointsJson = Route.pointsToJson(points),
            pointsOfInterestJson = Route.waypointsToJson(waypoints),
            totalDistanceKm = totalDistance,
            totalDurationSeconds = durationSeconds,
            difficultyScore = difficultyScore,
            weatherTrafficInfo = "Clima despejado • Viento 12km/h • Sin tráfico",
            timestamp = timestamp,
            sportType = sportType,
            isSynced = false,
            cloudProvider = null,
            format = "GPX"
        )
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun calculateDistanceKm(p1: RoutePoint, p2: RoutePoint): Double {
        val theta = p1.longitude - p2.longitude
        var dist = Math.sin(Math.toRadians(p1.latitude)) * Math.sin(Math.toRadians(p2.latitude)) +
                Math.cos(Math.toRadians(p1.latitude)) * Math.cos(Math.toRadians(p2.latitude)) *
                Math.cos(Math.toRadians(theta))
        dist = Math.acos(dist)
        dist = Math.toDegrees(dist)
        dist *= 60 * 1.1515 * 1.609344 // to kilometers
        return if (dist.isNaN()) 0.0 else dist
    }

    fun formatDistance(km: Double): String {
        return if (km < 1.0) {
            val meters = Math.round(km * 1000.0).toInt()
            "$meters m"
        } else {
            String.format("%.2f km", km)
        }
    }
    
    fun calculateSimulatedScore(): Float {
        // simulated score between 7.5 and 9.8 by default
        return (75 + (0..23).random()).toFloat() / 10f
    }
}

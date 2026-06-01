package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val pointsJson: String, // Stringified JSON Array of RoutePoints
    val pointsOfInterestJson: String = "[]", // Stringified JSON Array of Waypoints
    val totalDistanceKm: Double = 0.0,
    val totalDurationSeconds: Long = 0,
    val difficultyScore: Float = 5.0f, // 0 to 10
    val weatherTrafficInfo: String = "Buen clima • Sin tráfico",
    val timestamp: Long = System.currentTimeMillis(),
    val sportType: String = "Senderismo",
    val isSynced: Boolean = false,
    val cloudProvider: String? = null,
    val format: String = "GPX"
) {
    // Helper to get points
    fun getPoints(): List<RoutePoint> {
        val list = mutableListOf<RoutePoint>()
        try {
            val array = JSONArray(pointsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    RoutePoint(
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        altitude = obj.optDouble("altitude", 0.0),
                        timestamp = obj.optLong("timestamp", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    // Helper to get points of interest
    fun getPointsOfInterest(): List<Waypoint> {
        val list = mutableListOf<Waypoint>()
        try {
            val array = JSONArray(pointsOfInterestJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    Waypoint(
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        name = obj.optString("name", "Punto de Interés"),
                        description = obj.optString("description", ""),
                        frontPhotoPath = if (obj.isNull("frontPhotoPath")) null else obj.optString("frontPhotoPath", null),
                        backPhotoPath = if (obj.isNull("backPhotoPath")) null else obj.optString("backPhotoPath", null),
                        timestamp = obj.optLong("timestamp", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    companion object {
        fun pointsToJson(points: List<RoutePoint>): String {
            val array = JSONArray()
            for (pt in points) {
                val obj = JSONObject()
                obj.put("latitude", pt.latitude)
                obj.put("longitude", pt.longitude)
                obj.put("altitude", pt.altitude)
                obj.put("timestamp", pt.timestamp)
                array.put(obj)
            }
            return array.toString()
        }

        fun waypointsToJson(waypoints: List<Waypoint>): String {
            val array = JSONArray()
            for (wp in waypoints) {
                val obj = JSONObject()
                obj.put("latitude", wp.latitude)
                obj.put("longitude", wp.longitude)
                obj.put("name", wp.name)
                obj.put("description", wp.description)
                obj.put("frontPhotoPath", wp.frontPhotoPath ?: JSONObject.NULL)
                obj.put("backPhotoPath", wp.backPhotoPath ?: JSONObject.NULL)
                obj.put("timestamp", wp.timestamp)
                array.put(obj)
            }
            return array.toString()
        }
    }
}

data class RoutePoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)

data class Waypoint(
    val latitude: Double,
    val longitude: Double,
    val name: String,
    val description: String = "",
    val frontPhotoPath: String? = null,
    val backPhotoPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

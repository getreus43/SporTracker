package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.database.RouteDao
import com.example.data.model.Route
import com.example.data.model.RoutePoint
import com.example.data.model.Waypoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class RouteRepository(
    private val context: Context,
    private val routeDao: RouteDao
) {
    val allRoutes: Flow<List<Route>> = routeDao.getAllRoutes()

    // Preferences Keys
    private val prefAccentColor = stringPreferencesKey("accent_color")
    private val prefThemeMode = stringPreferencesKey("theme_mode") // "Normal", "OLED", "AMOLED", "Light"
    private val prefCloudProvider = stringPreferencesKey("cloud_provider") // "Google Drive", "iCloud", "OneDrive"
    private val prefUserEmail = stringPreferencesKey("user_email")
    private val prefIsLoggedIn = booleanPreferencesKey("is_logged_in")
    private val prefSaveToWeb = booleanPreferencesKey("save_to_web")
    private val prefMapType = stringPreferencesKey("map_type") // "Carreteras Base", "Topográfico", "Satelital"
    private val prefOverlayTransport = booleanPreferencesKey("overlay_transport") // Overlay layer toggle
    private val prefAutoSaveToGallery = booleanPreferencesKey("auto_save_to_gallery")

    // Preferences Flows
    val accentColor: Flow<String> = context.dataStore.data.map { it[prefAccentColor] ?: "#007AFF" } // Standard iOS blue
    val themeMode: Flow<String> = context.dataStore.data.map { it[prefThemeMode] ?: "Normal" }
    val cloudProvider: Flow<String> = context.dataStore.data.map { it[prefCloudProvider] ?: "Google Drive" }
    val userEmail: Flow<String> = context.dataStore.data.map { it[prefUserEmail] ?: "" }
    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map { it[prefIsLoggedIn] ?: false }
    val saveToWeb: Flow<Boolean> = context.dataStore.data.map { it[prefSaveToWeb] ?: true }
    val mapType: Flow<String> = context.dataStore.data.map { it[prefMapType] ?: "Carreteras Base" }
    val overlayTransport: Flow<Boolean> = context.dataStore.data.map { it[prefOverlayTransport] ?: false }
    val autoSaveToGallery: Flow<Boolean> = context.dataStore.data.map { it[prefAutoSaveToGallery] ?: false }

    // Settings modifiers
    suspend fun setAccentColor(colorHex: String) {
        context.dataStore.edit { it[prefAccentColor] = colorHex }
    }

    suspend fun setMapType(type: String) {
        context.dataStore.edit { it[prefMapType] = type }
    }

    suspend fun setOverlayTransport(enabled: Boolean) {
        context.dataStore.edit { it[prefOverlayTransport] = enabled }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[prefThemeMode] = mode }
    }

    suspend fun setCloudProvider(provider: String) {
        context.dataStore.edit { it[prefCloudProvider] = provider }
    }

    suspend fun login(email: String, provider: String) {
        context.dataStore.edit {
            it[prefUserEmail] = email
            it[prefCloudProvider] = provider
            it[prefIsLoggedIn] = true
        }
        // Force sync all unsynced routes to this cloud provider
        syncAllRoutes()
    }

    suspend fun logout() {
        context.dataStore.edit {
            it[prefUserEmail] = ""
            it[prefIsLoggedIn] = false
        }
    }

    suspend fun setSaveToWeb(save: Boolean) {
        context.dataStore.edit { it[prefSaveToWeb] = save }
    }

    suspend fun setAutoSaveToGallery(enabled: Boolean) {
        context.dataStore.edit { it[prefAutoSaveToGallery] = enabled }
    }

    // Database actions
    suspend fun insertRoute(route: Route): Long {
        val email = userEmail.first()
        val provider = cloudProvider.first()
        val syncToCloud = isLoggedIn.first()
        
        val routeToSave = if (syncToCloud) {
            route.copy(isSynced = true, cloudProvider = provider)
        } else {
            route
        }
        return routeDao.insertRoute(routeToSave)
    }

    suspend fun updateRoute(route: Route) {
        routeDao.updateRoute(route)
    }

    suspend fun deleteRoute(route: Route) {
        routeDao.deleteRoute(route)
    }

    suspend fun clearAllRoutes() {
        allRoutes.first().forEach {
            routeDao.deleteRoute(it)
        }
    }

    suspend fun syncAllRoutes() {
        val provider = cloudProvider.first()
        val status = isLoggedIn.first()
        if (status) {
            val routes = allRoutes.first()
            for (route in routes) {
                if (!route.isSynced || route.cloudProvider != provider) {
                    routeDao.updateRoute(route.copy(isSynced = true, cloudProvider = provider))
                }
            }
        }
    }

    // Prepopulate default routes
    suspend fun prepopulateDefaultRoutes() {
        val currentRoutes = allRoutes.first()
        if (currentRoutes.isEmpty()) {
            val route1 = Route(
                name = "Senda de los Cazadores",
                description = "Espectacular ruta circular en el Parque Nacional de Ordesa y Monte Perdido.",
                pointsJson = generateSimulatedTrackPoints(42.6264, -0.0556, 120, 2500.0),
                pointsOfInterestJson = Route.waypointsToJson(
                    listOf(
                        Waypoint(42.6270, -0.0545, "Mirador de Calcilarruego", "Vistas impresionantes del valle de Ordesa.", frontPhotoPath = "mock_calc_front", backPhotoPath = "mock_calc_back"),
                        Waypoint(42.6288, -0.0232, "Cascada Cola de Caballo", "El salto de agua más famoso de los Pirineos.", frontPhotoPath = "mock_cola_front", backPhotoPath = "mock_cola_back")
                    )
                ),
                totalDistanceKm = 11.2,
                totalDurationSeconds = 14400, // 4 hours
                difficultyScore = 8.5f,
                weatherTrafficInfo = "Despejado • Temp: 19°C • Sin dificultad técnica",
                sportType = "Senderismo",
                isSynced = false,
                format = "GPX"
            )

            val route2 = Route(
                name = "Anillo Verde Ciclista",
                description = "Ruta cicloturista de Madrid que rodea el casco urbano.",
                pointsJson = generateSimulatedTrackPoints(40.4856, -3.6738, 80, 700.0),
                pointsOfInterestJson = Route.waypointsToJson(
                    listOf(
                        Waypoint(40.4870, -3.6710, "Área Recreativa de Valdebebas", "Punto perfecto para descansar y rehidratarse.", frontPhotoPath = "mock_valde_front", backPhotoPath = "mock_valde_back")
                    )
                ),
                totalDistanceKm = 24.5,
                totalDurationSeconds = 5400, // 1.5 hours
                difficultyScore = 9.4f,
                weatherTrafficInfo = "Parcialmente nublado • Viento: 8km/h • Sin incidencias",
                sportType = "Ciclismo",
                isSynced = false,
                format = "GPX"
            )

            val route3 = Route(
                name = "Ruta Urbana El Retiro",
                description = "Circuito de running por los senderos históricos del Parque del Buen Retiro.",
                pointsJson = generateSimulatedTrackPoints(40.4181, -3.6828, 50, 667.0),
                pointsOfInterestJson = Route.waypointsToJson(
                    listOf(
                        Waypoint(40.4150, -3.6830, "Palacio de Cristal", "Precioso pabellón romántico ideal para fotos.", frontPhotoPath = "mock_cristal_front", backPhotoPath = "mock_cristal_back")
                    )
                ),
                totalDistanceKm = 5.0,
                totalDurationSeconds = 1800, // 30 mins
                difficultyScore = 9.8f,
                weatherTrafficInfo = "Soleado • Temp: 22°C • Parque con afluencia media",
                sportType = "Running",
                isSynced = false,
                format = "GPX"
            )

            insertRoute(route1)
            insertRoute(route2)
            insertRoute(route3)
        }
    }

    private fun generateSimulatedTrackPoints(
        startLat: Double,
        startLon: Double,
        count: Int,
        startEle: Double
    ): String {
        val list = mutableListOf<RoutePoint>()
        var currentLat = startLat
        var currentLon = startLon
        var currentEle = startEle
        var currentTime = System.currentTimeMillis() - (count * 10 * 1000)

        // Generate a subtle squiggly spiral/s-shape
        for (i in 0 until count) {
            val angle = i.toDouble() * 0.15
            currentLat += Math.sin(angle) * 0.0006 + 0.0002
            currentLon += Math.cos(angle) * 0.0006 + 0.0001
            currentEle += (Math.sin(i.toDouble() * 0.2) * 5.0) // tiny hills
            currentTime += 10000 // 10s intervals
            list.add(RoutePoint(currentLat, currentLon, currentEle, currentTime))
        }
        return Route.pointsToJson(list)
    }
}

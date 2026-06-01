package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.RouteDatabase
import com.example.data.model.Route
import com.example.data.model.RoutePoint
import com.example.data.model.Waypoint
import com.example.data.repository.RouteRepository
import com.example.utils.GpxParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date

enum class AppTab {
    HOME,
    RECORD,
    ROUTES,
    SETTINGS
}

data class PlaybackState(
    val route: Route? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPointIndex: Int = 0,
    val elapsedSeconds: Long = 0,
    val distanceCoveredKm: Double = 0.0,
    val voiceInstruction: String = "Iniciando navegación...",
    val countdown: Int = -1, // -1: inactive, 3,2,1 active, 0 starting
    val showFinishedDialog: Boolean = false,
    val finishedPhotoFront: String? = null,
    val finishedPhotoBack: String? = null,
    val activeSportMode: String = "Senderismo",
    val userLatitude: Double? = null,
    val userLongitude: Double? = null,
    val isOffRoute: Boolean = false,
    val deviationDistanceMeters: Double = 0.0,
    val isSimulationMode: Boolean = true
)

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedSeconds: Long = 0,
    val distanceKm: Double = 0.0,
    val format: String = "GPX",
    val useExternalGps: Boolean = false,
    val points: List<RoutePoint> = emptyList(),
    val waypoints: List<Waypoint> = emptyList(),
    val countdown: Int = -1, // 3,2,1 countdown, -1: inactive
    val name: String = "Nueva Ruta",
    val activityType: String = "Senderismo",
    val justFinished: Boolean = false
)

class RouteViewModel(application: Application) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    private val repository: RouteRepository
    private var tts: TextToSpeech? = null
    private var ttsEnabled = false

    private var lastDeviationAnnouncementTime = 0L
    private var lastAnnouncedMilestoneIndex = -1
    
    private var locationManager: android.location.LocationManager? = null
    private val locationListener = object : android.location.LocationListener {
        override fun onLocationChanged(location: android.location.Location) {
            updateUserLocation(location.latitude, location.longitude)
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    init {
        val database = RouteDatabase.getDatabase(application)
        repository = RouteRepository(application, database.routeDao())
        
        // Populate defaults on startup
        viewModelScope.launch {
            repository.prepopulateDefaultRoutes()
        }

        // Initialize TTS
        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Tab State
    private val _activeTab = MutableStateFlow(AppTab.HOME)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    fun selectTab(tab: AppTab) {
        // Pause active recording or playback if needed or just keep them running in background! It's better if they keep running!
        _activeTab.value = tab
    }

    // Exposed Flows from Repo
    val routes: StateFlow<List<Route>> = repository.allRoutes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val accentColor: StateFlow<String> = repository.accentColor.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "#007AFF"
    )

    val themeMode: StateFlow<String> = repository.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Normal"
    )

    val cloudProvider: StateFlow<String> = repository.cloudProvider.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Google Drive"
    )

    val userEmail: StateFlow<String> = repository.userEmail.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )

    val isLoggedIn: StateFlow<Boolean> = repository.isLoggedIn.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val saveToWeb: StateFlow<Boolean> = repository.saveToWeb.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true
    )

    val mapType: StateFlow<String> = repository.mapType.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Carreteras Base"
    )

    val overlayTransport: StateFlow<Boolean> = repository.overlayTransport.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    // Route Detail view on Bottom sheet or Detail screen
    private val _selectedRouteId = MutableStateFlow<Long?>(null)
    val selectedRouteId: StateFlow<Long?> = _selectedRouteId.asStateFlow()

    fun selectRoute(id: Long?) {
        _selectedRouteId.value = id
    }

    // Settings actions
    fun setAccentColor(hex: String) = viewModelScope.launch {
        repository.setAccentColor(hex)
    }

    fun setMapType(type: String) = viewModelScope.launch {
        repository.setMapType(type)
    }

    fun setOverlayTransport(enabled: Boolean) = viewModelScope.launch {
        repository.setOverlayTransport(enabled)
    }

    fun setThemeMode(mode: String) = viewModelScope.launch {
        repository.setThemeMode(mode)
    }

    fun login(email: String, provider: String) = viewModelScope.launch {
        repository.login(email, provider)
    }

    fun logout() = viewModelScope.launch {
        repository.logout()
    }

    fun setSaveToWeb(save: Boolean) = viewModelScope.launch {
        repository.setSaveToWeb(save)
    }

    fun deleteRoute(route: Route) = viewModelScope.launch {
        repository.deleteRoute(route)
        if (_selectedRouteId.value == route.id) {
            _selectedRouteId.value = null
        }
    }

    fun importGpx(xmlContent: String, name: String = "Ruta Importada", sport: String = "Senderismo") = viewModelScope.launch {
        try {
            val route = GpxParser.gpxToRoute(xmlContent, sport).copy(
                name = if (name != "Ruta Importada") name else "GPX Importado"
            )
            repository.insertRoute(route)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun exportRouteToGpx(route: Route): String {
        return GpxParser.routeToGpx(route)
    }

    // ==========================================
    // PLAYBACK ENGINE
    // ==========================================
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var playbackJob: Job? = null

    fun startRoutePlayback(route: Route) = viewModelScope.launch {
        lastDeviationAnnouncementTime = 0L
        lastAnnouncedMilestoneIndex = -1

        val firstPt = route.getPoints().firstOrNull()

        // Prepare to play - show circular countdown screen overlay
        _playbackState.value = PlaybackState(
            route = route,
            isPlaying = true,
            countdown = 3,
            activeSportMode = route.sportType,
            userLatitude = firstPt?.latitude,
            userLongitude = firstPt?.longitude,
            isSimulationMode = true
        )

        // Count down 3, 2, 1
        speakText("Sincronizando ruta. Cuenta atrás iniciada.")
        for (i in 3 downTo 1) {
            _playbackState.value = _playbackState.value.copy(countdown = i)
            speakText(i.toString())
            delay(1000)
        }
        
        _playbackState.value = _playbackState.value.copy(countdown = 0)
        delay(500)
        _playbackState.value = _playbackState.value.copy(countdown = -1)
        speakText("Iniciando ruta: " + route.name + ". Sigue las indicaciones por voz.")

        if (!_playbackState.value.isSimulationMode) {
            startGpsTracking()
        }

        // Start clock timer loop
        startPlaybackLoop()
    }

    private fun startPlaybackLoop() {
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            var seconds = 0L
            while (_playbackState.value.isPlaying) {
                if (_playbackState.value.isPaused) {
                    delay(1000)
                    continue
                }
                delay(1000)
                seconds++
                _playbackState.value = _playbackState.value.copy(elapsedSeconds = seconds)
            }
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        _playbackState.value = _playbackState.value.copy(isSimulationMode = enabled)
        if (enabled) {
            stopGpsTracking()
        } else {
            startGpsTracking()
        }
    }

    fun simulateProgressPercent(percent: Float) {
        val state = _playbackState.value
        val route = state.route ?: return
        val points = route.getPoints()
        if (points.isEmpty()) return

        val targetIdx = ((points.size - 1) * (percent / 100f)).toInt().coerceIn(points.indices)
        val pt = points[targetIdx]
        updateUserLocation(pt.latitude, pt.longitude)
    }

    fun simulateDeviation() {
        val state = _playbackState.value
        val route = state.route ?: return
        val points = route.getPoints()
        if (points.isEmpty()) return

        val currentPoint = points[state.currentPointIndex.coerceIn(points.indices)]
        // Simulate approx 100m deviation
        val fakeDevLat = currentPoint.latitude + 0.0009
        val fakeDevLon = currentPoint.longitude + 0.0009
        updateUserLocation(fakeDevLat, fakeDevLon)
    }

    fun simulateReturnToRoute() {
        val state = _playbackState.value
        val route = state.route ?: return
        val points = route.getPoints()
        if (points.isEmpty()) return

        val originalPt = points[state.currentPointIndex.coerceIn(points.indices)]
        updateUserLocation(originalPt.latitude, originalPt.longitude)
    }

    fun startGpsTracking() {
        val app = getApplication<Application>()
        if (locationManager == null) {
            locationManager = app.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
        }
        try {
            val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val hasCoarse = androidx.core.content.ContextCompat.checkSelfPermission(
                app,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            
            if (hasFine || hasCoarse) {
                locationManager?.requestLocationUpdates(
                    android.location.LocationManager.GPS_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
                locationManager?.requestLocationUpdates(
                    android.location.LocationManager.NETWORK_PROVIDER,
                    1000L,
                    1f,
                    locationListener
                )
                val lastGps = locationManager?.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                val lastNet = locationManager?.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                val best = lastGps ?: lastNet
                if (best != null) {
                    updateUserLocation(best.latitude, best.longitude)
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopGpsTracking() {
        try {
            locationManager?.removeUpdates(locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun updateUserLocation(lat: Double, lon: Double) {
        val state = _playbackState.value
        if (!state.isPlaying) return

        val route = state.route ?: return
        val points = route.getPoints()
        if (points.isEmpty()) return

        // Find closest point
        var minDistance = Double.MAX_VALUE
        var closestIdx = 0

        for (i in points.indices) {
            val dist = GpxParser.calculateDistanceKm(
                RoutePoint(lat, lon, 0.0, 0L),
                points[i]
            )
            if (dist < minDistance) {
                minDistance = dist
                closestIdx = i
            }
        }

        val deviationMeters = minDistance * 1000.0
        val isOffRoute = deviationMeters > 50.0 // 50 meters threshold

        val subPoints = points.take(closestIdx + 1)
        var distanceCovered = 0.0
        if (subPoints.size > 1) {
            for (i in 0 until subPoints.size - 1) {
                distanceCovered += GpxParser.calculateDistanceKm(subPoints[i], subPoints[i + 1])
            }
        }

        var voiceText = ""
        val now = System.currentTimeMillis()
        if (isOffRoute && now - lastDeviationAnnouncementTime > 15000) {
            lastDeviationAnnouncementTime = now
            voiceText = "Atención: te has desviado ${deviationMeters.toInt()} metros de la ruta. Por favor, reincorpórate al sendero original por la línea de retorno naranja."
        } else if (!isOffRoute && state.isOffRoute) {
            voiceText = "Ruta recuperada con éxito. Continúa por el sendero principal."
        } else {
            val pct = (closestIdx.toFloat() / points.size.toFloat()) * 100f
            if (closestIdx != state.currentPointIndex) {
                if (closestIdx == 0 && lastAnnouncedMilestoneIndex != 0) {
                    voiceText = "Continúa de frente en trescientos metros por el sendero principal."
                    lastAnnouncedMilestoneIndex = 0
                } else if (pct >= 25f && pct < 50f && lastAnnouncedMilestoneIndex < 25) {
                    voiceText = "En doscientos metros, gira ligeramente a la derecha en dirección a la avenida principal."
                    lastAnnouncedMilestoneIndex = 25
                } else if (pct >= 50f && pct < 75f && lastAnnouncedMilestoneIndex < 50) {
                    voiceText = "Has realizado la mitad de la ruta. El clima sigue agradable y despejado."
                    lastAnnouncedMilestoneIndex = 50
                } else if (pct >= 75f && pct < 95f && lastAnnouncedMilestoneIndex < 75) {
                    voiceText = "Atención, se aproxima un punto de interés: mirador natural de la sierra. Excelente lugar para fotos."
                    lastAnnouncedMilestoneIndex = 75
                } else if (closestIdx >= points.size - 3 && lastAnnouncedMilestoneIndex < 95) {
                    voiceText = "Bordeando la última curva. Estás llegando a tu destino final."
                    lastAnnouncedMilestoneIndex = 95
                }
            }
        }

        if (voiceText.isNotEmpty()) {
            _playbackState.value = _playbackState.value.copy(
                voiceInstruction = voiceText,
                isOffRoute = isOffRoute,
                deviationDistanceMeters = deviationMeters,
                currentPointIndex = closestIdx,
                distanceCoveredKm = distanceCovered,
                userLatitude = lat,
                userLongitude = lon
            )
            speakText(voiceText)
            
            if (closestIdx == points.size - 1 && !isOffRoute) {
                _playbackState.value = _playbackState.value.copy(
                    showFinishedDialog = true,
                    voiceInstruction = "¡Has completado la ruta con éxito!"
                )
            }
        } else {
            _playbackState.value = _playbackState.value.copy(
                isOffRoute = isOffRoute,
                deviationDistanceMeters = deviationMeters,
                currentPointIndex = closestIdx,
                distanceCoveredKm = distanceCovered,
                userLatitude = lat,
                userLongitude = lon
            )
        }
    }

    fun pausePlayback() {
        val state = _playbackState.value
        _playbackState.value = state.copy(isPaused = true)
        speakText("Navegación pausada.")
    }

    fun resumePlayback() {
        val state = _playbackState.value
        _playbackState.value = state.copy(isPaused = false)
        speakText("Reanudando navegación.")
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        stopGpsTracking()
        _playbackState.value = PlaybackState()
    }

    fun setPlaybackFinishedPhotos(front: String, back: String) {
        _playbackState.value = _playbackState.value.copy(
            finishedPhotoFront = front,
            finishedPhotoBack = back
        )
    }

    // ==========================================
    // RECORDING ENGINE
    // ==========================================
    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var recordingJob: Job? = null

    fun startRouteRecording(name: String, sportType: String, format: String, useExternalGps: Boolean) = viewModelScope.launch {
        _recordingState.value = RecordingState(
            isRecording = true,
            countdown = 3,
            format = format,
            useExternalGps = useExternalGps,
            name = name,
            activityType = sportType
        )

        speakText("Preparando para grabar. Tres... dos... uno...")
        for (i in 3 downTo 1) {
            _recordingState.value = _recordingState.value.copy(countdown = i)
            delay(1000)
        }
        
        _recordingState.value = _recordingState.value.copy(countdown = -1)
        speakText("Grabando ruta. En marcha.")

        startRecordingLoop()
    }

    private fun startRecordingLoop() {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            // Simulated start coordinates (Madrid center)
            var currentLat = 40.4168
            var currentLon = -3.7038
            var currentAlt = 650.0
            
            var seconds = 0L

            while (_recordingState.value.isRecording) {
                if (_recordingState.value.isPaused) {
                    delay(1000)
                    continue
                }

                delay(2000) // Coordinate ticks
                seconds += 2

                // Calculate next coordinate with slight random movement to simulate real walking
                val angle = Math.random() * 2 * Math.PI
                val step = 0.0003 // around 30 meters
                currentLat += Math.sin(angle) * step
                currentLon += Math.cos(angle) * step
                currentAlt += (Math.random() - 0.5) * 4.0 // slight alt change

                val newPoint = RoutePoint(currentLat, currentLon, currentAlt, System.currentTimeMillis())
                val updatedPoints = _recordingState.value.points + newPoint

                // recalculate distance
                var distance = 0.0
                if (updatedPoints.size > 1) {
                    for (i in 0 until updatedPoints.size - 1) {
                        distance += GpxParser.calculateDistanceKm(updatedPoints[i], updatedPoints[i + 1])
                    }
                }

                _recordingState.value = _recordingState.value.copy(
                    elapsedSeconds = seconds,
                    points = updatedPoints,
                    distanceKm = distance
                )
            }
        }
    }

    fun pauseRecording() {
        if (_recordingState.value.isRecording) {
            _recordingState.value = _recordingState.value.copy(isPaused = true)
            speakText("Grabación en pausa.")
        }
    }

    fun resumeRecording() {
        if (_recordingState.value.isRecording) {
            _recordingState.value = _recordingState.value.copy(isPaused = false)
            speakText("Grabación reanudada.")
        }
    }

    fun addRecordingWaypoint(name: String, description: String, frontPhoto: String?, backPhoto: String?) {
        val state = _recordingState.value
        val lat = state.points.lastOrNull()?.latitude ?: 40.4168
        val lon = state.points.lastOrNull()?.longitude ?: -3.7038
        
        val newWp = Waypoint(
            latitude = lat,
            longitude = lon,
            name = name,
            description = description,
            frontPhotoPath = frontPhoto,
            backPhotoPath = backPhoto,
            timestamp = System.currentTimeMillis()
        )
        
        _recordingState.value = state.copy(
            waypoints = state.waypoints + newWp
        )
        speakText("Punto de interés añadido: $name")
    }

    fun stopAndSaveRecording() = viewModelScope.launch {
        recordingJob?.cancel()
        val state = _recordingState.value
        
        if (state.points.isNotEmpty()) {
            val score = GpxParser.calculateSimulatedScore()
            val weatherOpts = listOf(
                "Despejado • Temp: 24°C • Sin viento",
                "Soleado • Parque tranquilo • Calor moderado",
                "Nubosidad ligera • Brisa suave • Tráfico nulo",
                "Despejado • Excelente visibilidad • Sin tráfico"
            )
            
            val route = Route(
                name = if (state.name.isNotBlank()) state.name else "Ruta Grabada",
                description = "Grabado en formato ${state.format} el ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}.",
                pointsJson = Route.pointsToJson(state.points),
                pointsOfInterestJson = Route.waypointsToJson(state.waypoints),
                totalDistanceKm = state.distanceKm,
                totalDurationSeconds = state.elapsedSeconds,
                difficultyScore = score,
                weatherTrafficInfo = weatherOpts.random(),
                timestamp = System.currentTimeMillis(),
                sportType = state.activityType,
                isSynced = false,
                format = state.format
            )
            repository.insertRoute(route)
            speakText("¡Ruta grabada con éxito!")
        }

        _recordingState.value = RecordingState(justFinished = true)
        delay(3000)
        _recordingState.value = RecordingState(justFinished = false)
    }

    fun discardRecording() {
        recordingJob?.cancel()
        _recordingState.value = RecordingState()
    }

    // ==========================================
    // TEXT TO SPEECH CALLS
    // ==========================================
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("es", "ES"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                ttsEnabled = true
            }
        }
    }

    private fun speakText(text: String) {
        if (ttsEnabled) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

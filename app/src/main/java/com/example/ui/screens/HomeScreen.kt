package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Route
import com.example.ui.components.RouteMapCanvas
import com.example.ui.viewmodel.RouteViewModel
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class GeocodeResult(
    val displayName: String,
    val latitude: Double,
    val longitude: Double
)

suspend fun searchLocations(query: String): List<GeocodeResult> {
    if (query.isBlank()) return emptyList()
    return withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?format=json&q=$encodedQuery&limit=5"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "AppRutasGPX/1.0 (Android; GPX Route Tracker App)")
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val jsonArray = JSONArray(response.toString())
                val results = mutableListOf<GeocodeResult>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val displayName = obj.optString("display_name", "")
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)
                    if (lat != 0.0 && lon != 0.0) {
                        results.add(GeocodeResult(displayName, lat, lon))
                    }
                }
                results
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val routes by viewModel.routes.collectAsState()
    val rawAccentColor by viewModel.accentColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val overlayTransport by viewModel.overlayTransport.collectAsState()
    
    val accentColor = remember(rawAccentColor) { Color(android.graphics.Color.parseColor(rawAccentColor)) }
    val isAmoled = themeMode in listOf("OLED", "AMOLED")

    // Map selection helper
    var highlightedRoute by remember { mutableStateOf<Route?>(null) }
    
    LaunchedEffect(routes) {
        if (routes.isNotEmpty() && highlightedRoute == null) {
            highlightedRoute = routes.firstOrNull()
        }
    }

    // Set map points
    val mapPoints = remember(highlightedRoute) {
        highlightedRoute?.getPoints() ?: emptyList()
    }
    
    val mapWaypoints = remember(highlightedRoute) {
        highlightedRoute?.getPointsOfInterest() ?: emptyList()
    }
    
    // Search bar states
    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var geocodeResults by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var isSearchingGeocode by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var selectedLocationName by remember { mutableStateOf<String?>(null) }

    // Filter local routes matching query in search
    val filteredLocalRoutes = remember(searchQuery, routes) {
        if (searchQuery.isBlank()) {
            emptyList()
        } else {
            routes.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Debounced real locations search
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 3) {
            delay(600)
            isSearchingGeocode = true
            geocodeResults = searchLocations(searchQuery)
            isSearchingGeocode = false
        } else {
            geocodeResults = emptyList()
        }
    }

    // Sheet expansion state
    var isExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // 1. Full screen interactive map
        RouteMapCanvas(
            points = if (selectedLocation != null) emptyList() else mapPoints,
            waypoints = if (selectedLocation != null) emptyList() else mapWaypoints,
            routeColor = accentColor,
            isAmoled = isAmoled,
            mapType = mapType,
            showTransportOverlay = overlayTransport,
            centerOn = selectedLocation,
            modifier = Modifier.fillMaxSize()
        )

        // Map Float Tools
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 110.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    if (routes.isNotEmpty()) {
                        selectedLocation = null
                        selectedLocationName = null
                        highlightedRoute = routes.random()
                    }
                },
                containerColor = if (isAmoled) Color(0xFF1E1E1E) else Color.White,
                contentColor = accentColor,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.Casino, contentDescription = "Ruta aleatoria")
            }

            FloatingActionButton(
                onClick = {},
                containerColor = if (isAmoled) Color(0xFF1E1E1E) else Color.White,
                contentColor = accentColor,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Centrar GPS")
            }
        }

        // Selected Route Name Pill at Top (Only if not centered on on-demand geolocated pin)
        AnimatedVisibility(
            visible = highlightedRoute != null && selectedLocation == null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                color = (if (isAmoled) Color.Black else MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.9f),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val sportIcon = when (highlightedRoute?.sportType) {
                        "Ciclismo" -> Icons.Default.DirectionsBike
                        "Running" -> Icons.Default.DirectionsRun
                        else -> Icons.Default.DirectionsWalk
                    }
                    Icon(sportIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = highlightedRoute?.name ?: "",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Active geocoded location marker chip below search bar
        AnimatedVisibility(
            visible = selectedLocation != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Surface(
                color = (if (isAmoled) Color(0xFF2C1919) else MaterialTheme.colorScheme.errorContainer).copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (isAmoled) Color(0xFFFF8C8C) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedLocationName ?: "Marcador",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Limpiar marcador",
                        tint = if (isAmoled) Color(0xFFFF8C8C) else MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .size(14.dp)
                            .clickable {
                                selectedLocation = null
                                selectedLocationName = null
                            }
                    )
                }
            }
        }

        // 3. Search Bar Pinned to Top
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = if (isAmoled) BorderStroke(1.dp, Color(0xFF2C2C2E)) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = accentColor,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(24.dp)
                    )
                    
                    TextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            searchActive = it.isNotBlank()
                        },
                        placeholder = {
                            Text(
                                "Buscar rutas, ciudades, montañas...",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("app_search_field")
                    )
                    
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                searchActive = false
                                geocodeResults = emptyList()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar busqueda",
                                tint = Color.Gray,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
                
                // Dropdown of search matches
                AnimatedVisibility(
                    visible = searchActive && (filteredLocalRoutes.isNotEmpty() || geocodeResults.isNotEmpty() || isSearchingGeocode)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        HorizontalDivider(color = if (isAmoled) Color(0xFF2C2C2E) else Color.LightGray.copy(alpha = 0.4f))
                        
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Local routes section matches
                            if (filteredLocalRoutes.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "Rutas Instaladas",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = accentColor,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                }
                                items(filteredLocalRoutes) { route ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                highlightedRoute = route
                                                selectedLocation = null
                                                selectedLocationName = null
                                                searchQuery = ""
                                                searchActive = false
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val sportIcon = when (route.sportType) {
                                            "Ciclismo" -> Icons.Default.DirectionsBike
                                            "Running" -> Icons.Default.DirectionsRun
                                            else -> Icons.Default.DirectionsWalk
                                        }
                                        Icon(sportIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(11.dp))
                                        Column {
                                            Text(
                                                text = route.name,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${route.totalDistanceKm} km • Dificultad: ${route.difficultyScore}/10",
                                                fontSize = 11.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Real Geocoded locations matches
                            if (geocodeResults.isNotEmpty() || isSearchingGeocode) {
                                item {
                                    Text(
                                        text = "Ubicaciones del Mapa (Live)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = accentColor,
                                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)
                                    )
                                }
                                
                                if (isSearchingGeocode) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(color = accentColor, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                        }
                                    }
                                }
                                
                                items(geocodeResults) { loc ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedLocation = Pair(loc.latitude, loc.longitude)
                                                selectedLocationName = loc.displayName.split(",").firstOrNull() ?: loc.displayName
                                                searchQuery = ""
                                                searchActive = false
                                            }
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(11.dp))
                                        Text(
                                            text = loc.displayName,
                                            fontSize = 13.sp,
                                            color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            
                            if (filteredLocalRoutes.isEmpty() && geocodeResults.isEmpty() && !isSearchingGeocode) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "No se encontraron resultados",
                                            fontSize = 13.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Sliding bottom sheet list of routes
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 0.dp) // fits perfectly above bottom bar
        ) {
            Surface(
                color = if (isAmoled) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                shadowElevation = 16.dp,
                border = if (isAmoled) BorderStroke(1.dp, Color(0xFF2C2C2E)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = spring())
                    .height(if (isExpanded) 380.dp else 125.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    // Header slider notch / expand indicator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { isExpanded = !isExpanded }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isAmoled) Color(0xFF3A3A3C) else Color.LightGray)
                        )
                    }

                    // Bottom Sheet Header Title
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rutas Instaladas",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        
                        TextButton(
                            onClick = { isExpanded = !isExpanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
                        ) {
                            Text(if (isExpanded) "Ver menos" else "Expandir listado")
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (routes.isEmpty()) {
                        // Empty local state
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No hay rutas instaladas. Ve a Rutas para importar una.",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    } else if (!isExpanded) {
                        // Compact collapsed single-row map indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val sportIcon = when (highlightedRoute?.sportType) {
                                "Ciclismo" -> Icons.Default.DirectionsBike
                                "Running" -> Icons.Default.DirectionsRun
                                else -> Icons.Default.DirectionsWalk
                            }
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(sportIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                            }
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = highlightedRoute?.name ?: "Selecciona una ruta para ver en mapa",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = highlightedRoute?.let { "${it.totalDistanceKm} km • ${formatDuration(it.totalDurationSeconds)}" } ?: "Carga rutas GPX en la pestaña Rutas",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                            
                            Icon(
                                Icons.Default.KeyboardArrowUp,
                                contentDescription = "Expandir listado",
                                tint = Color.Gray,
                                modifier = Modifier.clickable { isExpanded = true }
                            )
                        }
                    } else {
                        // Expanded full list containing all recorded routes
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 16.dp)
                        ) {
                            items(routes) { route ->
                                val isSelected = route.id == highlightedRoute?.id
                                RouteMinimalItem(
                                    route = route,
                                    isSelected = isSelected,
                                    accentColor = accentColor,
                                    isAmoled = isAmoled,
                                    onClick = { highlightedRoute = route }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RouteMinimalItem(
    route: Route,
    isSelected: Boolean,
    accentColor: Color,
    isAmoled: Boolean,
    onClick: () -> Unit
) {
    val bg = when {
        isSelected -> accentColor.copy(alpha = 0.15f)
        isAmoled -> Color(0xFF2C2C2E)
        else -> Color(0xFFF2F2F7)
    }
    
    val borderColor = if (isSelected) accentColor else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val sportIcon = when (route.sportType) {
            "Ciclismo" -> Icons.Default.DirectionsBike
            "Running" -> Icons.Default.DirectionsRun
            else -> Icons.Default.DirectionsWalk
        }
        
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    if (isSelected) accentColor else accentColor.copy(alpha = 0.15f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                sportIcon,
                contentDescription = null,
                tint = if (isSelected) Color.White else accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = route.name,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${route.totalDistanceKm} km • ${formatDuration(route.totalDurationSeconds)} • Dificultad: ${route.difficultyScore}/10",
                fontSize = 11.sp,
                color = Color.Gray
            )
        }

        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Seleccionado",
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins} min"
}

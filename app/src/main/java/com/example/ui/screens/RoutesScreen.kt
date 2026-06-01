package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Route
import com.example.data.model.Waypoint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.provider.OpenableColumns
import com.example.ui.components.DualCameraCapture
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.components.RouteMapCanvas
import com.example.ui.viewmodel.RouteViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesScreen(
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val routes by viewModel.routes.collectAsState()
    val rawAccentColor by viewModel.accentColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val playbackState by viewModel.playbackState.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val overlayTransport by viewModel.overlayTransport.collectAsState()
    
    val accentColor = remember(rawAccentColor) { Color(android.graphics.Color.parseColor(rawAccentColor)) }
    val isAmoled = themeMode in listOf("OLED", "AMOLED")

    // UI Dialog selectors
    var showImportDialog by remember { mutableStateOf(false) }
    var importGpxContent by remember { mutableStateOf("") }
    var importGpxName by remember { mutableStateOf("") }
    var importSportType by remember { mutableStateOf("Senderismo") }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val content = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (content != null) {
                    importGpxContent = content
                    // Try to extract display name
                    var displayName = ""
                    context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                        val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameCol != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameCol)
                        }
                    }
                    if (displayName.isNotEmpty()) {
                        importGpxName = displayName.substringBeforeLast(".")
                    } else {
                        importGpxName = "GPX Local Importado"
                    }
                    Toast.makeText(context, "¡Archivo GPX cargado con éxito!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al abrir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    var showExportDialog by remember { mutableStateOf<Route?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<Route?>(null) }

    // Navigation sub parts
    var isStatsExpanded by remember { mutableStateOf(false) }
    var activeDualCameraForPlay by remember { mutableStateOf(false) }
    var isCelebCameraActive by remember { mutableStateOf(false) }

    // Dynamic mock GPX template catalogue selector for quick user testing in app!
    val dummyGpxTemplates = remember {
        listOf(
            "Camino de Santiago (León - Santiago)" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Rutas GPX">
                  <metadata>
                    <name>Camino Francés Express</name>
                    <desc>Ruta histórica de senderismo del Camino de Santiago Francés.</desc>
                  </metadata>
                  <trk>
                    <name>Camino Francés</name>
                    <trkseg>
                      <trkpt lat="42.5987" lon="-5.5670"/>
                      <trkpt lat="42.6001" lon="-5.5812"/>
                      <trkpt lat="42.6120" lon="-5.6105"/>
                      <trkpt lat="42.6189" lon="-5.6350"/>
                    </trkseg>
                  </trk>
                </gpx>
            """.trimIndent(),
            "Ruta Pirineos Ibones de Anayet" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <gpx version="1.1" creator="Rutas GPX">
                  <metadata>
                    <name>Ibones de Anayet</name>
                    <desc>Maravillosa ascensión a los lagos glaciares bajo el pico de Anayet.</desc>
                  </metadata>
                  <trk>
                    <name>Ibones de Anayet</name>
                    <trkseg>
                      <trkpt lat="42.7845" lon="-0.4132"/>
                      <trkpt lat="42.7712" lon="-0.4289"/>
                      <trkpt lat="42.7680" lon="-0.4410"/>
                    </trkseg>
                  </trk>
                </gpx>
            """.trimIndent()
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (playbackState.showFinishedDialog) {
            // ==========================================
            // CELEBRATION COMPLETED ARCHIVE VIEW
            // ==========================================
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
                // Background star shapes (Confetti Simulator on Canvas)
                ConfettiRainIndicator(accentColor)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Spacer(modifier = Modifier.height(48.dp))
                    
                    // Award Icon Glow
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(Color(0xFFFFD700).copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WorkspacePremium,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(54.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "¡¡Has llegado!!",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "¡Enhorabuena! Has alcanzado con éxito de forma segura tu destino planificado.",
                        fontSize = 15.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    // Finishing Statistics Grid
                    Surface(
                        color = if (isAmoled) Color(0xFF1E1E1E) else Color(0xFFF2F2F7),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "MÉTRICAS DE LA ACTIVIDAD",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Ruta realizada", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        playbackState.route?.name ?: "Ruta sin nombre",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isAmoled) Color.White else Color.Black
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Distancia Total", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        String.format("%.2f km", playbackState.distanceCoveredKm),
                                        fontWeight = FontWeight.Bold,
                                        color = accentColor,
                                        fontSize = 18.sp
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Duración", fontSize = 11.sp, color = Color.Gray)
                                    Text(
                                        formatDuration(playbackState.elapsedSeconds),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isAmoled) Color.White else Color.Black,
                                        fontSize = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    if (playbackState.finishedPhotoFront != null) {
                        Text(
                            "📸 ¡Foto del Momento guardada!",
                            color = Color(0xFF4CD964),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Button controls
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Taking Dual picture of completion
                        Button(
                            onClick = { isCelebCameraActive = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Foto del Momento (Dual)", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Share Route Intent action
                        OutlinedButton(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "¡He completado la ruta '${playbackState.route?.name}' (${String.format("%.2f km", playbackState.distanceCoveredKm)}) usando la app Rutas GPX!")
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, null)
                                context.startActivity(shareIntent)
                            },
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, accentColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir Actividad", fontWeight = FontWeight.Bold)
                        }

                        // Back to start
                        TextButton(
                            onClick = { viewModel.stopPlayback() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Volver al Menú Principal", color = Color.Gray)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        } else if (playbackState.countdown > 0) {
            // CountDown Screen Overlay during navigation load
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isAmoled) Color.Black else Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "INICIANDO RUTA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = playbackState.countdown.toString(),
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        playbackState.route?.name ?: "",
                        fontSize = 16.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else if (playbackState.isPlaying) {
            // ==========================================
            // PLAYING WORKSPACE GPX TRACKING LIVE DASHBOARD
            // ==========================================
            val route = playbackState.route
            val points = route?.getPoints() ?: emptyList()
            val waypoints = route?.getPointsOfInterest() ?: emptyList()

            Box(modifier = Modifier.fillMaxSize()) {
                // Background Navigation Canvas Map
                RouteMapCanvas(
                    points = points,
                    waypoints = waypoints,
                    currentPointIndex = playbackState.currentPointIndex,
                    routeColor = accentColor,
                    isAmoled = isAmoled,
                    mapType = mapType,
                    showTransportOverlay = overlayTransport,
                    userLatitude = playbackState.userLatitude,
                    userLongitude = playbackState.userLongitude,
                    isOffRoute = playbackState.isOffRoute,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Vocal Instructions Banner Board
                Surface(
                    color = (if (isAmoled) Color.Black else Color(0xFF1E293B)).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 64.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(accentColor.copy(alpha = 0.25f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.VolumeUp,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "INDICACIONES POR VOZ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Gray,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = playbackState.voiceInstruction,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Collapsible sliding statistics bottom sheet
                Surface(
                    color = if (isAmoled) Color(0xFF1C1C1E) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    border = if (isAmoled) BorderStroke(1.dp, Color(0xFF2C2C2E)) else null,
                    shadowElevation = 16.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .animateContentSize()
                        .height(if (isStatsExpanded) 340.dp else 135.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        // Slider notches
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isStatsExpanded = !isStatsExpanded }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (isAmoled) Color(0xFF38383A) else Color.LightGray)
                            )
                        }

                        // Compact header block details
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = route?.name ?: "En Ruta",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Deporte Activo: ${playbackState.activeSportMode}",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                IconButton(
                                    onClick = { activeDualCameraForPlay = true },
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = accentColor.copy(alpha = 0.15f))
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = "Foto Dual", tint = accentColor)
                                }

                                Button(
                                    onClick = { viewModel.stopPlayback() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Detener", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Secondary elements shown under expansion
                        if (!isStatsExpanded) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Distancia cubierta", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        String.format("%.2f / %.2f km", playbackState.distanceCoveredKm, route?.totalDistanceKm ?: 0.0),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isAmoled) Color.White else Color.Black
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Tiempo transcurrido", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        formatDuration(playbackState.elapsedSeconds),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isAmoled) Color.White else Color.Black
                                    )
                                }
                            }
                        } else {
                            // Full stats sheets
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                if (playbackState.isOffRoute) {
                                    item {
                                        Surface(
                                            color = Color(0xFFFFF2E6),
                                            border = BorderStroke(1.dp, Color(0xFFFF9500)),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = "Desvío de Ruta", tint = Color(0xFFFF9500))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text("FUERA DE RUTA", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFFF9500), letterSpacing = 0.5.sp)
                                                    Text("Desviado por ${playbackState.deviationDistanceMeters.toInt()}m de la ruta original. Por favor, regresa al camino indicado por la línea de retorno naranja.", fontSize = 12.sp, color = if (isAmoled) Color.White else Color.DarkGray)
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Surface(
                                        color = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.GpsFixed,
                                                        contentDescription = null,
                                                        tint = accentColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        "Simulador GPS interactivo",
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = if (isAmoled) Color.White else Color.Black
                                                    )
                                                }
                                                androidx.compose.material3.Switch(
                                                    checked = playbackState.isSimulationMode,
                                                    onCheckedChange = { viewModel.setSimulationMode(it) },
                                                    modifier = Modifier.scale(0.8f)
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            val pointsCount = points.size
                                            val progressPercent = if (pointsCount > 1) {
                                                (playbackState.currentPointIndex.toFloat() / (pointsCount - 1).toFloat() * 100f).coerceIn(0f, 100f)
                                            } else 0f

                                            if (playbackState.isSimulationMode) {
                                                Text(
                                                    "Trayecto simulado (${progressPercent.toInt()}%):",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                                
                                                androidx.compose.material3.Slider(
                                                    value = progressPercent,
                                                    onValueChange = { viewModel.simulateProgressPercent(it) },
                                                    valueRange = 0f..100f,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = { viewModel.simulateDeviation() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500)),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                    ) {
                                                        Text("Simular Desvío", fontSize = 11.sp, color = Color.White)
                                                    }
                                                    
                                                    Button(
                                                        onClick = { viewModel.simulateReturnToRoute() },
                                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f),
                                                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                    ) {
                                                        Text("Regresar al camino", fontSize = 11.sp, color = Color.White)
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    "📡 Leyendo datos de ubicación física real de Android en segundo plano para el personaje...",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Live Telemetry Grid
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Distancia", fontSize = 10.sp, color = Color.Gray)
                                                Text(String.format("%.2f km", playbackState.distanceCoveredKm), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = accentColor)
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Tiempo", fontSize = 10.sp, color = Color.Gray)
                                                Text(formatDuration(playbackState.elapsedSeconds), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (isAmoled) Color.White else Color.Black)
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Sport Custom Stats
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                val m1Title = when (playbackState.activeSportMode) {
                                                    "Ciclismo" -> "Ritmo Pedaleo (Cadencia)"
                                                    "Running" -> "Zancadas / min"
                                                    else -> "Esfuerzo Estimado"
                                                }
                                                val m1Val = when (playbackState.activeSportMode) {
                                                    "Ciclismo" -> "84 rpm"
                                                    "Running" -> "162 spm"
                                                    else -> "Cardio: Moderado"
                                                }
                                                Text(m1Title, fontSize = 10.sp, color = Color.Gray)
                                                Text(m1Val, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isAmoled) Color.White else Color.Black)
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier.weight(1f),
                                            color = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text("Condición Inclinación", fontSize = 10.sp, color = Color.Gray)
                                                Text("+120 m acumulados", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isAmoled) Color.White else Color.Black)
                                            }
                                        }
                                    }
                                }

                                item {
                                    // Upcoming POIs item card
                                    Text(
                                        "PUNTOS DE INTERÉS CERCANOS",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                                    )
                                    
                                    if (waypoints.isEmpty()) {
                                        Text("No se han anclados puntos de interés en este sendero.", color = Color.Gray, fontSize = 12.sp)
                                    } else {
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            waypoints.forEach { wp ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                                                        .padding(10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(wp.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isAmoled) Color.White else Color.Black)
                                                    }
                                                    Text(wp.description, fontSize = 11.sp, color = Color.Gray)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ==========================================
            // GENERAL GPX LIST INDEX WORKSPACE VIEW
            // ==========================================
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp)
            ) {
                // Header Details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tus Rutas GPX",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Gestiona, importa y reproduce tus rutas.",
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }

                    // IMPORT WORKSPACE BUTTON
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Importar")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Importar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (routes.isEmpty()) {
                    // Empty list screen details placeholder
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Route,
                            contentDescription = null,
                            tint = Color.LightGray,
                            modifier = Modifier.size(80.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Lista de rutas vacía",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = if (isAmoled) Color.White else Color.Black
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Haz click en Importar arriba a la derecha para cargar cualquier archivo GPX.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(routes) { route ->
                            RouteManagementCard(
                                route = route,
                                accentColor = accentColor,
                                isAmoled = isAmoled,
                                onPlayClick = { viewModel.startRoutePlayback(route) },
                                onExportClick = { showExportDialog = route },
                                onDeleteClick = { showDeleteConfirmDialog = route }
                            )
                        }
                    }
                }
            }
        }

        // ==========================================
        // SYSTEM POPUP INTERACTIONS
        // ==========================================

        // GPX Import Workspace Modal
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                title = { Text("Importar Archivo GPX", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Importa un archivo .gpx desde tu PC/dispositivo para cargarlo en la aplicación, o usa una de nuestras plantillas rápidas si lo prefieres.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        // Selection from storage / PC button
                        Button(
                            onClick = {
                                filePickerLauncher.launch("*/*")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Seleccionar archivo de mi PC", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = (if (isAmoled) Color.DarkGray else Color.LightGray).copy(alpha = 0.5f))
                            Text("O ADELANTE CON", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = (if (isAmoled) Color.DarkGray else Color.LightGray).copy(alpha = 0.5f))
                        }

                        // Rapid import templates
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            dummyGpxTemplates.forEach { tpl ->
                                SuggestionChip(
                                    onClick = {
                                        importGpxName = tpl.first
                                        importGpxContent = tpl.second
                                    },
                                    label = { Text(tpl.first, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = importGpxName,
                            onValueChange = { importGpxName = it },
                            label = { Text("Nombre de la Ruta") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = importGpxContent,
                            onValueChange = { importGpxContent = it },
                            label = { Text("Contenido de Código GPX (XML)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            maxLines = 10
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Actividad:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("Senderismo", "Ciclismo", "Running").forEach { sport ->
                                    val actSel = importSportType == sport
                                    val bg = if (actSel) accentColor else Color.Transparent
                                    val fg = if (actSel) Color.White else accentColor
                                    
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, accentColor, RoundedCornerShape(12.dp))
                                            .background(bg)
                                            .clickable { importSportType = sport }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(sport, fontSize = 11.sp, color = fg, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        onClick = {
                            if (importGpxContent.isNotBlank()) {
                                viewModel.importGpx(
                                    xmlContent = importGpxContent,
                                    name = importGpxName,
                                    sport = importSportType
                                )
                                showImportDialog = false
                                Toast.makeText(context, "¡GPX importado con éxito!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Cargar e Instalar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("Cerrar", color = Color.Gray)
                    }
                }
            )
        }

        // GPX Export Inspector dialog
        if (showExportDialog != null) {
            val routeToExport = showExportDialog!!
            val xmlText = remember(routeToExport) { viewModel.exportRouteToGpx(routeToExport) }

            AlertDialog(
                onDismissRequest = { showExportDialog = null },
                title = { Text("Exportar GPX: ${routeToExport.name}", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Aquí tienes el código XML estándar inter-operable para exportar a cualquier dispositivo Garmin, Polar o Strava.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        OutlinedTextField(
                            value = xmlText,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Código de Ruta GPX") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                        
                        Button(
                            onClick = {
                                val sendIntent: Intent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, xmlText)
                                    type = "text/plain"
                                }
                                val shareIntent = Intent.createChooser(sendIntent, "Exportar Código GPX")
                                context.startActivity(shareIntent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Compartir archivo XML")
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showExportDialog = null },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                    ) {
                        Text("Entendido", color = Color.White)
                    }
                }
            )
        }

        // Delete Confirm popup
        if (showDeleteConfirmDialog != null) {
            val routeToDelete = showDeleteConfirmDialog!!
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = null },
                title = { Text("Borrar Ruta", fontWeight = FontWeight.Bold) },
                text = { Text("¿Deseas desinstalar permanentemente la ruta '${routeToDelete.name}' de este dispositivo?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        onClick = {
                            viewModel.deleteRoute(routeToDelete)
                            showDeleteConfirmDialog = null
                            Toast.makeText(context, "Ruta borrada", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Borrar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = null }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }

        // Play active Camera Overlay
        if (activeDualCameraForPlay) {
            DualCameraCapture(
                onCaptured = { front, back ->
                    activeDualCameraForPlay = false
                    Toast.makeText(context, "¡Captura dual guardada en la ruta!", Toast.LENGTH_SHORT).show()
                },
                onClose = { activeDualCameraForPlay = false }
            )
        }

        // Completion active camera Overlay
        if (isCelebCameraActive) {
            DualCameraCapture(
                onCaptured = { front, back ->
                    viewModel.setPlaybackFinishedPhotos(front, back)
                    isCelebCameraActive = false
                },
                onClose = { isCelebCameraActive = false }
            )
        }
    }
}

@Composable
fun RouteManagementCard(
    route: Route,
    accentColor: Color,
    isAmoled: Boolean,
    onPlayClick: () -> Unit,
    onExportClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAmoled) Color(0xFF1E1E1F) else Color.White
        ),
        border = if (isAmoled) BorderStroke(1.dp, Color(0xFF2C2C2E)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header stats badge + difficulty score
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sportIcon = when (route.sportType) {
                        "Ciclismo" -> Icons.Default.DirectionsBike
                        "Running" -> Icons.Default.DirectionsRun
                        else -> Icons.Default.DirectionsWalk
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(sportIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = route.sportType.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 1.sp
                    )
                }

                // Safety score bubble
                ScoreBubbleIndicator(route.difficultyScore)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Trail details
            Text(
                text = route.name,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = if (isAmoled) Color.White else Color.Black
            )
            
            if (route.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = route.description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sub details metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Distance column
                Column {
                    Text("DISTANCIA", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        String.format("%.2f km", route.totalDistanceKm),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isAmoled) Color.White else Color.Black
                    )
                }

                // Time estimate column
                Column {
                    Text("DURACIÓN", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Text(
                        formatDuration(route.totalDurationSeconds),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isAmoled) Color.White else Color.Black
                    )
                }

                // Sync status badge
                Column(horizontalAlignment = Alignment.End) {
                    Text("NUBE", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (route.isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (route.isSynced) Color(0xFF4CD964) else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (route.isSynced) (route.cloudProvider ?: "Sincro") else "Local",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (route.isSynced) Color(0xFF4CD964) else Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Weather & Difficulty Info Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background((if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7)))
                    .padding(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(route.weatherTrafficInfo, fontSize = 11.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Tool triggers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Secondary buttons (Delete & Export)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onExportClick,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "Exportar", tint = Color.Gray)
                    }

                    IconButton(
                        onClick = onDeleteClick,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = if (isAmoled) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Borrar", tint = Color.Gray)
                    }
                }

                // PLAY ACTION ROUND BUTTON
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("COMENZAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ScoreBubbleIndicator(score: Float) {
    val bubbleBg = when {
        score >= 9.0f -> Color(0xFF4CD964).copy(alpha = 0.2f) // Bright safe green
        score >= 7.5f -> Color(0xFFFF9500).copy(alpha = 0.2f) // Orange warning
        else -> Color(0xFFFF3B30).copy(alpha = 0.2f) // Danger red
    }

    val bubbleText = when {
        score >= 9.0f -> Color(0xFF4CD964)
        score >= 7.5f -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bubbleBg)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SafetyCheck, contentDescription = null, tint = bubbleText, modifier = Modifier.size(12.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = String.format("%.1f/10", score),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = bubbleText
            )
        }
    }
}

@Composable
fun ConfettiRainIndicator(accentColor: Color) {
    val stars = remember {
        List(25) {
            Triple(
                Random.nextFloat(), // X offset ratio
                Random.nextFloat(), // Y offset ratio
                Random.nextInt(4, 10).dp // Star sizing
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        stars.forEach { t ->
            val colorList = listOf(accentColor, Color(0xFFFFD700), Color(0xFF4CD964), Color(0xFFFF2D55), Color(0xFF5AC8FA))
            val selectedCol = remember { colorList.random() }

            Box(
                modifier = Modifier
                    .offset(
                        x = (t.first * 400).dp,
                        y = (t.second * 800).dp
                    )
                    .size(t.third)
                    .background(selectedCol, CircleShape)
            )
        }
    }
}

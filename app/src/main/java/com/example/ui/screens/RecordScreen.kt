package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.DualCameraCapture
import com.example.ui.components.RouteMapCanvas
import com.example.ui.viewmodel.RouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.recordingState.collectAsState()
    val rawAccentColor by viewModel.accentColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val overlayTransport by viewModel.overlayTransport.collectAsState()
    val userLoc by viewModel.userCoordinates.collectAsState()
    val userBearing by viewModel.userBearing.collectAsState()
    val autoSaveToGallery by viewModel.autoSaveToGallery.collectAsState()
    
    val accentColor = remember(rawAccentColor) { Color(android.graphics.Color.parseColor(rawAccentColor)) }
    val isAmoled = themeMode in listOf("OLED", "AMOLED")

    // UI helpers
    var routeName by remember { mutableStateOf("Caminata del Lunes") }
    var selectedSport by remember { mutableStateOf("Senderismo") }
    var selectedFormat by remember { mutableStateOf("GPX") }
    var useExternalGps by remember { mutableStateOf(false) }

    // Dropdowns status
    var showSportMenu by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }

    // Waypoint add status
    var showWaypointDialog by remember { mutableStateOf(false) }
    var waypointName by remember { mutableStateOf("") }
    var waypointDesc by remember { mutableStateOf("") }
    var showDualCameraForWp by remember { mutableStateOf(false) }
    
    var capturedFrontWpPhoto by remember { mutableStateOf<String?>(null) }
    var capturedBackWpPhoto by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        if (state.justFinished) {
            // "¡Ruta grabada!" Animation Screen
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface)
            ) {
                // Wave/pulse visual
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scaleFactor by infiniteTransition.animateFloat(
                    initialValue = 0.8f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .scale(scaleFactor)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor,
                            modifier = Modifier.size(90.dp)
                        ) {
                            Icon(
                                Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier
                                    .padding(24.dp)
                                    .size(44.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = "¡Ruta grabada!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "La ruta se ha estructurado y guardado correctamente en tu dispositivo. Sincronización en curso.",
                        fontSize = 15.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else if (state.countdown > 0) {
            // FULL SCREEN BEAUTIFUL COUNTDOWN VIEW
            val animatedScale = remember { Animatable(0f) }
            
            LaunchedEffect(state.countdown) {
                animatedScale.snapTo(0f)
                animatedScale.animateTo(
                    targetValue = 1.2f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isAmoled) Color.Black else Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "PREPARANDO GPS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = state.countdown.toString(),
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.scale(animatedScale.value)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Ponte en posición de inicio",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        } else if (state.isRecording) {
            // ACTIVE LIVE GPX RECORDING INTERACTIVE VIEW
            Box(modifier = Modifier.fillMaxSize()) {
                // Background live map updating
                RouteMapCanvas(
                    points = state.points,
                    waypoints = state.waypoints,
                    routeColor = accentColor,
                    isAmoled = isAmoled,
                    mapType = mapType,
                    showTransportOverlay = overlayTransport,
                    centerOn = if (state.points.isEmpty()) userLoc else null,
                    userLatitude = userLoc?.first,
                    userLongitude = userLoc?.second,
                    userBearing = userBearing,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Stats Floating Card Panel
                Surface(
                    color = (if (isAmoled) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.85f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, (if (isAmoled) Color.DarkGray else Color.LightGray).copy(alpha = 0.4f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 64.dp)
                        .align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Distancia", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = com.example.utils.GpxParser.formatDistance(state.distanceKm),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        
                        Divider(
                            modifier = Modifier
                                .height(40.dp)
                                .width(1.dp),
                            color = Color.Gray.copy(alpha = 0.3f)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Tiempo", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = formatRecordingTimer(state.elapsedSeconds),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Divider(
                            modifier = Modifier
                                .height(40.dp)
                                .width(1.dp),
                            color = Color.Gray.copy(alpha = 0.3f)
                        )

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Puntos", fontSize = 11.sp, color = Color.Gray)
                            Text(
                                text = "${state.waypoints.size} POI",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                }

                // Bottom Action buttons
                Card(
                    colors = CardDefaults.cardColors(containerColor = (if (isAmoled) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface).copy(alpha = 0.9f)),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 0.dp) // fits on top of tabbar
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (state.isPaused) Color.Yellow else Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (state.isPaused) "GRABACIÓN EN PAUSA" else "GRABANDO EN TIEMPO REAL • ${state.format}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isPaused) Color.Gray else accentColor
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. ADD POINT OF INTEREST WITH DUAL CAMERA
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = { 
                                        waypointName = "Punto de Interés ${state.waypoints.size + 1}"
                                        waypointDesc = ""
                                        capturedFrontWpPhoto = null
                                        capturedBackWpPhoto = null
                                        showWaypointDialog = true 
                                    },
                                    containerColor = accentColor.copy(alpha = 0.15f),
                                    contentColor = accentColor,
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Añadir POI")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Añadir POI", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }

                            // 2. PAUSE / RESUME TRIGGER
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = {
                                        if (state.isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                                    },
                                    containerColor = if (state.isPaused) accentColor else Color(0xFFFF9500),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(72.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = "Pausar/Reanudar",
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    if (state.isPaused) "Reanudar" else "Pausar",
                                    fontSize = 12.sp,
                                    color = if (isAmoled) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // 3. STOP AND FINISH SAVING BACKUP
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FloatingActionButton(
                                    onClick = { viewModel.stopAndSaveRecording() },
                                    containerColor = Color(0xFFFF3B30),
                                    contentColor = Color.White,
                                    modifier = Modifier.size(54.dp),
                                    shape = CircleShape
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Finalizar")
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Finalizar", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        } else {
            // RECORDING FORM PREPARATION INITIAL VIEW
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .padding(top = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Display
                Text(
                    text = "Graba tus Aventuras",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Selecciona tus ajustes de rastreo y genera tu archivo de ruta fácilmente.",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // ROUTE NAME CARD
                OutlinedTextField(
                    value = routeName,
                    onValueChange = { routeName = it },
                    label = { Text("Nombre de la Ruta") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = accentColor) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accentColor,
                        focusedLabelColor = accentColor
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // SPORTS DISCIPLINE DROPDOWN CARD
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showSportMenu = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val sportIcon = when (selectedSport) {
                                    "Ciclismo" -> Icons.Default.DirectionsBike
                                    "Running" -> Icons.Default.DirectionsRun
                                    "Escalada" -> Icons.Default.Terrain
                                    else -> Icons.Default.DirectionsWalk
                                }
                                Icon(sportIcon, contentDescription = null, tint = accentColor)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Tipo de Deporte", fontSize = 11.sp, color = Color.Gray)
                                    Text(selectedSport, fontWeight = FontWeight.Bold, color = if (isAmoled) Color.White else Color.Black)
                                }
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Menú deportes")
                        }
                    }

                    DropdownMenu(
                        expanded = showSportMenu,
                        onDismissRequest = { showSportMenu = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        listOf("Senderismo", "Ciclismo", "Running", "Escalada").forEach { sport ->
                            DropdownMenuItem(
                                text = { Text(sport) },
                                onClick = {
                                    selectedSport = sport
                                    showSportMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // FORMAT EXPORT DROPDOWN CARD
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showFormatMenu = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DataObject, contentDescription = null, tint = accentColor)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text("Formato de Archivo de Ruta", fontSize = 11.sp, color = Color.Gray)
                                    Text(selectedFormat, fontWeight = FontWeight.Bold, color = if (isAmoled) Color.White else Color.Black)
                                }
                            }
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Menú Formato")
                        }
                    }

                    DropdownMenu(
                        expanded = showFormatMenu,
                        onDismissRequest = { showFormatMenu = false },
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        listOf("GPX", "KML", "GeoJSON").forEach { format ->
                            DropdownMenuItem(
                                text = { Text(format) },
                                onClick = {
                                    selectedFormat = format
                                    showFormatMenu = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // EXTERNAL DEVICE SUPPORT SWIPE SWITCH CARD
                OutlinedCard(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = accentColor)
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Vincular GPS Externo", fontWeight = FontWeight.Bold, color = if (isAmoled) Color.White else Color.Black)
                                Text("Acelera la precisión GPS conectando un sensor hardware Garmin/Polar", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        
                        Switch(
                            checked = useExternalGps,
                            onCheckedChange = { useExternalGps = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // START GIANT CIRCLE BUTTON
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(accentColor.copy(alpha = 0.15f), CircleShape)
                            .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                viewModel.startRouteRecording(
                                    name = routeName,
                                    sportType = selectedSport,
                                    format = selectedFormat,
                                    useExternalGps = useExternalGps
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor,
                            modifier = Modifier.size(76.dp)
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.FiberManualRecord, contentDescription = "Iniciar", tint = Color.White, modifier = Modifier.size(24.dp))
                                Text("GRABAR", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // ----------------- CAMERA & DIALOG OVERLAYS -----------------
        if (showWaypointDialog) {
            AlertDialog(
                onDismissRequest = { showWaypointDialog = false },
                title = { Text("Anclar Punto de Interés", fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = waypointName,
                            onValueChange = { waypointName = it },
                            label = { Text("Nombre del Punto") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = waypointDesc,
                            onValueChange = { waypointDesc = it },
                            label = { Text("Descripción / Nota") },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { showDualCameraForWp = true },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Camera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Foto Dual (Selfie + Frontal)")
                        }

                        if (capturedFrontWpPhoto != null) {
                            Text(
                                "✅ ¡Foto Dual Capturada!",
                                color = Color(0xFF4CD964),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        onClick = {
                            viewModel.addRecordingWaypoint(
                                name = waypointName,
                                description = waypointDesc,
                                frontPhoto = capturedFrontWpPhoto,
                                backPhoto = capturedBackWpPhoto
                            )
                            showWaypointDialog = false
                        }
                    ) {
                        Text("Guardar POI", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWaypointDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                }
            )
        }

        if (showDualCameraForWp) {
            DualCameraCapture(
                onCaptured = { front, back ->
                    capturedFrontWpPhoto = front
                    capturedBackWpPhoto = back
                    showDualCameraForWp = false
                    if (autoSaveToGallery) {
                        com.example.utils.GallerySaver.saveImageToPublicGallery(context, front)
                        com.example.utils.GallerySaver.saveImageToPublicGallery(context, back)
                        Toast.makeText(context, "¡Fotos guardadas en la galería!", Toast.LENGTH_SHORT).show()
                    }
                },
                onClose = { showDualCameraForWp = false }
            )
        }
    }
}

fun formatRecordingTimer(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%01d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

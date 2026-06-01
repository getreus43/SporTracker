package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.RouteViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RouteViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val userEmail by viewModel.userEmail.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val cloudProvider by viewModel.cloudProvider.collectAsState()
    val currentAccentHex by viewModel.accentColor.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val saveToWeb by viewModel.saveToWeb.collectAsState()
    val mapType by viewModel.mapType.collectAsState()
    val overlayTransport by viewModel.overlayTransport.collectAsState()

    val accentColor = remember(currentAccentHex) { Color(android.graphics.Color.parseColor(currentAccentHex)) }
    val isAmoled = themeMode in listOf("OLED", "AMOLED")
    
    // Local text inputs
    var inputEmail by remember { mutableStateOf("") }
    var selectedCloud by remember { mutableStateOf("Google Drive") }
    var showCloudSelectMenu by remember { mutableStateOf(false) }

    // Color Swatches Lists (Branding presets)
    val colorPresets = remember {
        listOf(
            "#007AFF" to "Apple iOS Blue",
            "#FF9500" to "Garmin Sunrise",
            "#4CD964" to "Suunto Trail Lime",
            "#FF3B30" to "Polar Summit Red",
            "#5856D6" to "Wahoo Indigo"
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 64.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Main Screen Header details
        Text(
            text = "Ajustes de Sistema",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (isAmoled) Color.White else MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Personaliza tu experiencia de navegación, nube y batería.",
            fontSize = 13.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 1. CLOUD STORAGE PRIVATE ACCOUNT CARD
        // ==========================================
        Text(
            "SINCRONIZACIÓN PRIVADA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (isLoggedIn) {
                    // Active logged-in cloud status display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cloudIcon = when (cloudProvider) {
                            "iCloud" -> Icons.Default.CloudQueue
                            "OneDrive" -> Icons.Default.CloudDone
                            else -> Icons.Default.AddToDrive
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(accentColor.copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(cloudIcon, contentDescription = null, tint = accentColor)
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Conectado a la Nube",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isAmoled) Color.White else Color.Black
                            )
                            Text(
                                text = "$userEmail ($cloudProvider)",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF4CD964))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle for Web backup saves
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Guardar rutas en la web",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = if (isAmoled) Color.White else Color.Black
                            )
                            Text(
                                "Permite sincronizar copias de respaldo públicas en rutas-web.com",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = saveToWeb,
                            onCheckedChange = { viewModel.setSaveToWeb(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = accentColor, checkedTrackColor = accentColor.copy(alpha = 0.5f))
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3B30)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar Sesión", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    // Sign-in configuration form container
                    Text(
                        "Inicia sesión para sincronizar automáticamente el respaldo de tus archivos de ruta en tu proveedor preferido.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = inputEmail,
                        onValueChange = { inputEmail = it },
                        label = { Text("Correo Electrónico") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = accentColor) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = accentColor,
                            focusedLabelColor = accentColor
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Cloud Provider select menu triggers
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { showCloudSelectMenu = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Cloud, contentDescription = null, tint = accentColor)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = selectedCloud,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isAmoled) Color.White else Color.Black
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = showCloudSelectMenu,
                            onDismissRequest = { showCloudSelectMenu = false },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            listOf("Google Drive", "Apple iCloud", "Microsoft OneDrive").forEach { drive ->
                                DropdownMenuItem(
                                    text = { Text(drive) },
                                    onClick = {
                                        selectedCloud = drive
                                        showCloudSelectMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (inputEmail.isNotBlank()) {
                                viewModel.login(inputEmail, selectedCloud)
                                Toast.makeText(context, "Sincronizando con $selectedCloud...", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Por favor ingrese un correo", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Vincular Cuenta Nuve", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 2. BRANDING ACCENT COLOR PALETTES
        // ==========================================
        Text(
            "ESTILO Y PERSONALIZACIÓN DE ACENTO",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Selecciona el color del botón de Play, detalles secundarios e indicadores GPS globales.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    colorPresets.forEach { swatch ->
                        val hex = swatch.first
                        val colorVal = Color(android.graphics.Color.parseColor(hex))
                        val isSelected = currentAccentHex.equals(hex, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colorVal)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isAmoled) Color.White else Color.Black,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.setAccentColor(hex)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Activo",
                                    tint = if (hex == "#4CD964") Color.Black else Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 3. DISPLAY SCREENS SCREEN THEME MODES
        // ==========================================
        Text(
            "TEMA DE PANTALLA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val themePresets = remember { listOf("Normal", "OLED", "AMOLED", "Light") }

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Ajusta los fondos para optimizar la visibilidad durante tus salidas al exterior, o activa el modo AMOLED con negros puros para ahorrar batería.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    themePresets.forEach { mode ->
                        val isSelected = themeMode == mode
                        val checkBg = if (isSelected) accentColor else (if (isAmoled) Color(0xFF2C2C2E) else Color.White)
                        val checkText = if (isSelected) Color.White else (if (isAmoled) Color.White else Color.Black)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(checkBg)
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                    val themeIcon = when (mode) {
                                        "Light" -> Icons.Default.LightMode
                                        "OLED", "AMOLED" -> Icons.Default.BatteryChargingFull
                                        else -> Icons.Default.DarkMode
                                    }
                                Icon(themeIcon, contentDescription = null, tint = if (isSelected) Color.White else accentColor)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (mode) {
                                        "OLED" -> "Modo OLED (Negros puros)"
                                        "AMOLED" -> "Modo AMOLED (Batería Máxima)"
                                        "Light" -> "Modo Claro (Alta visibilidad)"
                                        else -> "Modo Oscuro Convencional"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = checkText
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Default.RadioButtonChecked, contentDescription = "Seleccionado", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ==========================================
        // 4. CONFIGURACIÓN DEL MAPA DE RUTAS
        // ==========================================
        Text(
            "CONFIGURACIÓN DEL MAPA",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isAmoled) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Selecciona el diseño del mapa de fondo para las visualizaciones y graba tus recorridos en el campo.",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Map type options
                val mapTypes = listOf("Carreteras Base", "Topográfico", "Satelital")
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    mapTypes.forEach { type ->
                        val isSelected = when {
                            type.lowercase().contains("topo") && mapType.lowercase().contains("topo") -> true
                            type.lowercase().contains("sate") && mapType.lowercase().contains("sate") -> true
                            type.lowercase().contains("carre") && mapType.lowercase().contains("carre") -> true
                            else -> mapType.equals(type, ignoreCase = true)
                        }
                        val btnBg = if (isSelected) accentColor else (if (isAmoled) Color(0xFF2C2C2E) else Color.White)
                        val btnText = if (isSelected) Color.White else (if (isAmoled) Color.White else Color.Black)
                        val typeIcon = when (type) {
                            "Topográfico" -> Icons.Default.Terrain
                            "Satelital" -> Icons.Default.Satellite
                            else -> Icons.Default.Map
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(btnBg)
                                .clickable { viewModel.setMapType(type) }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    typeIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = type,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = btnText
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = (if (isAmoled) Color.DarkGray else Color.LightGray).copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(16.dp))

                // Public and Private Transit overlay toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isAmoled) Color(0xFF2C2C2E) else Color.White)
                        .clickable { viewModel.setOverlayTransport(!overlayTransport) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.DirectionsTransit, contentDescription = null, tint = accentColor)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Transporte público y privado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (isAmoled) Color.White else Color.Black
                            )
                            Text(
                                text = "Capa extra de metro, bus y tráfico",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                    Switch(
                        checked = overlayTransport,
                        onCheckedChange = { viewModel.setOverlayTransport(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = accentColor
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

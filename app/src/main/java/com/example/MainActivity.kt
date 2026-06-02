package com.example

import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordScreen
import com.example.ui.screens.RoutesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.RouteViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: RouteViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val accentColorHex by viewModel.accentColor.collectAsState()
            
            val accentColor = remember(accentColorHex) { 
                Color(android.graphics.Color.parseColor(accentColorHex)) 
            }

            MyApplicationTheme(
                accentColor = accentColor,
                themeMode = themeMode
            ) {
                // Ensure GPS is examined and requested on app startup
                LocationAndGpsCheck(viewModel = viewModel)

                MainAppLayout(viewModel = viewModel, accentColor = accentColor)
            }
        }
    }
}

private fun isLocationServicesEnabled(context: Context): Boolean {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
           locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocationAndGpsCheck(viewModel: RouteViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var checkGpsTrigger by remember { mutableStateOf(0) }

    // Listen to ON_RESUME so that if the user returns from settings after enabling GPS, we re-check
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkGpsTrigger++
                // Automatically re-trigger physical tracking if permissions are active
                val hasFine = androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFine) {
                     viewModel.startGpsTracking()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val locationPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    // Ask for permissions at start
    LaunchedEffect(Unit) {
        if (!locationPermissionState.allPermissionsGranted) {
            locationPermissionState.launchMultiplePermissionRequest()
        }
    }

    // Call startGpsTracking inside the viewmodel once permission has been successfully granted
    LaunchedEffect(locationPermissionState.allPermissionsGranted) {
        if (locationPermissionState.allPermissionsGranted) {
            viewModel.startGpsTracking()
        }
    }

    var showGpsDisabledDialog by remember { mutableStateOf(false) }

    LaunchedEffect(locationPermissionState.allPermissionsGranted, checkGpsTrigger) {
        if (locationPermissionState.allPermissionsGranted) {
            showGpsDisabledDialog = !isLocationServicesEnabled(context)
        }
    }

    if (showGpsDisabledDialog) {
        AlertDialog(
            onDismissRequest = { showGpsDisabledDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("GPS de Alta Precisión Desactivado", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text(
                    "Para seguir y grabar tus rutas correctamente en tiempo real, es necesario activar los servicios de ubicación (GPS) de tu teléfono.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                ) {
                    Text("Activar GPS", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showGpsDisabledDialog = false }
                ) {
                    Text("Ahora No", color = Color.Gray)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun MainAppLayout(
    viewModel: RouteViewModel,
    accentColor: Color
) {
    val activeTab by viewModel.activeTab.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("system_bottom_navigation_bar"),
                tonalElevation = NavigationBarDefaults.Elevation
            ) {
                // Tab Item: MAPA (Inicio - HOME)
                NavigationBarItem(
                    selected = activeTab == AppTab.HOME,
                    onClick = { viewModel.selectTab(AppTab.HOME) },
                    icon = { Icon(Icons.Default.Map, contentDescription = "Mapa Inicio") },
                    label = { Text("Inicio", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = accentColor,
                        indicatorColor = accentColor
                    ),
                    modifier = Modifier.testTag("tab_button_home")
                )

                // Tab Item: GRABAR (RECORD)
                NavigationBarItem(
                    selected = activeTab == AppTab.RECORD,
                    onClick = { viewModel.selectTab(AppTab.RECORD) },
                    icon = { Icon(Icons.Default.FiberManualRecord, contentDescription = "Grabar Ruta", tint = if (activeTab == AppTab.RECORD) Color.White else Color.Red) },
                    label = { Text("Grabar", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = accentColor,
                        indicatorColor = accentColor
                    ),
                    modifier = Modifier.testTag("tab_button_record")
                )

                // Tab Item: RUTAS (ROUTES)
                NavigationBarItem(
                    selected = activeTab == AppTab.ROUTES,
                    onClick = { viewModel.selectTab(AppTab.ROUTES) },
                    icon = { Icon(Icons.Default.Route, contentDescription = "Lista de Rutas") },
                    label = { Text("Rutas", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = accentColor,
                        indicatorColor = accentColor
                    ),
                    modifier = Modifier.testTag("tab_button_routes")
                )

                // Tab Item: AJUSTES (SETTINGS)
                NavigationBarItem(
                    selected = activeTab == AppTab.SETTINGS,
                    onClick = { viewModel.selectTab(AppTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes de App") },
                    label = { Text("Ajustes", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = accentColor,
                        indicatorColor = accentColor
                    ),
                    modifier = Modifier.testTag("tab_button_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                AppTab.HOME -> HomeScreen(viewModel = viewModel)
                AppTab.RECORD -> RecordScreen(viewModel = viewModel)
                AppTab.ROUTES -> RoutesScreen(viewModel = viewModel)
                AppTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

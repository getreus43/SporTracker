package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
                MainAppLayout(viewModel = viewModel, accentColor = accentColor)
            }
        }
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

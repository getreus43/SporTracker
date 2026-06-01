package com.example.ui.components

import android.Manifest
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaActionSound
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DualCameraCapture(
    onCaptured: (frontPhotoPath: String, backPhotoPath: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Permission State
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Flash states
    var showFlashOverlay by remember { mutableStateOf(false) }
    var isFrontActiveFirst by remember { mutableStateOf(true) }

    // Mock pictures lists for the emulator
    val frontMockPhotos = remember {
        listOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&q=80&w=500", // smiling woman
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&q=80&w=500", // smiling man
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?auto=format&fit=crop&q=80&w=500", // happy woman
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?auto=format&fit=crop&q=80&w=500"  // adventurer man
        )
    }

    val backMockPhotos = remember {
        listOf(
            "https://images.unsplash.com/photo-1464822759023-fed622ff2c3b?auto=format&fit=crop&q=80&w=800", // beautiful mountains
            "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?auto=format&fit=crop&q=80&w=800", // green forest meadow
            "https://images.unsplash.com/photo-1501785888041-af3ef285b470?auto=format&fit=crop&q=80&w=800", // lake reflection
            "https://images.unsplash.com/photo-1472396961693-142e6e269027?auto=format&fit=crop&q=80&w=800"  // sunny woods path
        )
    }

    var selectedFrontIdx by remember { mutableStateOf((0..3).random()) }
    var selectedBackIdx by remember { mutableStateOf((0..3).random()) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Main Camera Feed (Back Camera)
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = rememberAsyncImagePainter(backMockPhotos[selectedBackIdx]),
                    contentDescription = "Cámara Trasera - Paisaje",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Dark bottom overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                )

                // Top indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 48.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color.Red, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "MODO DUAL",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = { 
                            // Randomize pictures to simulate camera focus and movement
                            selectedFrontIdx = (0..3).random()
                            selectedBackIdx = (0..3).random()
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Rotar", tint = Color.White)
                    }
                }

                // Front Camera Feed (Selfie Overlay - Top Right PiP)
                Box(
                    modifier = Modifier
                        .padding(top = 110.dp, end = 16.dp)
                        .size(110.dp, 160.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .background(Color.DarkGray)
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(frontMockPhotos[selectedFrontIdx]),
                        contentDescription = "Cámara Delantera - Selfie",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            "SELFIE",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Shutter Control Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp)
                        .align(Alignment.BottomCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash Icon
                        IconButton(onClick = { }) {
                            Icon(Icons.Filled.FlashOn, contentDescription = "Flash", tint = Color.LightGray)
                        }

                        // Mechanical Shutter Button
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .clip(CircleShape)
                                .clickable {
                                    coroutineScope.launch {
                                        // Sound Click
                                        try {
                                            val sound = MediaActionSound()
                                            sound.play(MediaActionSound.SHUTTER_CLICK)
                                        } catch (e: Exception) {}
                                        
                                        // Flash Trigger Animation
                                        showFlashOverlay = true
                                        delay(150)
                                        showFlashOverlay = false
                                        delay(100)

                                        // Return paths to fake photos
                                        onCaptured(
                                            frontMockPhotos[selectedFrontIdx],
                                            backMockPhotos[selectedBackIdx]
                                        )
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .background(Color.White, CircleShape)
                            )
                        }

                        // GPS Compass Placeholder
                        IconButton(onClick = {}) {
                            Icon(Icons.Filled.Map, contentDescription = "Ubicación", tint = Color.LightGray)
                        }
                    }
                }
            }
        } else {
            // Permission missing
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Cámara",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Permiso de Cámara Requerido",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Para poder tomar fotos duales simultáneas (cámara delantera y trasera a la vez) durante la ruta, es necesario que autorices el uso de la cámara.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Conceder Permiso", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Camera shutter white flash animation
        AnimatedVisibility(
            visible = showFlashOverlay,
            enter = fadeIn(animationSpec = tween(50)),
            exit = fadeOut(animationSpec = tween(150))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            )
        }
    }
}

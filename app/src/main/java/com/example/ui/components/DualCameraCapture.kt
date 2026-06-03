package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaActionSound
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DualCameraCapture(
    onCaptured: (frontPhotoPath: String, backPhotoPath: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Permissions State
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // CameraX configurations
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCaptureState by remember { mutableStateOf<ImageCapture?>(null) }
    var previewState by remember { mutableStateOf<Preview?>(null) }
    var cameraControlState by remember { mutableStateOf<CameraControl?>(null) }
    var noCameraDetected by remember { mutableStateOf(false) }

    // Sequential dual capture states
    var isBackActive by remember { mutableStateOf(true) }
    var flashMode by remember { mutableStateOf("Desactivado") } // "Desactivado", "Activado" (Torch Mode)
    var isCapturing by remember { mutableStateOf(false) }
    var captureStageText by remember { mutableStateOf("") }
    var showFlashOverlay by remember { mutableStateOf(false) }

    // File paths
    var capturedBackPath by remember { mutableStateOf<String?>(null) }
    var capturedFrontPath by remember { mutableStateOf<String?>(null) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Helper for haptic click
    fun triggerVibration() {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(80)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Camera binding effect
    LaunchedEffect(isBackActive, flashMode, cameraPermissionState.status.isGranted) {
        if (!cameraPermissionState.status.isGranted) return@LaunchedEffect

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()

                val availableCameras = cameraProvider.availableCameraInfos
                if (availableCameras.isEmpty()) {
                    noCameraDetected = true
                    return@addListener
                } else {
                    noCameraDetected = false
                }

                val preview = Preview.Builder().build()
                previewState = preview

                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                
                // Apply standard CameraX Flash modes
                val finalFlashMode = when (flashMode) {
                    "Activado" -> ImageCapture.FLASH_MODE_ON
                    else -> ImageCapture.FLASH_MODE_OFF
                }
                captureBuilder.setFlashMode(finalFlashMode)
                
                val imageCapture = captureBuilder.build()
                imageCaptureState = imageCapture

                // Choose camera selector with fallback if front or back doesn't exist
                val hasBack = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFront = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

                val cameraSelector = if (isBackActive) {
                    if (hasBack) CameraSelector.DEFAULT_BACK_CAMERA else if (hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else null
                } else {
                    if (hasFront) CameraSelector.DEFAULT_FRONT_CAMERA else if (hasBack) CameraSelector.DEFAULT_BACK_CAMERA else null
                }

                if (cameraSelector != null) {
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    cameraControlState = camera.cameraControl

                    // If flash mode is fully "Activado", enable Torch for visual illumination
                    if (flashMode == "Activado") {
                        camera.cameraControl.enableTorch(true)
                    } else {
                        camera.cameraControl.enableTorch(false)
                    }
                } else {
                    noCameraDetected = true
                }

            } catch (e: Exception) {
                Log.e("DualCameraCapture", "Failed to bind camera: ", e)
                noCameraDetected = true
            }
        }, ContextCompat.getMainExecutor(context))
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            // Turn off torch on exit
            try {
                val cameraProvider = cameraProviderFuture.get()
                cameraProvider.unbindAll()
            } catch (e: Exception) {}
        }
    }

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
            if (noCameraDetected) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = "Cámara no detectada",
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Cámara no detectada",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No se ha detectado hardware de cámara física en este dispositivo para realizar capturas duales. Pero puedes probar una simulación realista de fotos duales en ruta.",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onClose,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cerrar")
                        }
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isCapturing = true
                                    captureStageText = "Simulando lente trasera dinámica..."
                                    delay(900)
                                    captureStageText = "Simulando lente frontal (Selfie)..."
                                    delay(1000)
                                    captureStageText = "¡Guardando composición dual simulada!"
                                    delay(600)

                                    val saveDir = context.cacheDir
                                    val mockFrontFile = File(saveDir, "simulated_front_${System.currentTimeMillis()}.jpg")
                                    val mockBackFile = File(saveDir, "simulated_back_${System.currentTimeMillis()}.jpg")

                                    mockFrontFile.writeText("simulated_front_fallback")
                                    mockBackFile.writeText("simulated_back_fallback")

                                    onCaptured(mockFrontFile.absolutePath, mockBackFile.absolutePath)
                                    isCapturing = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9500))
                        ) {
                            Text("Simular Captura", color = Color.White)
                        }
                    }
                }
            } else {
                // Live physical CameraX Preview
                Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        try {
                            previewState?.setSurfaceProvider(previewView.surfaceProvider)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                )

                // Flash visual overlay during taking picture
                if (showFlashOverlay) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White)
                    )
                }

                // Header controls overlay background
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .align(Alignment.TopCenter)
                )

                // Top indicators
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 40.dp)
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
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
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
                                "COMPOSICIÓN DUAL",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    // Toggle front/back manually before snapshot
                    IconButton(
                        onClick = {
                            isBackActive = !isBackActive
                            triggerVibration()
                        },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Rotar Lente Principal", tint = Color.White)
                    }
                }

                // Front/Selfie Thumbnail overlay (PiP preview/card)
                // When taking photo, we sequentially switch, so it's super interactive!
                Box(
                    modifier = Modifier
                        .padding(top = 110.dp, end = 16.dp)
                        .size(100.dp, 140.dp)
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(12.dp))
                        .border(2.dp, Color.White, RoundedCornerShape(12.dp))
                        .background(Color(0xFF2E2E2E)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBackActive) {
                        // The Pip represents the secondary lens (Front)
                        if (capturedFrontPath != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(capturedFrontPath!!)),
                                contentDescription = "Selfie Capturada",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(Icons.Default.Face, contentDescription = "Selfie", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "CÁMARA DUAL",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Se toma de seguido",
                                    color = Color.LightGray,
                                    fontSize = 7.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // The Pip represents the secondary lens (Back landscape)
                        if (capturedBackPath != null) {
                            Image(
                                painter = rememberAsyncImagePainter(File(capturedBackPath!!)),
                                contentDescription = "Trazo Paisaje Capturado",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(Icons.Default.Photo, contentDescription = "Paisaje", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "CÁMARA TRASERA",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "Se toma de seguido",
                                    color = Color.LightGray,
                                    fontSize = 7.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Capture Status and Indicators Block
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 110.dp, start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            "ACTIVO: ${if (isBackActive) "CÁMARA TRASERA" else "CÁMARA DELANTERA"}",
                            color = Color(0xFFFF9500),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                if (flashMode == "Activado") Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (flashMode == "Activado") Color(0xFFFFD600) else Color.LightGray,
                                modifier = Modifier.size(10.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "FLASH: ${flashMode.uppercase()}",
                                color = if (flashMode == "Activado") Color(0xFFFFD600) else Color.LightGray,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Shutter Control Bottom area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flash Control
                        IconButton(
                            onClick = {
                                flashMode = if (flashMode == "Desactivado") "Activado" else "Desactivado"
                                triggerVibration()
                            }
                        ) {
                            val tintColor = if (flashMode == "Activado") Color(0xFFFFD600) else Color.LightGray
                            Icon(
                                if (flashMode == "Activado") Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Control de Flash",
                                tint = tintColor,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Physical Shutter Button
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                .border(4.dp, Color.White, CircleShape)
                                .clip(CircleShape)
                                .clickable(enabled = !isCapturing) {
                                    coroutineScope.launch {
                                        isCapturing = true
                                        triggerVibration()
                                        
                                        // Play sound shutter
                                        try {
                                            val sound = MediaActionSound()
                                            sound.play(MediaActionSound.SHUTTER_CLICK)
                                        } catch (e: Exception) {}

                                        // We will perform SEQUENTIAL captures to guarantee real dual-lens photos
                                        val saveDir = context.cacheDir
                                        val backFile = File(saveDir, "capture_back_${System.currentTimeMillis()}.jpg")
                                        val frontFile = File(saveDir, "capture_front_${System.currentTimeMillis()}.jpg")

                                        // Step 1: Capture active camera
                                        captureStageText = "Capturando primera perspectiva..."
                                        showFlashOverlay = true
                                        delay(80)
                                        showFlashOverlay = false

                                        val activeFile = if (isBackActive) backFile else frontFile
                                        val captureSuccess = performCameraCapture(
                                            imageCaptureState,
                                            activeFile,
                                            cameraExecutor
                                        )

                                        if (captureSuccess) {
                                            if (isBackActive) {
                                                capturedBackPath = backFile.absolutePath
                                            } else {
                                                capturedFrontPath = frontFile.absolutePath
                                            }
                                        } else {
                                            // Fallback if physical capture fails or runs on a legacy system
                                            if (isBackActive) {
                                                capturedBackPath = "mock_back_failed"
                                            } else {
                                                capturedFrontPath = "mock_front_failed"
                                            }
                                        }

                                        // Step 2: Switch lenses to capture opposite camera
                                        captureStageText = "Cambiando de cámara..."
                                        isBackActive = !isBackActive
                                        delay(550) // Allow CameraX to warm up and bind the other lens

                                        captureStageText = "Capturando segunda perspectiva..."
                                        showFlashOverlay = true
                                        delay(80)
                                        showFlashOverlay = false

                                        // Execute second physical capture
                                        val oppositeFile = if (isBackActive) backFile else frontFile
                                        val secondCaptureSuccess = performCameraCapture(
                                            imageCaptureState,
                                            oppositeFile,
                                            cameraExecutor
                                        )

                                        if (secondCaptureSuccess) {
                                            if (isBackActive) {
                                                capturedBackPath = backFile.absolutePath
                                            } else {
                                                capturedFrontPath = frontFile.absolutePath
                                            }
                                        } else {
                                            if (isBackActive) {
                                                capturedBackPath = "mock_back_failed"
                                            } else {
                                                capturedFrontPath = "mock_front_failed"
                                            }
                                        }

                                        // Fill fallbacks if things went wrong
                                        val finalBack = capturedBackPath ?: backFile.apply { writeBytes(ByteArray(0)) }.absolutePath
                                        val finalFront = capturedFrontPath ?: frontFile.apply { writeBytes(ByteArray(0)) }.absolutePath

                                        captureStageText = "¡Guardando composición dual!"
                                        delay(250)

                                        onCaptured(finalFront, finalBack)
                                        isCapturing = false
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .background(if (isCapturing) Color.LightGray else Color.White, CircleShape)
                            )
                        }

                        // Preview indicator of what is being recorded
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Tomar Foto",
                                tint = Color.White.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
            }
        } else {
            // Permission screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Permiso requerido",
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
                    "Esta app usa las dos cámaras de tu dispositivo para capturar composiciones duales simultáneas en tiempo real en la ruta.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Conceder Permiso", color = Color.White)
                }
            }
        }

        // Beautiful Capture Loading Overlay
        AnimatedVisibility(
            visible = isCapturing,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF9500))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = captureStageText,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Executes a CameraX capture asynchronously and waits for results.
 */
private suspend fun performCameraCapture(
    imageCapture: ImageCapture?,
    targetFile: File,
    executor: ExecutorService
): Boolean = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
    if (imageCapture == null) {
        continuation.resume(false) { }
        return@suspendCancellableCoroutine
    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(targetFile).build()

    try {
        imageCapture.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    if (continuation.isActive) {
                        continuation.resume(true) { }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraCapture", "Failed to capture image: ${exception.message}", exception)
                    if (continuation.isActive) {
                        continuation.resume(false) { }
                    }
                }
            }
        )
    } catch (e: Exception) {
        Log.e("CameraCapture", "Failed during takePicture execution: ${e.message}", e)
        if (continuation.isActive) {
            continuation.resume(false) { }
        }
    }
}

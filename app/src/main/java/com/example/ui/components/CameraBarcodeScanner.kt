package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.BrandAccent
import com.example.ui.theme.BrandPrimary
import com.example.ui.theme.BrandWhite
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Live CameraX + ML Kit Barcode scanner supporting QR and Aztec codes (§2 & §7).
 */
@Composable
fun CameraBarcodeScanner(
    onCodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var lastScanMs by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCameraPermission = granted
    }

    if (!hasCameraPermission) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CameraAlt,
                    contentDescription = "Camera permission needed",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "Camera permission required",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Allow camera access to scan live store QR and Aztec attendance codes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.minimumInteractiveComponentSize(),
                ) {
                    Text("Grant Camera Access")
                }
            }
        }
        return
    }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            analyzerExecutor.shutdown()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(310.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BrandPrimary),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        runCatching {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { analysis ->
                                    analysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                        processImageProxy(
                                            imageProxy = imageProxy,
                                            scanner = barcodeScanner,
                                            onRawValue = { raw ->
                                                val now = System.currentTimeMillis()
                                                if (now - lastScanMs > 2500L) {
                                                    lastScanMs = now
                                                    onCodeScanned(raw)
                                                }
                                            },
                                        )
                                    }
                                }
                            cameraProvider.unbindAll()
                            boundCamera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis,
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
        )

        // Targeting Reticle
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .border(2.5.dp, BrandAccent, RoundedCornerShape(18.dp)),
        )

        // Torch toggle
        IconButton(
            onClick = {
                torchEnabled = !torchEnabled
                boundCamera?.cameraControl?.enableTorch(torchEnabled)
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(BrandPrimary.copy(alpha = 0.65f), RoundedCornerShape(12.dp)),
        ) {
            Icon(
                imageVector = if (torchEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle flashlight",
                tint = BrandWhite,
            )
        }

        // Caption pill
        Surface(
            color = BrandPrimary.copy(alpha = 0.78f),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 14.dp),
        ) {
            Text(
                text = "Align store QR or Aztec code inside frame",
                style = MaterialTheme.typography.labelSmall,
                color = BrandWhite,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalGetImage::class)
private fun processImageProxy(
    imageProxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onRawValue: (String) -> Unit,
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes ->
            val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (!raw.isNullOrBlank()) {
                onRawValue(raw)
            }
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}

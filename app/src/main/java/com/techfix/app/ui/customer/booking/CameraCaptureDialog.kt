package com.techfix.app.ui.customer.booking

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview as CameraXPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import com.techfix.app.core.designsystem.FixoraSpacing
import com.techfix.app.core.designsystem.FixoraTheme
import java.io.File
import java.util.UUID

@Composable
fun CameraCaptureDialog(
    onImageCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(Unit) {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await()
            check(provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
            val preview = CameraXPreview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder().build()
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            cameraProvider = provider
            imageCapture = capture
        }.onFailure {
            onError("Camera is unavailable right now. You can choose a photo from your gallery instead.")
            onDismiss()
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })

            IconButton(
                onClick = onDismiss,
                enabled = !isCapturing,
                modifier = Modifier.align(Alignment.TopStart).padding(FixoraSpacing.md),
                colors = IconButtonDefaults.iconButtonColors(containerColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)),
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close camera", tint = androidx.compose.ui.graphics.Color.White)
            }

            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = androidx.compose.ui.graphics.Color.White,
                )
            }

            IconButton(
                onClick = {
                    val capture = imageCapture ?: return@IconButton
                    isCapturing = true
                    val outputFile = File(context.cacheDir, "capture_${UUID.randomUUID()}.jpg")
                    val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                isCapturing = false
                                if (outputFile.exists() && outputFile.length() > 0L) {
                                    onImageCaptured(Uri.fromFile(outputFile))
                                    onDismiss()
                                } else {
                                    outputFile.delete()
                                    onError("The photo could not be saved. Please try again.")
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                outputFile.delete()
                                onError("The photo could not be captured. Please try again.")
                            }
                        },
                    )
                },
                enabled = imageCapture != null && !isCapturing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(FixoraSpacing.xl)
                    .size(72.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = FixoraTheme.extendedColors.accent,
                    contentColor = FixoraTheme.extendedColors.onAccent,
                ),
            ) {
                Icon(
                    Icons.Rounded.PhotoCamera,
                    contentDescription = "Take photo",
                    modifier = Modifier.size(32.dp),
                )
            }
        }
    }
}

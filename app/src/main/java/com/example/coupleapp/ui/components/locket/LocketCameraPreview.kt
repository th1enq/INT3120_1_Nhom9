package com.example.coupleapp.ui.components.locket

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.coupleapp.data.model.CameraState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Camera preview component similar to Locket app
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun LocketCameraPreview(
    cameraState: CameraState,
    onFlashToggle: () -> Unit,
    onCameraToggle: () -> Unit,
    onZoomToggle: () -> Unit,
    onCapture: (Bitmap?) -> Unit,
    onGalleryClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var hasCameraPermission by remember { mutableStateOf(false) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera: Camera? by remember { mutableStateOf(null) }
    var previewView: PreviewView? by remember { mutableStateOf(null) }
    
    // Request camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    
    LaunchedEffect(cameraPermissionState.status.isGranted) {
        hasCameraPermission = cameraPermissionState.status.isGranted
        if (!hasCameraPermission) {
            cameraPermissionState.launchPermissionRequest()
        }
    }
    
    // Camera binding effect
    LaunchedEffect(hasCameraPermission, cameraState.isFrontCamera, previewView) {
        if (hasCameraPermission && previewView != null) {
            try {
                val cameraProvider = ProcessCameraProvider.getInstance(context).get()
                
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView!!.surfaceProvider)
                    }
                
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                
                val cameraSelector = if (cameraState.isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }
                
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture
                )
                
                Log.d("LocketCamera", "Camera bound successfully")
            } catch (e: Exception) {
                Log.e("LocketCamera", "Camera binding failed", e)
            }
        }
    }
    
    // Update camera settings
    LaunchedEffect(cameraState.zoomLevel, cameraState.isFlashOn, camera) {
        camera?.let {
            it.cameraControl.setZoomRatio(cameraState.zoomLevel)
            it.cameraControl.enableTorch(cameraState.isFlashOn)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Camera preview box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                // Camera Preview
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        }.also { previewView = it }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Permission request UI
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(64.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(R.string.camera_access_required),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 14.sp
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    TextButton(
                        onClick = { cameraPermissionState.launchPermissionRequest() }
                    ) {
                        Text(
                            text = stringResource(R.string.grant_permission),
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Flash button (top left)
            IconButton(
                onClick = onFlashToggle,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = if (cameraState.isFlashOn) 
                        Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = "Flash",
                    tint = if (cameraState.isFlashOn) Color(0xFFFFD700) else Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Zoom indicator (bottom center)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { onZoomToggle() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "${cameraState.zoomLevel}x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Camera controls
        CameraControls(
            onGalleryClick = onGalleryClick,
            onCapture = {
                // Capture photo logic
                val capture = imageCapture
                if (capture != null) {
                    capture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                // Convert to bitmap with correct rotation
                                val bitmap = image.toBitmap(cameraState.isFrontCamera)
                                onCapture(bitmap)
                                image.close()
                            }
                            
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("LocketCamera", "Photo capture failed", exception)
                                onCapture(null)
                            }
                        }
                    )
                }
            },
            onCameraToggle = onCameraToggle
        )
    }
}

/**
 * Camera control buttons (Gallery, Capture, Flip)
 */
@Composable
private fun CameraControls(
    onGalleryClick: () -> Unit,
    onCapture: () -> Unit,
    onCameraToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gallery button
        IconButton(
            onClick = onGalleryClick,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = "Gallery",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(28.dp)
            )
        }
        
        // Capture button
        CaptureButton(onClick = onCapture)
        
        // Flip camera button
        IconButton(
            onClick = onCameraToggle,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFF5F5F5))
        ) {
            Icon(
                imageVector = Icons.Outlined.Cameraswitch,
                contentDescription = "Flip Camera",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * Main capture button with animation
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4CAF50)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "captureScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .size(76.dp)
            .clip(CircleShape)
            .border(
                width = 4.dp,
                color = Color(0xFF2D2D2D),
                shape = CircleShape
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Inner highlight
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    color.copy(alpha = 0.3f)
                )
        )
    }
}

/**
 * Extension to convert ImageProxy to Bitmap with correct rotation
 */
private fun ImageProxy.toBitmap(isFrontCamera: Boolean): Bitmap {
    val buffer = planes[0].buffer
    buffer.rewind()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    
    // Get rotation from image info
    val rotationDegrees = imageInfo.rotationDegrees
    
    // Create transformation matrix
    val matrix = Matrix()
    
    // Apply rotation
    if (rotationDegrees != 0) {
        matrix.postRotate(rotationDegrees.toFloat())
    }
    
    // Mirror horizontally for front camera
    if (isFrontCamera) {
        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
    }
    
    // Apply transformation if needed
    return if (rotationDegrees != 0 || isFrontCamera) {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    } else {
        bitmap
    }
}

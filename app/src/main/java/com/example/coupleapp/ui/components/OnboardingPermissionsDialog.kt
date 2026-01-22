package com.example.coupleapp.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.coupleapp.util.BatteryOptimizationHelper
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.delay

/**
 * Onboarding Permissions Dialog
 * Shown after first install to guide users through essential permissions
 * 
 * Flow:
 * 1. Welcome page
 * 2. Location permission
 * 3. Activity Recognition (for Sleep API)
 * 4. Notifications
 * 5. Battery Optimization
 * 6. Completion
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun OnboardingPermissionsDialog(
    onComplete: () -> Unit,
    onDismiss: () -> Unit = onComplete
) {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf(0) }
    var visible by remember { mutableStateOf(false) }
    
    // Permission states
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    
    val activityRecognitionPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        rememberPermissionState(Manifest.permission.ACTIVITY_RECOGNITION)
    } else null
    
    val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else null
    
    val isBatteryOptimized by remember {
        derivedStateOf { BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context) }
    }
    
    // Total pages
    val totalPages = 5 // Welcome, Location, Activity, Notification, Battery
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFFFF5F8),
                                Color.White
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Progress indicator
                    LinearProgressIndicator(
                        progress = { (currentPage + 1).toFloat() / totalPages },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = Color(0xFFFF6B9D),
                        trackColor = Color(0xFFFFE4EC)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Content based on current page
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = {
                            (fadeIn(tween(300)) + slideInHorizontally { it / 4 }) togetherWith
                                    (fadeOut(tween(300)) + slideOutHorizontally { -it / 4 })
                        },
                        label = "page"
                    ) { page ->
                        when (page) {
                            0 -> WelcomePage()
                            1 -> PermissionPage(
                                icon = Icons.Filled.LocationOn,
                                iconColor = Color(0xFF4CAF50),
                                title = "Chia sẻ vị trí",
                                description = "Cho phép chia sẻ vị trí để người yêu biết bạn đang ở đâu và gửi thông báo khi bạn đến các địa điểm quan trọng.",
                                isGranted = locationPermission.status.isGranted,
                                onRequestPermission = {
                                    if (locationPermission.status.shouldShowRationale) {
                                        openAppSettings(context)
                                    } else {
                                        locationPermission.launchPermissionRequest()
                                    }
                                }
                            )
                            2 -> PermissionPage(
                                icon = Icons.Filled.Bedtime,
                                iconColor = Color(0xFF673AB7),
                                title = "Theo dõi giấc ngủ",
                                description = "Cho phép theo dõi hoạt động để tự động phát hiện giấc ngủ và chia sẻ với người yêu.",
                                isGranted = activityRecognitionPermission?.status?.isGranted ?: true,
                                onRequestPermission = {
                                    activityRecognitionPermission?.let {
                                        if (it.status.shouldShowRationale) {
                                            openAppSettings(context)
                                        } else {
                                            it.launchPermissionRequest()
                                        }
                                    }
                                },
                                isOptional = true
                            )
                            3 -> PermissionPage(
                                icon = Icons.Filled.Notifications,
                                iconColor = Color(0xFFFF9800),
                                title = "Nhận thông báo",
                                description = "Cho phép nhận thông báo để biết khi người yêu nhớ bạn, gửi tin nhắn hoặc đến/rời địa điểm.",
                                isGranted = notificationPermission?.status?.isGranted ?: true,
                                onRequestPermission = {
                                    notificationPermission?.let {
                                        if (it.status.shouldShowRationale) {
                                            openAppSettings(context)
                                        } else {
                                            it.launchPermissionRequest()
                                        }
                                    }
                                },
                                isOptional = true
                            )
                            4 -> BatteryOptimizationPage(
                                isOptimized = isBatteryOptimized,
                                onOpenSettings = {
                                    BatteryOptimizationHelper.openBatteryOptimizationSettings(context)
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Navigation buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (currentPage == 0) Arrangement.Center else Arrangement.SpaceBetween
                    ) {
                        if (currentPage > 0) {
                            TextButton(onClick = { currentPage-- }) {
                                Text("Quay lại", color = Color(0xFF718096))
                            }
                        }
                        
                        Button(
                            onClick = {
                                if (currentPage < totalPages - 1) {
                                    currentPage++
                                } else {
                                    onComplete()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF6B9D)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .then(
                                    if (currentPage == 0) Modifier.fillMaxWidth()
                                    else Modifier
                                )
                        ) {
                            Text(
                                text = when {
                                    currentPage == 0 -> "Bắt đầu 💕"
                                    currentPage == totalPages - 1 -> "Hoàn tất"
                                    else -> "Tiếp theo"
                                },
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                    
                    // Skip link (except for welcome and last page)
                    if (currentPage in 1 until totalPages - 1) {
                        TextButton(
                            onClick = onComplete,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Bỏ qua và cài đặt sau",
                                fontSize = 12.sp,
                                color = Color(0xFFA0AEC0)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WelcomePage() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutCubic),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Animated heart icon
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF6B9D),
                            Color(0xFFFF8E53)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Text(
            text = "Chào mừng đến với\nCouple App! 💕",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748),
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
        
        Text(
            text = "Để trải nghiệm đầy đủ các tính năng như chia sẻ vị trí, theo dõi giấc ngủ và nhận thông báo, app cần một số quyền.",
            fontSize = 14.sp,
            color = Color(0xFF718096),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        // Feature highlights
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFF7FAFC)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureItem(
                    icon = Icons.Filled.LocationOn,
                    text = "Chia sẻ vị trí realtime",
                    color = Color(0xFF4CAF50)
                )
                FeatureItem(
                    icon = Icons.Filled.Bedtime,
                    text = "Theo dõi giấc ngủ tự động",
                    color = Color(0xFF673AB7)
                )
                FeatureItem(
                    icon = Icons.Filled.NotificationsActive,
                    text = "Nhận thông báo từ người yêu",
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun FeatureItem(
    icon: ImageVector,
    text: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontSize = 13.sp,
            color = Color(0xFF4A5568)
        )
    }
}

@Composable
private fun PermissionPage(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    description: String,
    isGranted: Boolean,
    onRequestPermission: () -> Unit,
    isOptional: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(40.dp)
            )
        }
        
        // Title with optional badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
            if (isOptional) {
                Text(
                    text = "Tùy chọn",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF718096),
                    modifier = Modifier
                        .background(Color(0xFFE2E8F0), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        
        Text(
            text = description,
            fontSize = 14.sp,
            color = Color(0xFF718096),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        // Status indicator
        AnimatedContent(
            targetState = isGranted,
            transitionSpec = {
                (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut())
            },
            label = "status"
        ) { granted ->
            if (granted) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đã cho phép",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                Button(
                    onClick = onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = iconColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Cho phép quyền",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationPage(
    isOptimized: Boolean,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val instructions = remember { BatteryOptimizationHelper.getBatteryOptimizationInstructions(context) }
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFFFF9800).copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.BatteryChargingFull,
                contentDescription = null,
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(40.dp)
            )
        }
        
        Text(
            text = "Tắt tiết kiệm pin",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )
        
        Text(
            text = "Để nhận thông báo đầy đủ và cập nhật vị trí chính xác, vui lòng tắt tối ưu pin cho ứng dụng.",
            fontSize = 14.sp,
            color = Color(0xFF718096),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        // Status
        AnimatedContent(
            targetState = isOptimized,
            transitionSpec = {
                (fadeIn() + scaleIn()) togetherWith (fadeOut() + scaleOut())
            },
            label = "battery_status"
        ) { optimized ->
            if (optimized) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFE8F5E9)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Đã tắt tiết kiệm pin",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Mở cài đặt pin",
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    
                    // Manufacturer-specific instructions
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF3E0)
                    ) {
                        Text(
                            text = instructions,
                            fontSize = 11.sp,
                            color = Color(0xFFE65100),
                            modifier = Modifier.padding(12.dp),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        // Fallback to general settings
        val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    }
}

package com.example.coupleapp.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.coupleapp.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Notification Settings Screen - Allows user to manage notification preferences
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    // Notification settings
    var pushEnabled by remember { mutableStateOf(true) }
    var locketNotifications by remember { mutableStateOf(true) }
    var missingNotifications by remember { mutableStateOf(true) }
    var questNotifications by remember { mutableStateOf(true) }
    var calendarReminders by remember { mutableStateOf(true) }
    var sleepReminders by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(true) }
    var vibrationEnabled by remember { mutableStateOf(true) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notifications),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF2D3748)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF5F8),
                            Color(0xFFFFFBF5),
                            Color(0xFFFFFAF0)
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // General section
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600)) +
                            slideInVertically(animationSpec = tween(600)) { it / 4 }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.general),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF718096),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column {
                                NotificationToggleItem(
                                    icon = Icons.Filled.Notifications,
                                    title = "Push Notifications",
                                    subtitle = "Receive notifications on your device",
                                    checked = pushEnabled,
                                    onCheckedChange = { pushEnabled = it }
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.VolumeUp,
                                    title = "Sound",
                                    subtitle = "Play sound for notifications",
                                    checked = soundEnabled,
                                    onCheckedChange = { soundEnabled = it },
                                    enabled = pushEnabled
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.Vibration,
                                    title = "Vibration",
                                    subtitle = "Vibrate for notifications",
                                    checked = vibrationEnabled,
                                    onCheckedChange = { vibrationEnabled = it },
                                    enabled = pushEnabled
                                )
                            }
                        }
                    }
                }
                
                // Features section
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600, delayMillis = 100)) +
                            slideInVertically(animationSpec = tween(600, delayMillis = 100)) { it / 4 }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.features_section),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF718096),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 2.dp
                        ) {
                            Column {
                                NotificationToggleItem(
                                    icon = Icons.Filled.Favorite,
                                    title = "Locket",
                                    subtitle = "When partner sends a locket",
                                    checked = locketNotifications,
                                    onCheckedChange = { locketNotifications = it },
                                    enabled = pushEnabled
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.FavoriteBorder,
                                    title = "Missing",
                                    subtitle = "When partner sends a miss",
                                    checked = missingNotifications,
                                    onCheckedChange = { missingNotifications = it },
                                    enabled = pushEnabled
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.EmojiEvents,
                                    title = "Quests",
                                    subtitle = "Daily quest reminders",
                                    checked = questNotifications,
                                    onCheckedChange = { questNotifications = it },
                                    enabled = pushEnabled
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.CalendarMonth,
                                    title = "Calendar",
                                    subtitle = "Anniversary and event reminders",
                                    checked = calendarReminders,
                                    onCheckedChange = { calendarReminders = it },
                                    enabled = pushEnabled
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                NotificationToggleItem(
                                    icon = Icons.Filled.Nightlight,
                                    title = "Sleep Reminders",
                                    subtitle = "Bedtime and wake-up reminders",
                                    checked = sleepReminders,
                                    onCheckedChange = { sleepReminders = it },
                                    enabled = pushEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color(0xFF718096) else Color(0xFFB0B0B0),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) Color(0xFF2D3748) else Color(0xFFB0B0B0)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = if (enabled) Color(0xFF718096) else Color(0xFFD0D0D0)
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFFFF6B9D),
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE0E0E0)
            )
        )
    }
}

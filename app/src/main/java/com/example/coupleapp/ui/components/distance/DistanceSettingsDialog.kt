package com.example.coupleapp.ui.components.distance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.coupleapp.ui.theme.*

/**
 * Settings dialog for distance/location feature
 */
@Composable
fun DistanceSettingsDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var locationSharingEnabled by remember { mutableStateOf(true) }
    var showBatteryLevel by remember { mutableStateOf(true) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var highAccuracyMode by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(PastelPink),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = SoftPink,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Text(
                            text = "Location Settings",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Settings items
                SettingsSwitchItem(
                    icon = Icons.Outlined.LocationOn,
                    title = "Share My Location",
                    subtitle = "Let your partner see your location",
                    checked = locationSharingEnabled,
                    onCheckedChange = { locationSharingEnabled = it },
                    accentColor = SoftPink
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsSwitchItem(
                    icon = Icons.Outlined.BatteryStd,
                    title = "Show Battery Level",
                    subtitle = "Display your battery percentage",
                    checked = showBatteryLevel,
                    onCheckedChange = { showBatteryLevel = it },
                    accentColor = Color(0xFF4CAF50)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsSwitchItem(
                    icon = Icons.Outlined.Notifications,
                    title = "Location Alerts",
                    subtitle = "Get notified when arriving/leaving places",
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it },
                    accentColor = Color(0xFFFFB74D)
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                SettingsSwitchItem(
                    icon = Icons.Outlined.GpsFixed,
                    title = "High Accuracy Mode",
                    subtitle = "Better precision, more battery usage",
                    checked = highAccuracyMode,
                    onCheckedChange = { highAccuracyMode = it },
                    accentColor = SoftBlue
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text(
                            text = "Cancel",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Button(
                        onClick = {
                            // TODO: Save settings
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SoftPink
                        )
                    ) {
                        Text(
                            text = "Save",
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = if (checked) accentColor.copy(alpha = 0.1f) else BackgroundCard
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (checked) accentColor.copy(alpha = 0.2f) else Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) accentColor else TextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            
            // Switch
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accentColor,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color(0xFFE0E0E0)
                )
            )
        }
    }
}

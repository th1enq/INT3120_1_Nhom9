package com.example.coupleapp.ui.components.sleep

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog to guide user to enable Health Connect permissions
 */
@Composable
fun HealthConnectPermissionDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = Color(0xFFFF9800)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Title
                Text(
                    text = "Health Connect Permissions",
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF2D2D2D),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Description
                Text(
                    text = "To sync sleep data, you need to:\n\n" +
                            "1. Install Health Connect app from Play Store\n" +
                            "2. Open Health Connect\n" +
                            "3. Go to App permissions\n" +
                            "4. Select this app\n" +
                            "5. Enable Sleep permissions\n\n" +
                            "Then come back and try syncing again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Start,
                    lineHeight = 20.sp
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Cancel button
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF757575)
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    // Open Health Connect button
                    Button(
                        onClick = {
                            // Try to open Health Connect app
                            try {
                                val intent = Intent().apply {
                                    action = "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"
                                    // Fallback to Play Store if Health Connect not installed
                                    try {
                                        context.startActivity(this)
                                    } catch (e: Exception) {
                                        // Open Play Store
                                        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
                                            data = Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                                        }
                                        context.startActivity(playStoreIntent)
                                    }
                                }
                            } catch (e: Exception) {
                                // If all fails, just dismiss
                                onDismiss()
                            }
                            onOpenSettings()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9ECE)
                        )
                    ) {
                        Text("Open Settings")
                    }
                }
            }
        }
    }
}

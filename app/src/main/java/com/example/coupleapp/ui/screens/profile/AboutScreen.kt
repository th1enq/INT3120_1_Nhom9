package com.example.coupleapp.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * About Screen - Shows app information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About App",
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
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600)) +
                            scaleIn(animationSpec = tween(600))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // App icon
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFFF6B9D),
                                            Color(0xFFFF8A80)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💕",
                                fontSize = 48.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // App name
                        Text(
                            text = "Couple App",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D3748)
                        )
                        
                        Text(
                            text = "Version 1.0.0",
                            fontSize = 14.sp,
                            color = Color(0xFF718096)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) +
                            slideInVertically(animationSpec = tween(600, delayMillis = 200)) { it / 4 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Description
                        Text(
                            text = "Stay connected with your loved one through shared moments, activities, and memories.",
                            fontSize = 15.sp,
                            color = Color(0xFF718096),
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Info cards
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AboutInfoRow(
                                    icon = Icons.Filled.Update,
                                    label = "Build",
                                    value = "2024.12.09"
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                AboutInfoRow(
                                    icon = Icons.Filled.Android,
                                    label = "Platform",
                                    value = "Android"
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                AboutInfoRow(
                                    icon = Icons.Filled.Code,
                                    label = "Framework",
                                    value = "Jetpack Compose"
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                AboutInfoRow(
                                    icon = Icons.Filled.Group,
                                    label = "Developer",
                                    value = "INT3120 - Team 9"
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Links
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            shadowElevation = 2.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                AboutLinkRow(
                                    icon = Icons.Filled.Policy,
                                    label = "Privacy Policy"
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                AboutLinkRow(
                                    icon = Icons.Filled.Description,
                                    label = "Terms of Service"
                                )
                                HorizontalDivider(color = Color(0xFFF0F0F0))
                                AboutLinkRow(
                                    icon = Icons.Filled.Copyright,
                                    label = "Licenses"
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600, delayMillis = 400))
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Made with ❤️ for couples",
                            fontSize = 13.sp,
                            color = Color(0xFFB0B0B0)
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Text(
                            text = "© 2024 Couple App. All rights reserved.",
                            fontSize = 11.sp,
                            color = Color(0xFFB0B0B0)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF718096),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF718096),
            modifier = Modifier.weight(1f)
        )
        
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D3748)
        )
    }
}

@Composable
private fun AboutLinkRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF718096),
            modifier = Modifier.size(20.dp)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color(0xFF2D3748),
            modifier = Modifier.weight(1f)
        )
        
        Icon(
            imageVector = Icons.Filled.OpenInNew,
            contentDescription = null,
            tint = Color(0xFFFF6B9D),
            modifier = Modifier.size(18.dp)
        )
    }
}

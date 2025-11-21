package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.ui.components.home.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToFeature: (String) -> Unit = {},
    onNavigateToWidget: (String) -> Unit = {}
) {
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Scaffold(
        bottomBar = {
            CoupleBottomNavigation(
                selectedItem = selectedBottomNavItem,
                onItemSelected = { selectedBottomNavItem = it }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
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
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Image Slider
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(800, delayMillis = 200)) + 
                            slideInVertically(animationSpec = tween(800, delayMillis = 200)) { it / 2 }
                ) {
                    AutoImageSlider(
                        slides = rememberSlides(),
                        modifier = Modifier,
                        onButtonClick = { index ->
                            // Handle button clicks for each slide
                            when (index) {
                                0 -> onNavigateToFeature("Pets")
                                1 -> onNavigateToFeature("Sleep")
                                2 -> onNavigateToFeature("Calendar")
                            }
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Features Row
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(800, delayMillis = 400)) + 
                            slideInVertically(animationSpec = tween(800, delayMillis = 400)) { it / 2 }
                ) {
                    FeaturesRow(
                        onFeatureClick = onNavigateToFeature
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Widgets Section Header
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(800, delayMillis = 600))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Widgets",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = Color(0xFF2D2D2D)
                        )
                        
                        Text(
                            text = "See all →",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFFF9ECE),
                            fontSize = 14.sp
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Widgets Grid
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(800, delayMillis = 800)) + 
                            slideInVertically(animationSpec = tween(800, delayMillis = 800)) { it / 2 }
                ) {
                    WidgetsGrid(
                        onWidgetClick = onNavigateToWidget
                    )
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

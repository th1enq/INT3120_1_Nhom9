package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.example.coupleapp.ui.components.LoadingScreen
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToFeature: (String) -> Unit = {},
    onNavigateToWidget: (String) -> Unit = {}
) {
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    var visible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    val scrollState = rememberLazyListState()

    // --- OPTIMIZE TIMING LOGIC ---
    LaunchedEffect(Unit) {
        delay(800)
        isLoading = false
        delay(400)
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

        Crossfade(
            targetState = isLoading,
            animationSpec = tween(durationMillis = 600),
            label = "LoadingCrossfade"
        ) { loading ->
            if (loading) {
                LoadingScreen()
            } else {
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
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                        item {
                            key("slider_section") {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(700)) +
                                            slideInVertically(animationSpec = tween(700)) { it / 8 }
                                ) {
                                    AutoImageSlider(
                                        slides = rememberSlides(),
                                        modifier = Modifier,
                                        onButtonClick = { index ->
                                            when (index) {
                                                0 -> onNavigateToFeature("Pets")
                                                1 -> onNavigateToFeature("Sleep")
                                                2 -> onNavigateToFeature("Calendar")
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                        item {
                            key("features_section") {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(700, delayMillis = 150)) +
                                            slideInVertically(animationSpec = tween(700, delayMillis = 150)) { it / 8 }
                                ) {
                                    FeaturesRow(onFeatureClick = onNavigateToFeature)
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                        item {
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(animationSpec = tween(700, delayMillis = 300))
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
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }

                        // Widgets Grid
                        item {
                            key("widgets_section") {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(700, delayMillis = 450)) +
                                            slideInVertically(animationSpec = tween(700, delayMillis = 450)) { it / 8 }
                                ) {
                                    WidgetsGrid(onWidgetClick = onNavigateToWidget)
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }
        }
    }
}
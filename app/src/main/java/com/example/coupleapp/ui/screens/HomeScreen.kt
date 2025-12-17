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
// Import ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.viewmodel.HomeViewModel
import com.example.coupleapp.ui.components.home.*
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToFeature: (String) -> Unit = {},
    onNavigateToWidget: (String) -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    // Inject ViewModel vào đây
    viewModel: HomeViewModel = viewModel()
) {
    // State cục bộ chỉ để quản lý navigation
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    val scrollState = rememberLazyListState()
    
    // Xử lý navigation khi chọn tab Friends hoặc Activities
    LaunchedEffect(selectedBottomNavItem) {
        when (selectedBottomNavItem) {
            BottomNavItem.FRIENDS -> {
                onNavigateToPartnerHub()
                selectedBottomNavItem = BottomNavItem.HOME
            }
            BottomNavItem.ACTIVITIES -> {
                onNavigateToMoments()
                selectedBottomNavItem = BottomNavItem.HOME
            }
            BottomNavItem.PROFILE -> {
                onNavigateToProfile()
                selectedBottomNavItem = BottomNavItem.HOME
            }
            else -> {}
        }
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
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 16.dp),
                // Tối ưu scrolling performance
                userScrollEnabled = true
            ) {
                item(key = "spacer_top") { 
                    Spacer(modifier = Modifier.height(16.dp)) 
                }

                // Slider Section (no animation)
                item(key = "slider_section") {
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

                        item(key = "spacer_1") { 
                            Spacer(modifier = Modifier.height(32.dp)) 
                        }

                        // Features Section (no animation)
                        item(key = "features_section") {
                            FeaturesRow(onFeatureClick = onNavigateToFeature)
                        }

                        item(key = "spacer_2") { 
                            Spacer(modifier = Modifier.height(32.dp)) 
                        }

                        // Widgets Header (no animation)
                        item(key = "widgets_header") {
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
                            }
                        }

                        item(key = "spacer_3") { 
                            Spacer(modifier = Modifier.height(16.dp)) 
                        }

                        // Widgets Grid (no animation)
                        item(key = "widgets_section") {
                            WidgetsGrid(onWidgetClick = onNavigateToWidget)
                        }

                        item(key = "spacer_bottom") { 
                            Spacer(modifier = Modifier.height(32.dp)) 
                        }
                    }
                }
    }
}
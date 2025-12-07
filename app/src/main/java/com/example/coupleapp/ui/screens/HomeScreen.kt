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
import com.example.coupleapp.ui.components.LoadingScreen
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onNavigateToFeature: (String) -> Unit = {},
    onNavigateToWidget: (String) -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    // Inject ViewModel vào đây
    viewModel: HomeViewModel = viewModel()
) {
    // 1. Lấy UI State từ ViewModel
    val uiState by viewModel.uiState.collectAsState()

    // State cục bộ chỉ để quản lý animation hiển thị (bay vào)
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    var visible by remember { mutableStateOf(false) }

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
            else -> {}
        }
    }

    // --- CẢI TIẾN LOGIC TIMING THÔNG MINH ---
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            // Nếu đang loading thì ẩn content
            visible = false
        } else {
            // Nếu loading xong (isLoading = false)
            // Kiểm tra xem đây là lần đầu hay là quay lại?

            if (!visible) {
                // Nếu content chưa hiện -> Đây là lần chuyển từ Loading sang Content
                // Delay nhẹ để Crossfade chạy được một chút rồi mới cho Content bay lên
                delay(400)
                visible = true
            }
            // Nếu visible đã là true (do quay lại từ màn hình khác mà VM vẫn giữ state),
            // thì không làm gì cả, content sẽ giữ nguyên -> Không bị chớp.
        }
    }

    // Xử lý trường hợp quay lại màn hình (Hot Reload):
    // Nếu vào màn hình mà VM báo đã load xong rồi, cho hiện content ngay
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
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

        // Dùng uiState.isLoading của ViewModel
        Crossfade(
            targetState = uiState.isLoading,
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

                        // Slider Section
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

                        // Features Section
                        item {
                            key("features_section") {
                                AnimatedVisibility(
                                    visible = visible,
                                    // Logic: Nếu vừa loading xong thì delay, còn nếu hiện sẵn thì hiện luôn
                                    enter = fadeIn(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 150 else 0)) +
                                            slideInVertically(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 150 else 0)) { it / 8 }
                                ) {
                                    FeaturesRow(onFeatureClick = onNavigateToFeature)
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(32.dp)) }

                        // Widgets Header
                        item {
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 300 else 0))
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
                                    enter = fadeIn(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 450 else 0)) +
                                            slideInVertically(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 450 else 0)) { it / 8 }
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
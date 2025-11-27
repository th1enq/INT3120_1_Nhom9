package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.components.missing.*
import com.example.coupleapp.viewmodel.MissingViewModel
import kotlinx.coroutines.delay

/**
 * Missing Screen - Redesigned with:
 * 1. Top bar with streak indicator (fire icon like TikTok)
 * 2. Lottie heart animation on background image
 * 3. "Missing" button to send love
 * 4. Today's miss counts for both users
 * 5. History section with timeline design (last 7 days)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissingScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MissingViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var visible by remember { mutableStateOf(false) }
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    
    val scrollState = rememberScrollState()
    
    // Snackbar for success feedback
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Animation timing
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(400)
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
    }
    
    // Show success snackbar
    LaunchedEffect(uiState.sendSuccess) {
        if (uiState.sendSuccess) {
            snackbarHostState.showSnackbar(
                message = "Love sent to ${uiState.partnerUser.name}! 💕",
                duration = SnackbarDuration.Short
            )
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            CoupleBottomNavigation(
                selectedItem = selectedBottomNavItem,
                onItemSelected = { item ->
                    selectedBottomNavItem = item
                    if (item == BottomNavItem.HOME) {
                        onBackClick()
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 300),
            label = "LoadingCrossfade",
            modifier = modifier.fillMaxSize()
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Top Bar with Streak Indicator
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(animationSpec = tween(300)) { -it }
                        ) {
                            MissingTopBar(
                                title = "Missing",
                                onBackClick = onBackClick,
                                streakCount = uiState.summary.currentStreak,
                                isStreakActive = uiState.summary.hasSentToday
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Heart Animation with Lottie on background image - full width
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                                    scaleIn(
                                        animationSpec = tween(400, delayMillis = 100),
                                        initialScale = 0.8f
                                    )
                        ) {
                            MissingHeartAnimation(
                                isAnimating = uiState.isHeartAnimating,
                                clickCount = uiState.clickCount,
                                onHeartClick = { viewModel.sendMissing() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Missing Button - always enabled for rapid clicking
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 150)) +
                                    scaleIn(
                                        animationSpec = tween(400, delayMillis = 150),
                                        initialScale = 0.9f
                                    )
                        ) {
                            MissingButton(
                                onClick = { viewModel.sendMissing() },
                                isAnimating = false // Always enabled
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Today's Miss Count Card
                        TodayMissCountCard(
                            myCount = uiState.myTodayCount,
                            partnerCount = uiState.partnerTodayCount,
                            visible = visible
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // History Section with Timeline Design
                        MissingHistorySection(
                            dailyHistory = uiState.dailyHistory,
                            currentUserId = uiState.currentUser.id,
                            visible = visible,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}

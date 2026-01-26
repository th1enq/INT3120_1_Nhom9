package com.example.coupleapp.ui.screens.moments

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.components.moments.*
import com.example.coupleapp.viewmodel.MomentsViewModel
import kotlinx.coroutines.delay

/**
 * Moments Screen - Display timeline of couple's activities
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentsScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToFriends: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: MomentsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.ACTIVITIES) }
    
    // Animation states
    var visible by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
    
    // Handle navigation
    LaunchedEffect(selectedBottomNavItem) {
        when (selectedBottomNavItem) {
            BottomNavItem.HOME -> {
                onNavigateToHome()
                selectedBottomNavItem = BottomNavItem.ACTIVITIES
            }
            BottomNavItem.FRIENDS -> {
                onNavigateToFriends()
                selectedBottomNavItem = BottomNavItem.ACTIVITIES
            }
            BottomNavItem.PROFILE -> {
                onNavigateToProfile()
                selectedBottomNavItem = BottomNavItem.ACTIVITIES
            }
            else -> {}
        }
    }
    
    // Handle loading animation
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(100)  // Minimal delay for smooth animation
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
    }
    
    Scaffold(
        topBar = {
            MomentsTopBar(
                onRefresh = { viewModel.refreshMoments() }
            )
        },
        bottomBar = {
            CoupleBottomNavigation(
                selectedItem = selectedBottomNavItem,
                onItemSelected = { selectedBottomNavItem = it }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 400),
            label = "LoadingCrossfade"
        ) { loading ->
            if (loading) {
                LoadingScreen(message = stringResource(R.string.loading_moments))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xFFF5FFF5),
                                    Color(0xFFFFFBF5),
                                    Color(0xFFFFFAF0)
                                )
                            )
                        )
                        .padding(paddingValues)
                ) {
                    if (uiState.momentsGroups.isEmpty()) {
                        EmptyMomentsState()
                    } else {
                        MomentsTimeline(
                            momentsGroups = uiState.momentsGroups,
                            visible = visible,
                            scrollState = scrollState
                        )
                    }
                }
            }
        }
    }
}

/**
 * Top bar for Moments screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MomentsTopBar(
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.moments),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = Color(0xFF718096)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White.copy(alpha = 0.9f)
        )
    )
}

/**
 * Timeline of moments
 */
@Composable
private fun MomentsTimeline(
    momentsGroups: List<MomentsGroup>,
    visible: Boolean,
    scrollState: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        state = scrollState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 16.dp)
    ) {
        momentsGroups.forEachIndexed { groupIndex, group ->
            // Timeline section with date indicator on left, cards on right
            item(key = "section_${group.section.date}") {
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 600,
                            delayMillis = groupIndex * 100
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 600,
                            delayMillis = groupIndex * 100
                        ),
                        initialOffsetY = { it / 3 }
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Timeline indicator on the left
                        TimelineIndicator(
                            section = group.section,
                            isLast = groupIndex == momentsGroups.size - 1
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Moments cards on the right
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(bottom = if (groupIndex == momentsGroups.size - 1) 0.dp else 24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            group.moments.forEach { moment ->
                                when (moment) {
                                    is SleepMoment -> SleepMomentCard(moment = moment)
                                    is MissingMoment -> MissingMomentCard(moment = moment)
                                    is LocketMoment -> LocketMomentCard(moment = moment)
                                    is EventMoment -> EventMomentCard(moment = moment)
                                    is AnniversaryMoment -> AnniversaryMomentCard(moment = moment)
                                    is GardenMoment -> GardenMomentCard(moment = moment)
                                    is MessageMoment -> MessageMomentCard(moment = moment)
                                    is CalendarMemoryMoment -> CalendarMemoryMomentCard(moment = moment)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Empty state when no moments available
 */
@Composable
private fun EmptyMomentsState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "📝",
                fontSize = 64.sp
            )
            
            Text(
                text = stringResource(R.string.no_moments),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
            
            Text(
                text = stringResource(R.string.moments_empty_subtitle),
                fontSize = 14.sp,
                color = Color(0xFF718096),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

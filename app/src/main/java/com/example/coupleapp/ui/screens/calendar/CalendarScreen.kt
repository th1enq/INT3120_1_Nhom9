package com.example.coupleapp.ui.screens.calendar

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.CalendarViewMode
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.calendar.*
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.viewmodel.CalendarViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    viewModel: CalendarViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }
    var visible by remember { mutableStateOf(false) }
    
    // Loading animation timing similar to other screens
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                kotlinx.coroutines.delay(300)
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
    }
    
    // Loading state
    Crossfade(
        targetState = uiState.isLoading,
        animationSpec = tween(durationMillis = 600),
        label = "LoadingCrossfade"
    ) { loading ->
        if (loading) {
            LoadingScreen()
        } else {
            CalendarMainContent(
                uiState = uiState,
                visible = visible,
                selectedBottomNavItem = selectedBottomNavItem,
                onBottomNavItemSelected = { item ->
                    selectedBottomNavItem = item
                    if (item == BottomNavItem.HOME) {
                        onBackClick()
                    }
                },
                onBackClick = onBackClick,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun CalendarMainContent(
    uiState: com.example.coupleapp.viewmodel.CalendarUiState,
    visible: Boolean,
    selectedBottomNavItem: BottomNavItem,
    onBottomNavItemSelected: (BottomNavItem) -> Unit,
    onBackClick: () -> Unit,
    viewModel: CalendarViewModel
) {
    Scaffold(
        bottomBar = {
            CoupleBottomNavigation(
                selectedItem = selectedBottomNavItem,
                onItemSelected = onBottomNavItemSelected
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Background Image (no overlay)
            BackgroundImage(
                imageUrl = uiState.coupleProfile?.backgroundImageUrl,
                useDefault = uiState.settings.useDefaultBackground
            )
            
            // Main Content with swipe gesture
            var offsetX by remember { mutableFloatStateOf(0f) }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (kotlin.math.abs(offsetX) > 100) {
                                    viewModel.toggleViewMode()
                                }
                                offsetX = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                offsetX += dragAmount
                            }
                        )
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {
                    // Top Bar - Simplified
                    CalendarTopBar(
                        onBackClick = onBackClick,
                        onCalendarClick = { viewModel.showAnniversaryManagement() },
                        onSettingsClick = { viewModel.showSettings() }
                    )
                    
                    // Main Content - Unified Layout
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(700)) +
                                slideInVertically(animationSpec = tween(700)) { it / 8 }
                    ) {
                        CalendarContent(
                            uiState = uiState,
                            onDateClick = { date -> viewModel.selectDate(date) },
                            onPreviousMonth = { viewModel.previousMonth() },
                            onNextMonth = { viewModel.nextMonth() }
                        )
                    }
                }
            }
            
            // Dialogs
            if (uiState.showAddEventDialog) {
                AddEditEventDialog(
                    anniversary = uiState.editingAnniversary,
                    selectedDate = uiState.selectedDate,
                    onDismiss = { viewModel.hideEventDialog() },
                    onSave = { anniversary -> viewModel.saveAnniversary(anniversary) },
                    onDelete = { id -> viewModel.deleteAnniversary(id) }
                )
            }
            
            if (uiState.showSettingsDialog) {
                CalendarSettingsDialog(
                    settings = uiState.settings,
                    coupleProfile = uiState.coupleProfile,
                    onDismiss = { viewModel.hideSettings() },
                    onUpdateNickname = { userId, nickname ->
                        viewModel.updateNickname(userId, nickname)
                    },
                    onUpdateBackground = { imageUrl ->
                        viewModel.updateBackgroundImage(imageUrl)
                    },
                    onToggleHeartbeat = { enabled ->
                        viewModel.toggleHeartbeatAnimation(enabled)
                    }
                )
            }
            
            if (uiState.showAnniversaryManagement) {
                AnniversaryManagementScreen(
                    anniversaries = uiState.allAnniversaries,
                    calendarEvents = uiState.calendarEvents,
                    selectedYearMonth = uiState.selectedYearMonth,
                    onDismiss = { viewModel.hideAnniversaryManagement() },
                    onDateClick = { date -> viewModel.selectDate(date) },
                    onAddEvent = { date -> viewModel.showAddEventDialog(date) },
                    onEditEvent = { anniversary -> viewModel.showEditEventDialog(anniversary) },
                    onDeleteEvent = { id -> viewModel.deleteAnniversary(id) },
                    onPreviousMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() }
                )
            }
        }
    }
}

@Composable
private fun CalendarContent(
    uiState: com.example.coupleapp.viewmodel.CalendarUiState,
    onDateClick: (java.time.LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        
        // Love Days Display - Changes between circle and square
        uiState.loveDaysCounter?.let { counter ->
            AnimatedContent(
                targetState = uiState.viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                },
                label = "counter_transition"
            ) { viewMode ->
                when (viewMode) {
                    CalendarViewMode.CIRCLE_COUNTER -> {
                        CircleLoveDaysDisplay(totalDays = counter.totalDays)
                    }
                    CalendarViewMode.GRID_CALENDAR -> {
                        SquareLoveDaysDisplay(
                            years = counter.years,
                            months = counter.months,
                            days = counter.days
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Couple Profile Section (Always visible)
        uiState.coupleProfile?.let { profile ->
            CoupleProfileSection(
                user1 = profile.user1,
                user2 = profile.user2,
                showHeartbeat = uiState.settings.showHeartbeatAnimation
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Upcoming Events Timeline (Always visible)
        if (uiState.upcomingEvents.isNotEmpty()) {
            UpcomingEventsTimeline(
                events = uiState.upcomingEvents
            )
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}


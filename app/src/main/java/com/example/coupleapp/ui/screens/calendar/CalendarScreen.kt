package com.example.coupleapp.ui.screens.calendar

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.CalendarViewMode
import com.example.coupleapp.data.repository.FirebaseStorageRepository
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.calendar.*
import com.example.coupleapp.viewmodel.CalendarViewModelFirebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: CalendarViewModelFirebase = viewModel(),
    questViewModel: com.example.coupleapp.viewmodel.QuestViewModelFirebase? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }
    
    // Loading animation timing similar to other screens
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                kotlinx.coroutines.delay(100)  // Minimal delay for smooth animation
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
        animationSpec = tween(durationMillis = 400),
        label = "LoadingCrossfade"
    ) { loading ->
        if (loading) {
            LoadingScreen(message = stringResource(R.string.loading_calendar))
        } else {
            CalendarMainContent(
                uiState = uiState,
                visible = visible,
                onBackClick = onBackClick,
                viewModel = viewModel,
                questViewModel = questViewModel
            )
        }
    }
}

@Composable
private fun CalendarMainContent(
    uiState: com.example.coupleapp.data.model.CalendarUiState,
    visible: Boolean,
    onBackClick: () -> Unit,
    viewModel: CalendarViewModelFirebase,
    questViewModel: com.example.coupleapp.viewmodel.QuestViewModelFirebase? = null
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRepository = remember { FirebaseStorageRepository() }
    var isUploadingBackground by remember { mutableStateOf(false) }
    
    // Image picker launcher for background
    val backgroundImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            scope.launch {
                isUploadingBackground = true
                try {
                    val result = storageRepository.uploadImageWithContext(
                        context = context,
                        uri = selectedUri,
                        path = "calendar_backgrounds",
                        filename = "calendar_bg_${System.currentTimeMillis()}.jpg"
                    )
                    result.onSuccess { imageUrl ->
                        viewModel.updateBackgroundImage(imageUrl)
                        snackbarHostState.showSnackbar("Đã cập nhật hình nền")
                    }
                    result.onFailure { error ->
                        snackbarHostState.showSnackbar("Lỗi: ${error.message}")
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Lỗi: ${e.message}")
                }
                isUploadingBackground = false
            }
        }
    }
    
    // Show error/success message
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                val isNewEvent = uiState.editingAnniversary == null
                AddEditEventDialog(
                    anniversary = uiState.editingAnniversary,
                    selectedDate = uiState.selectedDate,
                    onDismiss = { viewModel.hideEventDialog() },
                    onSave = { anniversary -> 
                        viewModel.saveAnniversary(anniversary)
                        // Update quest progress when adding new event (not editing)
                        if (isNewEvent) {
                            questViewModel?.updateQuestProgress(com.example.coupleapp.data.model.QuestType.ADD_CALENDAR_EVENT, 1)
                        }
                    },
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
                    },
                    onUpdateAnniversaryDate = { date ->
                        viewModel.updateRelationshipStartDate(date)
                    },
                    onSelectBackgroundFromGallery = {
                        backgroundImagePickerLauncher.launch("image/*")
                    },
                    onUpdateReminderHours = { hours ->
                        viewModel.updateReminderHours(hours)
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
    uiState: com.example.coupleapp.data.model.CalendarUiState,
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


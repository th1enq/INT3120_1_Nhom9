package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.components.home.BottomNavItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import com.example.coupleapp.ui.components.sleep.*
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.viewmodel.SleepTrackerViewModel
import com.example.coupleapp.viewmodel.TimeEditorType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTrackerScreen(
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier,
    onNavigateToHistory: (String) -> Unit = {},
    viewModel: SleepTrackerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var visible by remember { mutableStateOf(false) }
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.HOME) }

    val scrollState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

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

    Scaffold(
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
            animationSpec = tween(durationMillis = 600),
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
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {

                        // Top Bar
                        item {
                            AnimatedVisibility(
                                visible = visible,
                                enter = fadeIn(animationSpec = tween(600)) +
                                        slideInVertically(animationSpec = tween(600)) { -it }
                            ) {
                                TopBar(
                                    userName = viewModel.getActiveUser().name,
                                    onBackClick = onBackClick,
                                    onMenuClick = {
                                        scope.launch { viewModel.showBottomSheet(true) }
                                    }
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(24.dp)) }

                        // Sleep Quality Circle
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                AnimatedVisibility(
                                    visible = visible,
                                    // Delay nhẹ nếu là lần đầu load
                                    enter = fadeIn(animationSpec = tween(600, delayMillis = if(uiState.isLoading) 100 else 0)) +
                                            scaleIn(
                                                animationSpec = tween(600, delayMillis = if(uiState.isLoading) 100 else 0),
                                                initialScale = 0.8f
                                            )
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        uiState.sleepRecord?.let { record ->
                                            key(record.quality, record.achievementPercentage) {
                                                SleepQualityCircle(
                                                    quality = record.quality,
                                                    achievementPercentage = record.achievementPercentage
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(24.dp))

                                            SleepQualityStatus(
                                                qualityText = record.qualityText,
                                                achievementPercentage = record.achievementPercentage,
                                                quality = record.quality
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }

                        // Sleep Times
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(600, delayMillis = if(uiState.isLoading) 250 else 0)) +
                                            slideInVertically(animationSpec = tween(600, delayMillis = if(uiState.isLoading) 250 else 0)) { it / 4 }
                                ) {
                                    Column {
                                        uiState.sleepRecord?.let { record ->
                                            SleepTimesRow(
                                                bedTime = record.bedTime,
                                                wakeUpTime = record.wakeUpTime,
                                                duration = record.actualSleepDuration,
                                                onEditBedTime = { viewModel.showTimeEditor(true, TimeEditorType.BED_TIME) },
                                                onEditWakeUpTime = { viewModel.showTimeEditor(true, TimeEditorType.WAKE_UP_TIME) },
                                                onEditDuration = { viewModel.showTimeEditor(true, TimeEditorType.SLEEP_GOAL) }
                                            )

                                            Spacer(modifier = Modifier.height(16.dp))

                                            SleepStagesCard(
                                                awakeMinutes = record.sleepStages.awakeDurationMinutes,
                                                sleepMinutes = record.sleepStages.sleepDurationMinutes
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(modifier = Modifier.height(40.dp)) }

                        // Recent Sleep Header
                        item {
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(600, delayMillis = if(uiState.isLoading) 400 else 0))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Recent Sleep",
                                            style = MaterialTheme.typography.titleLarge.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp
                                            ),
                                            color = Color(0xFF2D2D2D)
                                        )

                                        Text(
                                            text = "View all →",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Medium
                                            ),
                                            color = Color(0xFFFF9ECE),
                                            fontSize = 14.sp,
                                            modifier = Modifier.clickable { onNavigateToHistory(viewModel.getActiveUser().id) }
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }

                        // Sleep History Items
                        items(uiState.sleepHistory) { record ->
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                AnimatedVisibility(
                                    visible = visible,
                                    // Delay nối tiếp nhau nếu muốn, hoặc hiện cùng lúc
                                    enter = fadeIn(animationSpec = tween(500, delayMillis = if(uiState.isLoading) 500 else 0))
                                ) {
                                    SleepHistoryItem(record = record)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }

                    // User Toggle Button
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = if(uiState.isLoading) 600 else 0)) +
                                slideInVertically(
                                    animationSpec = tween(600, delayMillis = if(uiState.isLoading) 600 else 0),
                                    initialOffsetY = { it }
                                ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = paddingValues.calculateBottomPadding() + 16.dp)
                    ) {
                        UserToggleButton(
                            currentUserName = uiState.currentUser.name,
                            partnerUserName = uiState.partnerUser.name,
                            isCurrentUser = uiState.isCurrentUser,
                            onToggle = { viewModel.toggleUser() }
                        )
                    }
                }
            }
        }

        // --- Bottom Sheet & Dialogs giữ nguyên ---
        if (uiState.showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.showBottomSheet(false) },
                sheetState = sheetState,
                containerColor = Color.Transparent,
                dragHandle = null
            ) {
                SleepSettingsBottomSheet(
                    onUtilitiesClick = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showBottomSheet(false)
                        }
                    },
                    onWhenToSleepClick = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showBottomSheet(false)
                            viewModel.showTimeEditor(true, TimeEditorType.BED_TIME)
                        }
                    },
                    onSleepGoalClick = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showBottomSheet(false)
                            viewModel.showTimeEditor(true, TimeEditorType.SLEEP_GOAL)
                        }
                    },
                    onMyHistoryClick = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showBottomSheet(false)
                            onNavigateToHistory(viewModel.getActiveUser().id)
                        }
                    },
                    onDismiss = {
                        scope.launch {
                            sheetState.hide()
                            viewModel.showBottomSheet(false)
                        }
                    }
                )
            }
        }

        if (uiState.showTimeEditor) {
            when (uiState.timeEditorType) {
                TimeEditorType.BED_TIME -> {
                    TimeEditorDialog(
                        title = "Bed Time",
                        initialTime = uiState.sleepRecord?.bedTime ?: LocalTime.of(22, 0),
                        onDismiss = { viewModel.showTimeEditor(false, TimeEditorType.NONE) },
                        onConfirm = { newTime ->
                            viewModel.updateBedTime(newTime)
                            viewModel.showTimeEditor(false, TimeEditorType.NONE)
                        }
                    )
                }
                TimeEditorType.WAKE_UP_TIME -> {
                    TimeEditorDialog(
                        title = "Wake Up Time",
                        initialTime = uiState.sleepRecord?.wakeUpTime ?: LocalTime.of(7, 0),
                        onDismiss = { viewModel.showTimeEditor(false, TimeEditorType.NONE) },
                        onConfirm = { newTime ->
                            viewModel.showTimeEditor(false, TimeEditorType.NONE)
                        }
                    )
                }
                TimeEditorType.SLEEP_GOAL -> {
                    DurationEditorDialog(
                        title = "Sleep Goal",
                        initialDurationMinutes = uiState.settings.targetSleepDuration,
                        onDismiss = { viewModel.showTimeEditor(false, TimeEditorType.NONE) },
                        onConfirm = { newDuration ->
                            viewModel.updateSleepGoal(newDuration)
                            viewModel.showTimeEditor(false, TimeEditorType.NONE)
                        }
                    )
                }
                TimeEditorType.NONE -> { }
            }
        }

        if (uiState.showBedtimeReminder) {
            BedtimeReminderDialog(
                bedTime = uiState.settings.idealBedTime,
                onDismiss = { viewModel.dismissBedtimeReminder() },
                onGoToSleep = { viewModel.dismissBedtimeReminder() }
            )
        }
    }
}
@Composable
private fun TopBar(
    userName: String,
    onBackClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(22.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9ECE).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = userName,
                    modifier = Modifier.size(20.dp),
                    tint = Color(0xFFFF9ECE)
                )
            }

            Text(
                text = userName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                ),
                color = Color(0xFF2D2D2D)
            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = Color(0xFF2D2D2D),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
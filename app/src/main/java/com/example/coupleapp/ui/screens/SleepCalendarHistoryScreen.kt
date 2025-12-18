package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.FirebaseSleepRecord
import com.example.coupleapp.ui.components.LoadingScreen
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.viewmodel.SleepCalendarViewModel
import com.example.coupleapp.viewmodel.SleepCalendarViewModelFactory


@Composable
fun SleepCalendarHistoryScreen(
    userId: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SleepCalendarViewModel = viewModel(
        factory = SleepCalendarViewModelFactory(userId, context)
    )
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }
    val scrollState = rememberLazyListState()
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

    Crossfade(
        targetState = uiState.isLoading,
        animationSpec = tween(durationMillis = 400),
        label = "LoadingCrossfade",
        modifier = modifier
    ) { loading ->
        if (loading) {
            LoadingScreen(message = "Loading history...")
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
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {

                    // 1. Top Bar
                    item(key = "TopBar") {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(600)) +
                                    slideInVertically(animationSpec = tween(600)) { -it }
                        ) {
                            Row(
                                modifier = Modifier
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
                                            // Dùng data từ ViewModel
                                            contentDescription = uiState.userName,
                                            modifier = Modifier.size(20.dp),
                                            tint = Color(0xFFFF9ECE)
                                        )
                                    }

                                    Column {
                                        Text(
                                            // Dùng data từ ViewModel
                                            text = uiState.userName,
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            ),
                                            color = Color(0xFF2D2D2D)
                                        )

                                        Text(
                                            text = "Sleep History",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color(0xFF757575),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "Spacer16") { Spacer(modifier = Modifier.height(16.dp)) }

                    // 2. Calendar Content
                    item(key = "CalendarContent") {
                        Column(modifier = Modifier.padding(horizontal = 24.dp)) {

                            // Title Month
                            AnimatedVisibility(
                                visible = visible,
                                // Hiện tiêu đề nhanh hơn một chút
                                enter = fadeIn(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 100 else 0)) +
                                        slideInVertically(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 100 else 0)) { it / 8 }
                            ) {
                                Text(
                                    // Dùng data từ ViewModel
                                    text = uiState.currentMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 24.sp
                                    ),
                                    color = Color(0xFF2D2D2D),
                                    modifier = Modifier.padding(bottom = 16.dp)
                                )
                            }

                            key(uiState.currentMonth, uiState.selectedDate) {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 250 else 0)) +
                                            slideInVertically(animationSpec = tween(700, delayMillis = if(uiState.isLoading) 250 else 0)) { it / 8 }
                                ) {
                                    CalendarGrid(
                                        currentMonth = uiState.currentMonth,
                                        sleepRecords = uiState.sleepHistory,
                                        selectedDate = uiState.selectedDate,
                                        onDateSelected = { date ->
                                            viewModel.selectDate(date)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    currentMonth: YearMonth,
    sleepRecords: List<FirebaseSleepRecord>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstDayOfMonth = currentMonth.atDay(1)
    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value % 7
    val daysInMonth = currentMonth.lengthOfMonth()

    // Map FirebaseSleepRecord by date
    val sleepDataMap = sleepRecords.associateBy { record ->
        record.date?.let { timestamp ->
            timestamp.toDate().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }.filterKeys { it != null }.mapKeys { it.key!! }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7").forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Date grid
        var dayCounter = 1
        for (week in 0..5) {
            if (dayCounter > daysInMonth) break

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (dayOfWeek in 0..6) {
                    val shouldShowDay = if (week == 0) {
                        dayOfWeek >= firstDayOfWeek && dayCounter <= daysInMonth
                    } else {
                        dayCounter <= daysInMonth
                    }

                    if (shouldShowDay) {
                        val currentDate = currentMonth.atDay(dayCounter)
                        val sleepRecord = sleepDataMap[currentDate]
                        val isSelected = currentDate == selectedDate

                        val currentDayForClick = currentDate

                        key(currentDate) {
                            CalendarDayCell(
                                day = dayCounter,
                                sleepRecord = sleepRecord,
                                isSelected = isSelected,
                                onClick = {
                                    if (sleepRecord != null) onDateSelected(currentDayForClick)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        dayCounter++
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    day: Int,
    sleepRecord: FirebaseSleepRecord?,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Convert Firebase quality string to color and image
    val qualityColor = sleepRecord?.quality?.let { quality ->
        when (quality.uppercase()) {
            "EXCELLENT" -> Color(0xFF4CAF50)
            "GOOD" -> Color(0xFFFF9800)
            "POOR" -> Color(0xFFF44336)
            else -> Color(0xFF9E9E9E)
        }
    }

    val imageRes = sleepRecord?.quality?.let { quality ->
        when (quality.uppercase()) {
            "EXCELLENT" -> R.drawable.excellent
            "GOOD" -> R.drawable.good
            "POOR" -> R.drawable.bad
            else -> null
        }
    }

    Column(
        modifier = modifier
            .padding(4.dp)
            .clickable(enabled = sleepRecord != null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = day.toString(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (sleepRecord != null) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (sleepRecord != null) Color(0xFF2D2D2D) else Color(0xFFCCCCCC),
            fontSize = 12.sp
        )

        if (sleepRecord != null && imageRes != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) Color(0xFFFF9ECE).copy(alpha = 0.2f) else Color(0xFFF5F5F5)
                    )
                    .then(if(isSelected) Modifier.border(2.dp, Color(0xFFFF9ECE), CircleShape) else Modifier),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = imageRes),
                    contentDescription = "Sleep quality",
                    modifier = Modifier
                        .size(35.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF5F5F5).copy(alpha = 0.3f))
                    .border(1.dp, Color(0xFFE0E0E0), CircleShape)
            )
        }

        if (sleepRecord != null && qualityColor != null) {
            // Use sleepDurationMinutes from Firebase record
            val totalMinutes = sleepRecord.sleepDurationMinutes
            val hours = totalMinutes / 60
            val minutes = totalMinutes % 60
            val hoursText = if (minutes >= 30) "${hours + 1}h" else "${hours}h"

            Text(
                text = hoursText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(qualityColor)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

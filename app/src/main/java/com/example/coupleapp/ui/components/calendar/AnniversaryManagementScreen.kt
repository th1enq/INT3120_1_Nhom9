package com.example.coupleapp.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import com.example.coupleapp.data.model.Anniversary
import com.example.coupleapp.ui.screens.calendar.getEventColor
import com.example.coupleapp.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun AnniversaryManagementScreen(
    anniversaries: List<Anniversary>,
    calendarEvents: Map<LocalDate, List<Anniversary>>,
    selectedYearMonth: YearMonth,
    onDismiss: () -> Unit,
    onDateClick: (LocalDate) -> Unit,
    onAddEvent: (LocalDate?) -> Unit,
    onEditEvent: (Anniversary) -> Unit,
    onDeleteEvent: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    val eventsOnSelectedDate = selectedDate?.let { calendarEvents[it] } ?: emptyList()
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(20.dp))
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.88f)
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = BackgroundWhite
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(PastelPink, SoftPink)
                                )
                            )
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "📅",
                                fontSize = 28.sp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.manage_anniversaries),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${anniversaries.size} sự kiện",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.3f), CircleShape)
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    
                    // Calendar with events
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Calendar
                        CalendarWithEvents(
                            yearMonth = selectedYearMonth,
                            events = calendarEvents,
                            selectedDate = selectedDate,
                            onDateClick = { date ->
                                selectedDate = date
                                onDateClick(date)
                            },
                            onPreviousMonth = onPreviousMonth,
                            onNextMonth = onNextMonth
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Event info or add button
                        if (eventsOnSelectedDate.isNotEmpty()) {
                            // Show events on selected date
                            val formattedDate = selectedDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) ?: ""
                            Text(
                                text = stringResource(R.string.event_on_date, formattedDate),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = eventsOnSelectedDate,
                                    key = { it.id }
                                ) { anniversary ->
                                    EventListItem(
                                        anniversary = anniversary,
                                        onEdit = { onEditEvent(anniversary) },
                                        onDelete = { onDeleteEvent(anniversary.id) }
                                    )
                                }
                            }
                        } else if (selectedDate != null) {
                            // Show add event button for selected date
                            Button(
                                onClick = { onAddEvent(selectedDate) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AccentPink
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(text = "➕", fontSize = 20.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Thêm sự kiện vào ngày ${selectedDate?.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else {
                            // Show all events
                            Text(
                                text = "Tất cả kỷ niệm",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = anniversaries.sortedBy { it.date },
                                    key = { it.id }
                                ) { anniversary ->
                                    EventListItem(
                                        anniversary = anniversary,
                                        onEdit = { onEditEvent(anniversary) },
                                        onDelete = { onDeleteEvent(anniversary.id) }
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
private fun CalendarWithEvents(
    yearMonth: YearMonth,
    events: Map<LocalDate, List<Anniversary>>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = PastelPink.copy(alpha = 0.1f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Month header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onPreviousMonth),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◀",
                        fontSize = 18.sp,
                        color = AccentPink
                    )
                }
                
                Text(
                    text = stringResource(R.string.month_year, yearMonth.monthValue, yearMonth.year),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clickable(onClick = onNextMonth),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▶",
                        fontSize = 18.sp,
                        color = AccentPink
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Weekday headers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("CN", "T2", "T3", "T4", "T5", "T6", "T7").forEach { day ->
                    Text(
                        text = day,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (day == "CN") AccentPink else TextSecondary,
                        modifier = Modifier.width(40.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Calendar grid
            val firstDay = yearMonth.atDay(1)
            val firstDayOfWeek = firstDay.dayOfWeek.value % 7
            val daysInMonth = yearMonth.lengthOfMonth()
            
            var dayCounter = 1
            var weekCounter = 0
            
            while (dayCounter <= daysInMonth) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    for (i in 0..6) {
                        if (weekCounter == 0 && i < firstDayOfWeek) {
                            Spacer(modifier = Modifier.width(40.dp))
                        } else if (dayCounter <= daysInMonth) {
                            val currentDate = yearMonth.atDay(dayCounter)
                            val hasEvent = events.containsKey(currentDate)
                            val isSelected = currentDate == selectedDate
                            val isToday = currentDate == LocalDate.now()
                            val eventCount = events[currentDate]?.size ?: 0
                            
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable { onDateClick(currentDate) }
                                    .background(
                                        when {
                                            isSelected -> AccentPink
                                            isToday -> SoftPink.copy(alpha = 0.5f)
                                            hasEvent -> PastelPink.copy(alpha = 0.3f)
                                            else -> Color.Transparent
                                        },
                                        RoundedCornerShape(10.dp)
                                    )
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = dayCounter.toString(),
                                        fontSize = 14.sp,
                                        fontWeight = if (hasEvent || isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = when {
                                            isSelected -> Color.White
                                            isToday -> AccentPink
                                            else -> TextPrimary
                                        }
                                    )
                                    
                                    if (hasEvent && !isSelected) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            repeat(minOf(eventCount, 3)) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(AccentPink, CircleShape)
                                                )
                                                if (it < minOf(eventCount, 3) - 1) {
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            dayCounter++
                        } else {
                            Spacer(modifier = Modifier.width(40.dp))
                        }
                    }
                }
                weekCounter++
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun EventListItem(
    anniversary: Anniversary,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        getEventColor(anniversary.type).copy(alpha = 0.2f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = anniversary.type.emoji,
                    fontSize = 24.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anniversary.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = anniversary.date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    if (anniversary.isRecurring) {
                        Chip(text = "Lặp lại", color = AccentBlue)
                    }
                    if (anniversary.reminderEnabled) {
                        Chip(text = stringResource(R.string.reminder_chip), color = AccentGreen)
                    }
                }
            }
            
            // Delete button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(ErrorColor.copy(alpha = 0.1f), CircleShape)
                    .clickable { showDeleteConfirm = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🗑️",
                    fontSize = 18.sp
                )
            }
        }
    }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete_anniversary)) },
            text = { Text(stringResource(R.string.delete_anniversary_confirm, anniversary.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text(stringResource(R.string.delete), color = ErrorColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.cancel), color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

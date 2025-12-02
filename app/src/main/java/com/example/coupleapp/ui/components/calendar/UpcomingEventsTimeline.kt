package com.example.coupleapp.ui.components.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.AnniversaryType
import com.example.coupleapp.data.model.CalendarEvent
import com.example.coupleapp.ui.screens.calendar.getEventColor
import com.example.coupleapp.ui.theme.*
import java.time.format.DateTimeFormatter

@Composable
fun UpcomingEventsTimeline(
    events: List<CalendarEvent>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        // Header
        Text(
            text = "Kỷ niệm sắp tới",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .background(
                    Color(0xFFFF6B9D).copy(alpha = 0.9f),
                    RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Timeline Items
        events.forEachIndexed { index, event ->
            TimelineItem(
                event = event,
                isLast = index == events.lastIndex
            )
            
            if (index != events.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun TimelineItem(
    event: CalendarEvent,
    isLast: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Timeline indicator column
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(40.dp)
        ) {
            // Circle indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                getEventColor(event.type),
                                getEventColor(event.type).copy(alpha = 0.7f)
                            )
                        ),
                        shape = CircleShape
                    )
                    .padding(2.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = event.type.emoji,
                    fontSize = 16.sp
                )
            }
            
            // Connecting line
            if (!isLast) {
                Canvas(
                    modifier = Modifier
                        .width(3.dp)
                        .height(80.dp)
                ) {
                    drawLine(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                getEventColor(event.type).copy(alpha = 0.5f),
                                getEventColor(event.type).copy(alpha = 0.2f)
                            )
                        ),
                        start = Offset(size.width / 2, 0f),
                        end = Offset(size.width / 2, size.height),
                        strokeWidth = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 5f))
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Event card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isLast) 80.dp else 88.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.95f)
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = event.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = formatEventDate(event),
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = event.type.displayName,
                        fontSize = 11.sp,
                        color = getEventColor(event.type),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Days until badge
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    getEventColor(event.type).copy(alpha = 0.2f),
                                    getEventColor(event.type).copy(alpha = 0.1f)
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = event.daysUntil.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = getEventColor(event.type)
                    )
                    Text(
                        text = if (event.daysUntil == 0L) "Hôm nay" else "ngày",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }
    }
}

private fun formatEventDate(event: CalendarEvent): String {
    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    return event.date.format(formatter)
}

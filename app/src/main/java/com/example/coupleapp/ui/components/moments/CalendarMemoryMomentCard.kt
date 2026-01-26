package com.example.coupleapp.ui.components.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.CalendarMemoryMoment
import com.example.coupleapp.data.model.MomentEventType
import java.time.format.DateTimeFormatter

/**
 * Calendar memory moment card component
 * Displays past calendar events as memories
 */
@Composable
fun CalendarMemoryMomentCard(
    moment: CalendarMemoryMoment,
    modifier: Modifier = Modifier
) {
    // Color scheme based on event type (similar to EventMomentCard but with softer/nostalgic tones)
    val (bgColor, iconColor, emoji) = when (moment.eventType) {
        MomentEventType.BIRTHDAY -> Triple(
            Color(0xFFFFF8E1).copy(alpha = 0.7f),
            Color(0xFFFFB74D),
            "🎂"
        )
        MomentEventType.ANNIVERSARY -> Triple(
            Color(0xFFFCE4EC).copy(alpha = 0.7f),
            Color(0xFFF48FB1),
            "💝"
        )
        MomentEventType.SPECIAL_DAY -> Triple(
            Color(0xFFE8EAF6).copy(alpha = 0.7f),
            Color(0xFF7986CB),
            "✨"
        )
        MomentEventType.REMINDER -> Triple(
            Color(0xFFE0F7FA).copy(alpha = 0.7f),
            Color(0xFF4DD0E1),
            "📝"
        )
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Memory icon with nostalgic styling
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = emoji,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "💭",
                        fontSize = 12.sp
                    )
                }
            }

            // Right side - Memory information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Memory label
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📅",
                        fontSize = 10.sp
                    )
                    Text(
                        text = "Memory",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF718096)
                    )
                }
                
                // Title
                Text(
                    text = moment.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor.copy(alpha = 0.9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Description (if available)
                moment.description?.let { desc ->
                    if (desc.isNotBlank()) {
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF4A5568),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Days ago indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "⏰", fontSize = 12.sp)
                    Text(
                        text = moment.getDaysAgoFormatted(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2D2D2D)
                    )
                }
                
                // Event date
                Text(
                    text = moment.eventDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    fontSize = 10.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

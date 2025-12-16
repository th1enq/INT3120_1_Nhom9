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
import com.example.coupleapp.data.model.EventMoment
import com.example.coupleapp.data.model.MomentEventType

/**
 * Event moment card component
 */
@Composable
fun EventMomentCard(
    moment: EventMoment,
    modifier: Modifier = Modifier
) {
    // Color scheme based on event type
    val (bgColor, iconColor, emoji) = when (moment.eventType) {
        MomentEventType.BIRTHDAY -> Triple(
            Color(0xFFFFF3E0).copy(alpha = 0.6f),
            Color(0xFFFFA726),
            "🎂"
        )
        MomentEventType.ANNIVERSARY -> Triple(
            Color(0xFFFCE4EC).copy(alpha = 0.6f),
            Color(0xFFEC407A),
            "💕"
        )
        MomentEventType.SPECIAL_DAY -> Triple(
            Color(0xFFE8EAF6).copy(alpha = 0.6f),
            Color(0xFF5C6BC0),
            "⭐"
        )
        MomentEventType.REMINDER -> Triple(
            Color(0xFFE0F2F1).copy(alpha = 0.6f),
            Color(0xFF26A69A),
            "🔔"
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
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 32.sp
                )
            }

            // Right side - Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Type
                Text(
                    text = "Event",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                
                // Title
                Text(
                    text = moment.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = iconColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Days until
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "📅", fontSize = 12.sp)
                    Text(
                        text = moment.getDaysUntilFormatted(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2D2D2D)
                    )
                }
                
                // Time
                Text(
                    text = moment.getFormattedTime(),
                    fontSize = 10.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

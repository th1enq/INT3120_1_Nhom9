package com.example.coupleapp.ui.components.moments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocketMoment
import com.example.coupleapp.data.model.LocketType

/**
 * Locket moment card component
 */
@Composable
fun LocketMomentCard(
    moment: LocketMoment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFE8F5E9).copy(alpha = 0.6f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Locket content preview
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFFFFFFFF),
                                Color(0xFFF1F8E9)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                when (moment.locketType) {
                    LocketType.EMOJI -> {
                        Text(
                            text = moment.content,
                            fontSize = 48.sp
                        )
                    }
                    LocketType.TEXT -> {
                        Text(
                            text = "💬",
                            fontSize = 48.sp
                        )
                    }
                    LocketType.PHOTO -> {
                        Icon(
                            imageVector = Icons.Filled.Image,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    LocketType.DRAWING -> {
                        Icon(
                            imageVector = Icons.Filled.Brush,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Right side - Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Type
                Text(
                    text = "Locket",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                
                // Sender
                Text(
                    text = moment.senderName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF66BB6A)
                )
                
                // Content
                if (moment.locketType == LocketType.TEXT || moment.locketType == LocketType.EMOJI) {
                    Text(
                        text = moment.content,
                        fontSize = 14.sp,
                        color = Color(0xFF2D2D2D),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Caption
                moment.caption?.let { caption ->
                    Text(
                        text = caption,
                        fontSize = 12.sp,
                        color = Color(0xFF718096),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                // Time
                Text(
                    text = moment.getFormattedTime(),
                    fontSize = 12.sp,
                    color = Color(0xFF718096)
                )
            }
        }
    }
}

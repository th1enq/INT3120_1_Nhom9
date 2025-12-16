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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.MissingMoment

/**
 * Missing moment card component
 */
@Composable
fun MissingMomentCard(
    moment: MissingMoment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFCE4EC).copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Hearts with avatars
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp)
            ) {
                // Sender avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = moment.senderAvatar ?: "💕",
                        fontSize = 22.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Hearts
                Text(
                    text = "❤️".repeat(minOf(moment.missCount, 3)),
                    fontSize = 10.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Receiver avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = moment.receiverAvatar ?: "💕",
                        fontSize = 22.sp
                    )
                }
            }

            // Right side - Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Type
                Text(
                    text = "Missing",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                
                // Title
                Text(
                    text = "${moment.senderName} → ${moment.receiverName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B9D)
                )
                
                // Count
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💌", fontSize = 12.sp)
                    Text(
                        text = "${moment.missCount} messages",
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

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
import com.example.coupleapp.data.model.AnniversaryMoment

/**
 * Anniversary moment card component
 */
@Composable
fun AnniversaryMomentCard(
    moment: AnniversaryMoment,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFFF0F5).copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Couple avatars stacked
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(64.dp)
            ) {
                // User 1
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = moment.user1Avatar ?: "❤️",
                        fontSize = 22.sp
                    )
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // Heart
                Text(text = "💕", fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(2.dp))
                
                // User 2
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = moment.user2Avatar ?: "❤️",
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
                    text = "Anniversary",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                
                // Milestone
                Text(
                    text = moment.getMilestoneText(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFF6B9D)
                )
                
                // Together text
                Text(
                    text = "together",
                    fontSize = 11.sp,
                    color = Color(0xFF718096)
                )
                
                // Names
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "👫", fontSize = 11.sp)
                    Text(
                        text = "${moment.user1Name} & ${moment.user2Name}",
                        fontSize = 12.sp,
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

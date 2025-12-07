package com.example.coupleapp.ui.components.moments

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.SleepMoment
import com.example.coupleapp.data.model.SleepQuality

/**
 * Sleep moment card component
 */
@Composable
fun SleepMomentCard(
    moment: SleepMoment,
    modifier: Modifier = Modifier
) {
    val (qualityColor, qualityLabel, qualityIcon) = when (moment.quality) {
        SleepQuality.EXCELLENT -> Triple(Color(0xFF4CAF50), "Excellent", R.drawable.excellent)
        SleepQuality.GOOD -> Triple(Color(0xFFFF9800), "Good", R.drawable.good)
        SleepQuality.POOR -> Triple(Color(0xFFF44336), "Poor", R.drawable.bad)
    }
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFFB8FFD6).copy(alpha = 0.4f))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side - Quality Circle with Progress
            Box(
                modifier = Modifier.size(90.dp),
                contentAlignment = Alignment.Center
            ) {
                val progress = moment.achievementPercentage / 100f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 1000),
                    label = "progress"
                )
                
                // Draw circular progress
                Canvas(modifier = Modifier.size(90.dp)) {
                    val strokeWidth = 8.dp.toPx()
                    
                    // Background circle
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = size.minDimension / 2 - strokeWidth / 2,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    // Progress arc
                    drawArc(
                        color = qualityColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                // Center image
                Image(
                    painter = painterResource(id = qualityIcon),
                    contentDescription = qualityLabel,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape)
                )
            }

            // Right side - Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // User name
                Text(
                    text = moment.userName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF718096)
                )
                
                // Quality text
                Text(
                    text = qualityLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = qualityColor
                )
                
                // Sleep duration
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "😴", fontSize = 16.sp)
                    Text(
                        text = moment.getSleepDurationFormatted(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF2D2D2D)
                    )
                }
                
                // Time range
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🕐", fontSize = 16.sp)
                    Text(
                        text = moment.getTimeRangeFormatted(),
                        fontSize = 14.sp,
                        color = Color(0xFF718096)
                    )
                }
            }
        }
    }
}

package com.example.coupleapp.ui.components.sleep

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.SleepQuality
import com.example.coupleapp.data.model.SleepRecord
import java.time.format.DateTimeFormatter

/**
 * Compact sleep history item - Mint green card style
 */
@Composable
fun SleepHistoryItem(
    record: SleepRecord,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val (qualityColor, qualityLabel, qualityIcon) = when (record.quality) {
        SleepQuality.EXCELLENT -> Triple(Color(0xFF4CAF50), "Tuyệt vời", R.drawable.excellent)
        SleepQuality.GOOD -> Triple(Color(0xFFFF9800), "Tốt", R.drawable.good)
        SleepQuality.POOR -> Triple(Color(0xFFF44336), "Kém", R.drawable.bad)
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
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                val progress = record.achievementPercentage / 100f
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 1000),
                    label = "progress"
                )
                
                // Draw circular progress
                Canvas(modifier = Modifier.size(90.dp)) {
                    val strokeWidth = 10.dp.toPx()
                    
                    // Background circle (light gray)
                    drawCircle(
                        color = Color(0xFFE0E0E0),
                        radius = size.minDimension / 2 - strokeWidth / 2,
                        style = Stroke(width = strokeWidth)
                    )
                    
                    // Progress arc (colored by quality)
                    drawArc(
                        color = qualityColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
                
                // Center image (circular)
                Image(
                    painter = painterResource(id = qualityIcon),
                    contentDescription = qualityLabel,
                    modifier = Modifier
                        .size(70.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            // Right side - Information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Quality text with color
                Text(
                    text = qualityLabel,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = qualityColor
                )
                
                // Sleep duration with icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "😴", fontSize = 16.sp)
                    val hours = record.actualSleepDuration / 60
                    val minutes = record.actualSleepDuration % 60
                    Text(
                        text = "${hours}h ${minutes}min",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF2D2D2D)
                    )
                }
                
                // Achievement percentage with icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🎯", fontSize = 16.sp)
                    Text(
                        text = "${record.achievementPercentage.toInt()}%",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = qualityColor
                    )
                }
                
                // Time range with icon
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🌙", fontSize = 16.sp)
                    Text(
                        text = "${record.bedTime.format(timeFormatter)} - ${record.wakeUpTime.format(timeFormatter)}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp
                        ),
                        color = Color(0xFF757575)
                    )
                }
            }
        }
        
        // Small frog icon at bottom right corner (if needed)
        Text(
            text = "🐸",
            fontSize = 28.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 8.dp, y = 8.dp)
        )
    }
}

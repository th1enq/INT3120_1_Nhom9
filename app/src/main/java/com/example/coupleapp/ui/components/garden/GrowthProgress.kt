package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.Plant
import com.example.coupleapp.data.model.PlantStage

/**
 * Growth progress timer showing time until next stage
 */
@Composable
fun GrowthProgressTimer(
    plant: Plant,
    modifier: Modifier = Modifier
) {
    val timeToNextStage = plant.timeToNextStage
    
    // Calculate days, hours, minutes
    val totalMinutes = (timeToNextStage / 60000).toInt()
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60

    // Progress calculation (0-1)
    val totalStageTime = plant.stage.growthTimeHours * 60 * 60 * 1000L
    val elapsed = System.currentTimeMillis() - plant.stageStartedAt
    val progress = if (totalStageTime > 0) {
        (elapsed.toFloat() / totalStageTime).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "GrowthProgress"
    )

    // Compact layout - just progress bar and time
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        // Progress bar - smaller
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFE8F5E9))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF81C784),
                                Color(0xFF4CAF50)
                            )
                        )
                    )
            )
            // Sprout icon
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .wrapContentWidth(Alignment.End)
                    .padding(end = 2.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("🌱", fontSize = 10.sp)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Compact time display
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌿", fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                getNextStageName(plant.stage),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFF4CAF50)
            )
            Text(" in ", style = MaterialTheme.typography.labelSmall, color = Color(0xFF5D4037))
            
            if (plant.stage != PlantStage.BLOOMING) {
                CompactTimeDisplay(days = days, hours = hours, minutes = minutes)
            } else {
                Text("Bloomed! 🌸", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = Color(0xFFE91E63))
            }
        }
    }
}

/**
 * Compact time display
 */
@Composable
fun CompactTimeDisplay(days: Int, hours: Int, minutes: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (days > 0) {
            CompactTimeBadge(value = days, unit = "d")
        }
        CompactTimeBadge(value = hours, unit = "h")
        CompactTimeBadge(value = minutes, unit = "m")
    }
}

@Composable
fun CompactTimeBadge(value: Int, unit: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF4CAF50))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                color = Color.White
            )
        }
        Text(unit, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = Color(0xFF5D4037))
    }
}

/**
 * Time display with badges
 */
@Composable
fun TimeDisplay(
    days: Int,
    hours: Int,
    minutes: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (days > 0) {
            TimeBadge(value = days, unit = "d")
        }
        TimeBadge(value = hours, unit = "h")
        TimeBadge(value = minutes, unit = "min")
    }
}

/**
 * Individual time badge
 */
@Composable
fun TimeBadge(
    value: Int,
    unit: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF4CAF50))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
        }
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF5D4037)
        )
    }
}

// Greenhouse option removed as per requirement

/**
 * Get next stage name
 */
fun getNextStageName(currentStage: PlantStage): String {
    return when (currentStage) {
        PlantStage.SEED -> "Sprout"
        PlantStage.SPROUT -> "Seedling"
        PlantStage.SEEDLING -> "Growing"
        PlantStage.GROWING -> "Mature"
        PlantStage.MATURE -> "Blooming"
        PlantStage.BLOOMING -> "Complete"
    }
}

/**
 * Circular progress indicator for growth
 */
@Composable
fun CircularGrowthProgress(
    progress: Float,
    stage: PlantStage,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "CircularProgress"
    )

    Box(
        modifier = modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2

            // Background circle
            drawCircle(
                color = Color(0xFFE8F5E9),
                radius = radius,
                style = Stroke(width = strokeWidth)
            )

            // Progress arc
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color(0xFF81C784),
                        Color(0xFF4CAF50),
                        Color(0xFF2E7D32)
                    )
                ),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(
                    (size.width - radius * 2) / 2,
                    (size.height - radius * 2) / 2
                )
            )
        }

        // Stage icon
        Text(
            text = when (stage) {
                PlantStage.SEED -> "🌰"
                PlantStage.SPROUT -> "🌱"
                PlantStage.SEEDLING -> "🌿"
                PlantStage.GROWING -> "🪴"
                PlantStage.MATURE -> "🌳"
                PlantStage.BLOOMING -> "🌸"
            },
            fontSize = 24.sp
        )
    }
}

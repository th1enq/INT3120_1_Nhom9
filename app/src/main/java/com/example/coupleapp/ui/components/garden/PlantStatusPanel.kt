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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.PlantStatus
import com.example.coupleapp.data.model.PlantStatusType

/**
 * Plant Status Panel showing sunlight, water, and health bars
 */
@Composable
fun PlantStatusPanel(
    status: PlantStatus,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                Color.White.copy(alpha = 0.95f)
            )
            .padding(8.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "🌱",
                    fontSize = 10.sp
                )
                Text(
                    "Plant Status",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF4CAF50)
                )
            }

            // Status bars
            StatusBar(
                type = PlantStatusType.SUNLIGHT,
                value = status.sunlight,
                maxValue = 100f
            )

            StatusBar(
                type = PlantStatusType.WATER,
                value = status.water,
                maxValue = 100f
            )

            StatusBar(
                type = PlantStatusType.HEALTH,
                value = status.health,
                maxValue = 100f
            )
        }
    }
}

/**
 * Individual status bar
 */
@Composable
fun StatusBar(
    type: PlantStatusType,
    value: Float,
    maxValue: Float,
    modifier: Modifier = Modifier
) {
    val progress = (value / maxValue).coerceIn(0f, 1f)
    
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "StatusBarProgress"
    )

    val (icon, colors) = when (type) {
        PlantStatusType.SUNLIGHT -> "☀️" to listOf(
            Color(0xFFFFB74D),
            Color(0xFFFF9800),
            Color(0xFFF57C00)
        )
        PlantStatusType.WATER -> "💧" to listOf(
            Color(0xFF64B5F6),
            Color(0xFF2196F3),
            Color(0xFF1976D2)
        )
        PlantStatusType.HEALTH -> "🌿" to listOf(
            Color(0xFF81C784),
            Color(0xFF4CAF50),
            Color(0xFF388E3C)
        )
    }

    val barColor = when {
        progress > 0.6f -> colors[0]
        progress > 0.3f -> colors[1]
        else -> Color(0xFFFF5252) // Red when low
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 12.sp
        )

        // Progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFE0E0E0))
        ) {
            // Animated progress
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (progress > 0.3f) colors else listOf(
                                Color(0xFFFF8A80),
                                Color(0xFFFF5252)
                            )
                        )
                    )
            )

            // Segmented lines
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val segmentCount = 10
                val segmentWidth = size.width / segmentCount
                
                for (i in 1 until segmentCount) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.3f),
                        start = Offset(i * segmentWidth, 0f),
                        end = Offset(i * segmentWidth, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}

/**
 * Growth boost indicator (+25% etc)
 */
@Composable
fun GrowthBoostIndicator(
    boostPercentage: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "BoostPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BoostScale"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFE082),
                        Color(0xFFFFD54F)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "☀️",
                fontSize = 14.sp
            )
            Text(
                "+$boostPercentage%",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = Color(0xFFF57C00)
            )
        }
    }
}

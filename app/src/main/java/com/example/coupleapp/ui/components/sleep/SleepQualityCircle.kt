package com.example.coupleapp.ui.components.sleep

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.coupleapp.data.model.SleepQuality

/**
 * Circular progress indicator for sleep quality
 */
@Composable
fun SleepQualityCircle(
    quality: SleepQuality,
    achievementPercentage: Float,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = achievementPercentage / 100f,
        animationSpec = tween(
            durationMillis = 1500,
            easing = FastOutSlowInEasing
        ),
        label = "sleepProgress"
    )
    
    val (circleColor, imageRes) = when (quality) {
        SleepQuality.EXCELLENT -> Color(0xFF4CAF50) to R.drawable.excellent
        SleepQuality.GOOD -> Color(0xFFFF9800) to R.drawable.good
        SleepQuality.POOR -> Color(0xFFF44336) to R.drawable.bad
    }
    
    Box(
        modifier = modifier.size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color(0xFFF5F5F5),
                radius = size.minDimension / 2,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Progress circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sweepAngle = 360f * animatedProgress
            drawArc(
                color = circleColor,
                startAngle = -90f,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = 20.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        
        // Center image - circular clipped
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = quality.name,
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}

/**
 * Quality status text below the circle
 */
@Composable
fun SleepQualityStatus(
    qualityText: String,
    achievementPercentage: Float,
    quality: SleepQuality,
    modifier: Modifier = Modifier
) {
    val qualityColor = when (quality) {
        SleepQuality.EXCELLENT -> Color(0xFF4CAF50)
        SleepQuality.GOOD -> Color(0xFFFF9800)
        SleepQuality.POOR -> Color(0xFFF44336)
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = qualityText,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp
            ),
            color = qualityColor
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "${achievementPercentage.toInt()}% of target",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 16.sp
            ),
            color = Color(0xFF757575)
        )
    }
}

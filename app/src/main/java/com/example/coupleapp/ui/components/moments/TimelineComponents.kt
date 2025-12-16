package com.example.coupleapp.ui.components.moments

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.TimelineSection
import java.time.format.DateTimeFormatter

/**
 * Timeline indicator on the left side
 */
@Composable
fun TimelineIndicator(
    section: TimelineSection,
    isLast: Boolean,
    modifier: Modifier = Modifier
) {
    val dateText = section.label
    val dayOfMonth = section.date.dayOfMonth
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.width(60.dp)
    ) {
        // Date circle
        Surface(
            shape = CircleShape,
            color = Color(0xFFFF6B9D),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Month label
        Text(
            text = section.date.format(DateTimeFormatter.ofPattern("MMM")),
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF718096),
            fontSize = 10.sp
        )
        
        // Vertical line connecting to next section
        if (!isLast) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(80.dp)
                    .padding(top = 8.dp)
                    .background(Color(0xFFE0E0E0))
            )
        }
    }
}


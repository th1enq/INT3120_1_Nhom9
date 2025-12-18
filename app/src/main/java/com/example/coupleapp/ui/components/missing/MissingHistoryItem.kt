package com.example.coupleapp.ui.components.missing

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import com.example.coupleapp.data.model.DailyMissingHistory
import com.example.coupleapp.data.model.DailyMissingSummary

/**
 * History section with timeline design (last 7 days)
 */
@Composable
fun MissingHistorySection(
    dailyHistory: List<DailyMissingHistory>,
    currentUserId: String,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Section header
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(300)) +
                    slideInVertically(animationSpec = tween(300)) { it / 4 }
        ) {
            Text(
                text = stringResource(R.string.history_last_7_days),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFF2D2D2D),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        
        // Daily entries with timeline
        dailyHistory.forEachIndexed { dayIndex, dailyEntry ->
            DailyHistoryGroup(
                dailyHistory = dailyEntry,
                currentUserId = currentUserId,
                isLast = dayIndex == dailyHistory.lastIndex,
                visible = visible,
                delayMillis = dayIndex * 100
            )
        }
    }
}

/**
 * Group of entries for a single day with timeline
 */
@Composable
private fun DailyHistoryGroup(
    dailyHistory: DailyMissingHistory,
    currentUserId: String,
    isLast: Boolean,
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300, delayMillis = delayMillis)) +
                slideInVertically(
                    animationSpec = tween(300, delayMillis = delayMillis),
                    initialOffsetY = { it / 4 }
                ),
        modifier = modifier
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Timeline column (left side)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(60.dp)
            ) {
                // Date label
                Text(
                    text = dailyHistory.getFormattedDate(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = Color(0xFF757575),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                // Timeline dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B9D))
                )
                
                // Timeline line (if not last)
                if (!isLast) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(80.dp)
                            .background(Color(0xFFE0E0E0))
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Entries column (right side)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(bottom = if (!isLast) 16.dp else 0.dp)
            ) {
                dailyHistory.summaries.forEach { summary ->
                    DailyUserMissItem(
                        summary = summary,
                        isCurrentUser = summary.userId == currentUserId
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/**
 * Single user's daily miss count item
 */
@Composable
private fun DailyUserMissItem(
    summary: DailyMissingSummary,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) 
                Color(0xFFFFF5F8) 
            else 
                Color(0xFFF5F8FF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar icon (Person icon instead of emoji)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isCurrentUser) Color(0xFFFFE8F5) else Color(0xFFE8F5FF)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = if (isCurrentUser) Color(0xFFFF6B9D) else Color(0xFF6B9DFF),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Name
            Text(
                text = if (isCurrentUser) "You" else summary.userName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF2D2D2D),
                modifier = Modifier.weight(1f)
            )
            
            // Heart count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Multiple small hearts for visual effect
                repeat(minOf(3, summary.missCount / 20 + 1)) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = Color(0xFFFF6B9D).copy(alpha = 1f - (it * 0.2f)),
                        modifier = Modifier.size(14.dp)
                    )
                }
                
                Text(
                    text = "x${summary.missCount}",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color(0xFFFF6B9D)
                )
            }
        }
    }
}

/**
 * Empty state when no history exists
 */
@Composable
fun MissingEmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Favorite,
            contentDescription = null,
            tint = Color(0xFFE0E0E0),
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = stringResource(R.string.no_missing_records),
            style = MaterialTheme.typography.titleMedium,
            color = Color(0xFF757575)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = stringResource(R.string.tap_to_miss),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB0B0B0)
        )
    }
}

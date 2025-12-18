package com.example.coupleapp.ui.components.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.coupleapp.R
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Sleep time info card (Bedtime, Wake up, Duration)
 */
@Composable
fun SleepTimeCard(
    icon: String,
    title: String,
    time: String,
    description: String,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon emoji
        Text(
            text = icon,
            fontSize = 32.sp
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Time with edit icon (if onEdit is provided)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color(0xFF2D2D2D)
            )
            
            if (onEdit != null) {
                Spacer(modifier = Modifier.width(4.dp))
                
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF5F5F5))
                        .clickable { onEdit() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(14.dp),
                        tint = Color(0xFF757575)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Description
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp
            ),
            color = Color(0xFFB0B0B0)
        )
    }
}

/**
 * Sleep times row (Bedtime, Wake up, Duration)
 */
@Composable
fun SleepTimesRow(
    bedTime: LocalTime,
    wakeUpTime: LocalTime,
    duration: Int,
    onEditBedTime: () -> Unit,
    onEditWakeUpTime: () -> Unit,
    onEditDuration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val hours = duration / 60
    val minutes = duration % 60
    val durationText = "${hours}h ${minutes}m"
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SleepTimeCard(
            icon = "🌙",
            title = stringResource(R.string.bedtime),
            time = bedTime.format(timeFormatter),
            description = stringResource(R.string.bedtime),
            onEdit = onEditBedTime,
            modifier = Modifier.weight(1f)
        )
        
        SleepTimeCard(
            icon = "☀️",
            title = stringResource(R.string.wake_up),
            time = wakeUpTime.format(timeFormatter),
            description = stringResource(R.string.wake_up),
            onEdit = onEditWakeUpTime,
            modifier = Modifier.weight(1f)
        )
        
        SleepTimeCard(
            icon = "⏱️",
            title = stringResource(R.string.duration),
            time = durationText,
            description = stringResource(R.string.total_sleep),
            onEdit = null, // No edit button for total sleep
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Sleep stages info card
 */
@Composable
fun SleepStagesCard(
    awakeMinutes: Int,
    sleepMinutes: Int,
    modifier: Modifier = Modifier
) {
    // Helper function to format minutes
    fun formatMinutes(minutes: Int): String {
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            if (mins == 0) "${hours}h" else "${hours}h ${mins}m"
        } else {
            "${minutes}m"
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(20.dp)
    ) {
        Text(
            text = stringResource(R.string.sleep_stages),
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ),
            color = Color(0xFF2D2D2D)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Awake stage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF9800))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = stringResource(R.string.awake),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF2D2D2D)
                )
            }
            
            Text(
                text = formatMinutes(awakeMinutes),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF2D2D2D)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Sleep stage
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2196F3))
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = stringResource(R.string.sleep),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF2D2D2D)
                )
            }
            
            Text(
                text = formatMinutes(sleepMinutes),
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF2D2D2D)
            )
        }
    }
}

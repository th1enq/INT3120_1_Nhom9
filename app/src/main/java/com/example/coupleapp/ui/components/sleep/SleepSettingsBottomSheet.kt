package com.example.coupleapp.ui.components.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom sheet content for sleep settings
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSettingsBottomSheet(
    bedTime: String = "22:00",
    sleepGoal: String = "8h 0m",
    onAddWidgetClick: () -> Unit,
    onWhenToSleepClick: () -> Unit,
    onSleepGoalClick: () -> Unit,
    onMyHistoryClick: () -> Unit,
    onInsertMockDataClick: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color.White,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(vertical = 24.dp)
    ) {
        // Handle bar
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE0E0E0))
                .align(Alignment.CenterHorizontally)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Title
        Text(
            text = "Sleep Settings",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
            color = Color(0xFF2D2D2D),
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Settings items
        SettingsItem(
            icon = Icons.Default.Add,
            title = "Add Widget",
            currentValue = "",
            onClick = onAddWidgetClick
        )
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color(0xFFF5F5F5)
        )
        
        SettingsItem(
            icon = Icons.Default.DateRange,
            title = "When to Sleep",
            currentValue = bedTime,
            onClick = onWhenToSleepClick
        )
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color(0xFFF5F5F5)
        )
        
        SettingsItem(
            icon = Icons.Default.CheckCircle,
            title = "Sleep Goal",
            currentValue = sleepGoal,
            onClick = onSleepGoalClick
        )
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color(0xFFF5F5F5)
        )
        
        SettingsItem(
            icon = Icons.AutoMirrored.Filled.List,
            title = "My History",
            currentValue = "",
            onClick = onMyHistoryClick
        )
        
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 24.dp),
            color = Color(0xFFF5F5F5)
        )
        
        SettingsItem(
            icon = Icons.Default.Edit,
            title = "Insert Mock Data (Test)",
            currentValue = "",
            onClick = onInsertMockDataClick
        )
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Individual settings item
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    currentValue: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFF757575)
                )
            }
            
            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                ),
                color = Color(0xFF2D2D2D)
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Current value
            if (currentValue.isNotEmpty()) {
                Text(
                    text = currentValue,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp
                    ),
                    color = Color(0xFF757575)
                )
            }
            
            // Arrow icon
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Navigate",
                modifier = Modifier.size(24.dp),
                tint = Color(0xFFB0B0B0)
            )
        }
    }
}

package com.example.coupleapp.ui.components.locket

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.LocketTab
import java.util.Locale

/**
 * Tab item composable for Locket feature
 */
@Composable
fun LocketTabItem(
    tab: LocketTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2D2D2D) else Color(0xFFB0B0B0),
        animationSpec = tween(300),
        label = "textColor"
    )
    
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF4CAF50) else Color(0xFFB0B0B0),
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    val isVietnamese = Locale.getDefault().language == "vi"
    val localizedTitle = tab.getLocalizedTitle(isVietnamese)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = getTabIcon(tab),
                contentDescription = localizedTitle,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            
            AnimatedVisibility(
                visible = isSelected,
                enter = fadeIn(animationSpec = tween(200)) + 
                        expandHorizontally(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200)) + 
                       shrinkHorizontally(animationSpec = tween(200))
            ) {
                Text(
                    text = localizedTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = textColor
                )
            }
        }
        
        // Animated underline
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(animationSpec = tween(200)) + 
                    expandHorizontally(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) + 
                   shrinkHorizontally(animationSpec = tween(200))
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .height(3.dp)
                    .width(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF4CAF50))
            )
        }
    }
}

/**
 * Tab bar for Locket feature with 4 tabs
 */
@Composable
fun LocketTabBar(
    selectedTab: LocketTab,
    onTabSelected: (LocketTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LocketTab.entries.forEach { tab ->
            LocketTabItem(
                tab = tab,
                isSelected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

/**
 * Get icon for each tab
 */
private fun getTabIcon(tab: LocketTab): ImageVector {
    return when (tab) {
        LocketTab.PHOTO -> Icons.Outlined.Image
        LocketTab.EMOJI -> Icons.Outlined.EmojiEmotions
        LocketTab.DRAWING -> Icons.Outlined.Brush
        LocketTab.TEXT -> Icons.Outlined.TextFields
    }
}

package com.example.coupleapp.ui.components.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class BottomNavItem(val icon: ImageVector, val iconSelected: ImageVector, val label: String) {
    HOME(Icons.Outlined.Home, Icons.Filled.Home, "Home"),
    FRIENDS(Icons.Outlined.Person, Icons.Filled.Person, "Partner"),
    ACTIVITIES(Icons.Outlined.DateRange, Icons.Filled.DateRange, "Activities"),
    PROFILE(Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle, "Profile")
}

@Composable
fun CoupleBottomNavigation(
    selectedItem: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomNavItem.entries.forEach { item ->
            BottomNavItemView(
                item = item,
                isSelected = selectedItem == item,
                onClick = { onItemSelected(item) }
            )
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // Icon color animation
    val iconColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFF6B9D) else Color(0xFFB0B0B0),
        animationSpec = tween(300),
        label = "iconColor"
    )
    
    // Background color animation
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFFFFE8F5) else Color.Transparent,
        animationSpec = tween(300),
        label = "backgroundColor"
    )
    
    // Scale animation
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .scale(scale)
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Icon
        Icon(
            imageVector = if (isSelected) item.iconSelected else item.icon,
            contentDescription = item.label,
            tint = iconColor,
            modifier = Modifier.size(26.dp)
        )
    }
}

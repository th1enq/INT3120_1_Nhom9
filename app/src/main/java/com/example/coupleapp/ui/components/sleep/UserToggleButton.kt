package com.example.coupleapp.ui.components.sleep

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Floating toggle button to switch between users
 */
@Composable
fun UserToggleButton(
    currentUserName: String,
    partnerUserName: String,
    isCurrentUser: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "toggleScale"
    )
    
    val offsetX by animateFloatAsState(
        targetValue = if (isCurrentUser) 0f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "toggleOffset"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(
                color = Color.White,
                shape = CircleShape
            )
            .clickable {
                isPressed = true
                onToggle()
            }
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Current User Button
            UserToggleItem(
                userName = currentUserName,
                isActive = isCurrentUser,
                color = Color(0xFFFF9ECE)
            )
            
            // Divider
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(24.dp)
                    .background(Color(0xFFF5F5F5))
            )
            
            // Partner User Button
            UserToggleItem(
                userName = partnerUserName,
                isActive = !isCurrentUser,
                color = Color(0xFF9ED9FF)
            )
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(100)
            isPressed = false
        }
    }
}

@Composable
private fun UserToggleItem(
    userName: String,
    isActive: Boolean,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(if (isActive) color.copy(alpha = 0.2f) else Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isActive) color else Color(0xFFF5F5F5)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = userName,
                modifier = Modifier.size(18.dp),
                tint = if (isActive) Color.White else Color(0xFFB0B0B0)
            )
        }
        
        Text(
            text = userName,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp
            ),
            color = if (isActive) Color(0xFF2D2D2D) else Color(0xFFB0B0B0)
        )
    }
}

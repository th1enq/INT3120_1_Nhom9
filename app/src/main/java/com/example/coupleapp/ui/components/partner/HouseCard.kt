package com.example.coupleapp.ui.components.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.PartnerShortcut
import com.example.coupleapp.data.model.PartnerUser
import com.example.coupleapp.ui.theme.*

/**
 * Card hình ngôi nhà hiển thị thông tin partner và các shortcut
 */
@Composable
fun HouseCard(
    partner: PartnerUser?,
    partnerDistance: String = "",
    partnerLocation: String = "",
    shortcuts: List<PartnerShortcut>,
    onChatClick: () -> Unit,
    onShortcutClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("HouseCard", "[PARTNER] HouseCard composing, partner: ${partner?.name}")
    android.util.Log.d("HouseCard", "[PARTNER] Shortcuts count: ${shortcuts.size}")
    
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        android.util.Log.d("HouseCard", "[PARTNER] HouseCard LaunchedEffect")
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500)) +
                scaleIn(initialScale = 0.9f, animationSpec = tween(500))
    ) {
        android.util.Log.d("HouseCard", "[PARTNER] AnimatedVisibility visible, rendering Surface")
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF8BC34A), // Màu xanh lá giống ảnh
            shadowElevation = 8.dp
        ) {
            android.util.Log.d("HouseCard", "[PARTNER] Surface rendered, rendering Column")
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                android.util.Log.d("HouseCard", "[PARTNER] Column rendered, rendering HouseTopSection")
                // Phần trên - Thông tin partner và hình ngôi nhà
                HouseTopSection(
                    partner = partner,
                    partnerDistance = partnerDistance,
                    partnerLocation = partnerLocation,
                    onChatClick = onChatClick
                )
                
                android.util.Log.d("HouseCard", "[PARTNER] HouseTopSection rendered, rendering HouseBottomSection")
                // Phần dưới - Grid shortcuts
                HouseBottomSection(
                    shortcuts = shortcuts,
                    onShortcutClick = onShortcutClick
                )
                android.util.Log.d("HouseCard", "[PARTNER] HouseBottomSection rendered")
            }
        }
    }
}

@Composable
private fun HouseTopSection(
    partner: PartnerUser?,
    partnerDistance: String,
    partnerLocation: String,
    onChatClick: () -> Unit
) {
    android.util.Log.d("HouseTopSection", "[PARTNER] Composing HouseTopSection for: ${partner?.name}")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(75.dp)
    ) {
        // Background gradient xanh lá
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF7CB342),
                            Color(0xFF8BC34A)
                        )
                    )
                )
        )
        
        // Hình ngôi nhà ở giữa - removed living room icons
        
        // Avatar + Tên partner góc trái
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            PartnerAvatar(
                name = partner?.name ?: "?",
                avatarUrl = partner?.avatarUrl,
                size = 48
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column {
                Text(
                    text = partner?.name ?: "Partner",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                if (partnerDistance.isNotEmpty() || partnerLocation.isNotEmpty()) {
                    Text(
                        text = buildString {
                            if (partnerDistance.isNotEmpty()) append(partnerDistance)
                            if (partnerDistance.isNotEmpty() && partnerLocation.isNotEmpty()) append(" · ")
                            if (partnerLocation.isNotEmpty()) append(partnerLocation)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        
        // Nút chat góc phải
        ChatButton(
            onClick = onChatClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun HouseBottomSection(
    shortcuts: List<PartnerShortcut>,
    onShortcutClick: (String) -> Unit
) {
    android.util.Log.d("HouseBottomSection", "[PARTNER] Composing HouseBottomSection, shortcuts: ${shortcuts.size}")
    // Chia shortcuts: 4 đầu bo tròn, 4 sau vuông
    val roundedShortcuts = shortcuts.take(4) // store, calendar, quest, garden
    val squareShortcuts = shortcuts.drop(4).take(4) // sleep, locket, missing, distance
    android.util.Log.d("HouseBottomSection", "[PARTNER] Rounded: ${roundedShortcuts.size}, Square: ${squareShortcuts.size}")
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFE8F5E9),
        shape = RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hàng 1: 4 shortcuts bo tròn (giống trang chủ)
            android.util.Log.d("HouseBottomSection", "[PARTNER] Rendering rounded shortcuts row")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                roundedShortcuts.forEachIndexed { index, shortcut ->
                    android.util.Log.d("HouseBottomSection", "[PARTNER] Rendering rounded shortcut $index: ${shortcut.name}")
                    RoundedShortcutItem(
                        shortcut = shortcut,
                        onClick = { 
                            android.util.Log.d("HouseBottomSection", "[PARTNER] Rounded shortcut clicked: ${shortcut.name}")
                            onShortcutClick(shortcut.route) 
                        }
                    )
                }
            }
            
            // Hàng 2: 4 shortcuts vuông
            android.util.Log.d("HouseBottomSection", "[PARTNER] Rendering square shortcuts row")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                squareShortcuts.forEachIndexed { index, shortcut ->
                    android.util.Log.d("HouseBottomSection", "[PARTNER] Rendering square shortcut $index: ${shortcut.name}")
                    SquareShortcutItem(
                        shortcut = shortcut,
                        onClick = { 
                            android.util.Log.d("HouseBottomSection", "[PARTNER] Square shortcut clicked: ${shortcut.name}")
                            onShortcutClick(shortcut.route) 
                        }
                    )
                }
            }
        }
    }
}

/**
 * Shortcut bo tròn (giống trang chủ)
 */
@Composable
private fun RoundedShortcutItem(
    shortcut: PartnerShortcut,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        modifier = Modifier
            .width(70.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon circular
        val iconRes = getDrawableForShortcut(shortcut.id)
        android.util.Log.d("RoundedShortcutItem", "[PARTNER] Rendering shortcut ${shortcut.name}, iconRes: $iconRes")
        
        if (iconRes != 0) {
            android.util.Log.d("RoundedShortcutItem", "[PARTNER] Loading image from resource")
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = shortcut.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback
            android.util.Log.d("RoundedShortcutItem", "[PARTNER] Using fallback icon")
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(shortcut.backgroundColor)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForShortcut(shortcut.iconName),
                    contentDescription = shortcut.name,
                    tint = Color(shortcut.iconColor),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = shortcut.name,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Shortcut vuông (giống widget trang chủ)
 */
@Composable
private fun SquareShortcutItem(
    shortcut: PartnerShortcut,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Column(
        modifier = Modifier
            .width(70.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon square
        val iconRes = getDrawableForShortcut(shortcut.id)
        
        if (iconRes != 0) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = shortcut.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(shortcut.backgroundColor)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getIconForShortcut(shortcut.iconName),
                    contentDescription = shortcut.name,
                    tint = Color(shortcut.iconColor),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(6.dp))
        
        Text(
            text = shortcut.name,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PartnerAvatar(
    name: String,
    avatarUrl: String?,
    size: Int
) {
    // Sử dụng hình giống broccoli trong ảnh
    Surface(
        modifier = Modifier.size(size.dp),
        shape = CircleShape,
        color = Color(0xFF81C784)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (avatarUrl.isNullOrEmpty()) {
                // Emoji broccoli như trong ảnh
                Text(
                    text = "🥦",
                    fontSize = (size / 2).sp
                )
            } else {
                // TODO: Load image from URL
                Text(
                    text = name.firstOrNull()?.toString() ?: "?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ChatButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Surface(
        modifier = modifier
            .size(44.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        shape = CircleShape,
        color = Color(0xFF4CAF50)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Message,
                contentDescription = "Chat",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Lấy drawable resource cho shortcut
 */
private fun getDrawableForShortcut(shortcutId: String): Int {
    android.util.Log.d("HouseCard", "[PARTNER] Getting drawable for shortcut: $shortcutId")
    val result = when (shortcutId) {
        "sleep" -> R.drawable.sleep_tracker
        "locket" -> R.drawable.locket
        "missing" -> R.drawable.missing
        "location" -> R.drawable.distance
        "calendar" -> R.drawable.calendar
        "garden" -> R.drawable.garden
        "quest" -> R.drawable.quest
        "store" -> R.drawable.store
        else -> 0
    }
    android.util.Log.d("HouseCard", "[PARTNER] Drawable resource ID: $result")
    return result
}

/**
 * Fallback icon nếu không có drawable
 */
private fun getIconForShortcut(iconName: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconName) {
        "bedtime" -> Icons.Default.Bedtime
        "photo_camera" -> Icons.Default.PhotoCamera
        "favorite" -> Icons.Default.Favorite
        "location_on" -> Icons.Default.LocationOn
        "event" -> Icons.Default.Event
        "local_florist" -> Icons.Default.LocalFlorist
        "assignment" -> Icons.Default.Checklist
        "store" -> Icons.Default.Store
        else -> Icons.Default.Apps
    }
}

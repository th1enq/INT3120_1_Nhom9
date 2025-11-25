package com.example.coupleapp.ui.components.locket

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.EmojiCategory
import com.example.coupleapp.data.model.EmojiItem

/**
 * Emoji content view for Locket
 */
@Composable
fun LocketEmojiContent(
    selectedEmoji: String?,
    onSelectEmoji: () -> Unit,
    onSendEmoji: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Emoji display box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF4A7C59),
                            Color(0xFF3D6B4C)
                        )
                    )
                )
                .clickable { onSelectEmoji() },
            contentAlignment = Alignment.Center
        ) {
            if (selectedEmoji != null) {
                Text(
                    text = selectedEmoji,
                    fontSize = 120.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                // Placeholder funny image/emoji
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "🥳",
                        fontSize = 80.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Choose an emoji",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Add emoji button
        EmojiActionButton(
            hasEmoji = selectedEmoji != null,
            onSelectClick = onSelectEmoji,
            onSendClick = onSendEmoji
        )
    }
}

/**
 * Action button for emoji tab
 */
@Composable
private fun EmojiActionButton(
    hasEmoji: Boolean,
    onSelectClick: () -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Select emoji button
        CaptureButton(
            onClick = onSelectClick,
            color = if (hasEmoji) Color(0xFFF5F5F5) else Color(0xFF4CAF50),
            modifier = Modifier.size(if (hasEmoji) 56.dp else 76.dp)
        )
        
        if (hasEmoji) {
            Spacer(modifier = Modifier.width(32.dp))
            
            // Send button
            CaptureButton(
                onClick = onSendClick,
                color = Color(0xFF4CAF50)
            )
        }
    }
}

/**
 * Emoji picker bottom sheet content
 */
@Composable
fun EmojiPickerContent(
    emojis: List<EmojiItem>,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(EmojiCategory.SMILEYS) }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF4A7C59),
                        Color(0xFF3D6B4C)
                    )
                )
            )
            .padding(top = 16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
            
            Text(
                text = "Choose an Emoji",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            
            Spacer(modifier = Modifier.width(48.dp))
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Category tabs
        EmojiCategoryTabs(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Emoji grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val filteredEmojis = if (selectedCategory == EmojiCategory.RECENT) {
                emojis.take(8)
            } else {
                emojis.filter { it.category == selectedCategory }
            }
            
            items(filteredEmojis) { emoji ->
                EmojiGridItem(
                    emoji = emoji,
                    onClick = { onEmojiSelected(emoji.emoji) }
                )
            }
        }
    }
}

/**
 * Category tabs for emoji picker
 */
@Composable
private fun EmojiCategoryTabs(
    selectedCategory: EmojiCategory,
    onCategorySelected: (EmojiCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf(
        "Status" to EmojiCategory.SMILEYS,
        "Emoji" to EmojiCategory.SYMBOLS
    )
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.forEach { (label, category) ->
            val isSelected = selectedCategory == category
            
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isSelected) Color.White.copy(alpha = 0.2f)
                        else Color.Transparent
                    )
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Single emoji item in grid
 */
@Composable
private fun EmojiGridItem(
    emoji: EmojiItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "emojiScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .size(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji.emoji,
            fontSize = 36.sp
        )
    }
}

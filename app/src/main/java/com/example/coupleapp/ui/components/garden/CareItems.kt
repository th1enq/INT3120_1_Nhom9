package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.*

/**
 * Tab selector for care items
 */
@Composable
fun CareTabSelector(
    selectedTab: GardenTab,
    onTabSelected: (GardenTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GardenTab.values().forEach { tab ->
            val isSelected = tab == selectedTab
            
            val backgroundColor by animateColorAsState(
                targetValue = if (isSelected) {
                    when (tab) {
                        GardenTab.CARE -> Color(0xFFFFE0B2)
                        GardenTab.BOOST -> Color(0xFFE1BEE7)
                        GardenTab.SEEDS -> Color(0xFFC8E6C9)
                    }
                } else Color.White,
                animationSpec = tween(300),
                label = "TabBg"
            )

            val textColor by animateColorAsState(
                targetValue = if (isSelected) {
                    when (tab) {
                        GardenTab.CARE -> Color(0xFFE65100)
                        GardenTab.BOOST -> Color(0xFF7B1FA2)
                        GardenTab.SEEDS -> Color(0xFF2E7D32)
                    }
                } else Color(0xFF757575),
                animationSpec = tween(300),
                label = "TabText"
            )

            val icon = when (tab) {
                GardenTab.CARE -> "🌿"
                GardenTab.BOOST -> "⚡"
                GardenTab.SEEDS -> "🌱"
            }

            Button(
                onClick = { onTabSelected(tab) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = backgroundColor
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(icon, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tab.vietnameseName,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Grid of care items
 */
@Composable
fun CareItemsGrid(
    items: List<CareItem>,
    selectedTab: GardenTab,
    onItemClick: (CareItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredItems = items.filter { item ->
        when (selectedTab) {
            GardenTab.CARE -> item.type in listOf(
                CareItemType.WATER,
                CareItemType.SUNLIGHT,
                CareItemType.PESTICIDE,
                CareItemType.SCISSORS
            )
            GardenTab.BOOST -> item.type in listOf(
                CareItemType.FERTILIZER_4H,
                CareItemType.FERTILIZER_8H,
                CareItemType.FERTILIZER_24H
            )
            GardenTab.SEEDS -> item.type in listOf(
                CareItemType.SEED_NORMAL,
                CareItemType.SEED_RARE,
                CareItemType.SEED_SUPER_RARE
            )
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(filteredItems) { item ->
            CareItemCard(
                item = item,
                onClick = { onItemClick(item) }
            )
        }
    }
}

/**
 * Individual care item card
 */
@Composable
fun CareItemCard(
    item: CareItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isAvailable = item.quantity > 0
    
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "ItemScale"
    )

    Column(
        modifier = modifier
            .scale(scale)
            .height(120.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isAvailable) Color.White else Color(0xFFF5F5F5)
            )
            .border(
                width = 2.dp,
                color = if (isAvailable) Color(0xFFE8F5E9) else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(enabled = isAvailable) {
                isPressed = true
                onClick()
            }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Item image with quantity badge
        Box {
            Image(
                painter = painterResource(id = item.iconRes),
                contentDescription = item.name,
                modifier = Modifier
                    .size(70.dp)
                    .then(
                        if (!isAvailable) Modifier.graphicsLayer { alpha = 0.5f }
                        else Modifier
                    ),
                contentScale = ContentScale.Fit
            )

            // Quantity badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (isAvailable) Color(0xFF4CAF50) else Color(0xFFBDBDBD)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "x${item.quantity}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    ),
                    color = Color.White
                )
            }

            // PRO badge for special items
            if (item.type == CareItemType.FERTILIZER_24H || item.type == CareItemType.SEED_SUPER_RARE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFD700),
                                    Color(0xFFFFA000)
                                )
                            )
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        "PRO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 8.sp
                        ),
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Item name
        Text(
            text = item.vietnameseName,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            ),
            color = if (isAvailable) Color(0xFF3E2723) else Color(0xFF9E9E9E),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Widget notification for plant care
 */
@Composable
fun PlantCareNotification(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFFF8E1))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "⚠️",
            fontSize = 18.sp
        )
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFE65100),
            modifier = Modifier.weight(1f)
        )

        Text(
            "→",
            color = Color(0xFFE65100),
            fontSize = 16.sp
        )
    }
}

/**
 * Side buttons for Gallery and Shop
 */
@Composable
fun SideButtons(
    onGalleryClick: () -> Unit,
    onShopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Gallery button
        SideButtonEmoji(
            icon = "📖",
            label = "Gallery",
            onClick = onGalleryClick,
            backgroundColor = Color(0xFFFFF9C4)
        )

        // Shop button with store image
        SideButtonImage(
            imageRes = com.example.coupleapp.R.drawable.store,
            label = "Shop",
            onClick = onShopClick,
            backgroundColor = Color(0xFFFFCDD2)
        )
    }
}

/**
 * Side button with emoji icon
 */
@Composable
fun SideButtonEmoji(
    icon: String,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            icon,
            fontSize = 24.sp
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF3E2723)
        )
    }
}

/**
 * Side button with circular image (like store)
 */
@Composable
fun SideButtonImage(
    imageRes: Int,
    label: String,
    onClick: () -> Unit,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Image(
            painter = painterResource(id = imageRes),
            contentDescription = label,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Medium
            ),
            color = Color(0xFF3E2723)
        )
    }
}

/**
 * Collection grid for displaying harvested plants
 */
@Composable
fun CollectionGrid(
    collection: List<CollectedPlant>,
    onItemClick: (CollectedPlant) -> Unit,
    modifier: Modifier = Modifier
) {
    if (collection.isEmpty()) {
        // Empty collection message
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "📚",
                    fontSize = 48.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Bộ sưu tập trống",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF5D4037)
                )
                Text(
                    "Thu hoạch cây để thêm vào bộ sưu tập!",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8D6E63)
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(collection) { plant ->
                CollectionItemCard(
                    plant = plant,
                    onClick = { onItemClick(plant) }
                )
            }
        }
    }
}

/**
 * Card for a collected plant in the collection grid
 */
@Composable
fun CollectionItemCard(
    plant: CollectedPlant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(plant.flowerColor.hexColor)
    val rarityStars = "⭐".repeat(plant.rarity.starsCount)
    
    Card(
        modifier = modifier
            .aspectRatio(0.85f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Plant type icon with custom color tint
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                backgroundColor.copy(alpha = 0.3f),
                                backgroundColor.copy(alpha = 0.1f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = plant.plantType.unlockedImageRes),
                    contentDescription = plant.plantType.displayName,
                    modifier = Modifier
                        .size(40.dp)
                        .graphicsLayer {
                            // Apply custom color hue if set
                            plant.customColorHue?.let { hue ->
                                // Color matrix transformation would go here
                            }
                        },
                    contentScale = ContentScale.Fit
                )
            }
            
            // Plant name
            Text(
                text = plant.plantType.vietnameseName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Medium
                ),
                color = Color(0xFF5D4037),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            
            // Color indicator
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
            
            // Rarity stars
            Text(
                text = rarityStars,
                fontSize = 10.sp,
                maxLines = 1
            )
            
            // Partner bonus indicator
            if (plant.partnerContributedCare) {
                Text(
                    text = "💕",
                    fontSize = 10.sp
                )
            }
        }
    }
}

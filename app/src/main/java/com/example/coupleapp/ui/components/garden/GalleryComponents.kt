package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*

/**
 * Gallery Screen showing plant collection
 */
@Composable
fun GalleryDialog(
    gallery: List<GalleryPlant>,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(onClick = onDismiss)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.85f)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .clickable(enabled = false) {} // Prevent dismiss when clicking inside
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "📖 Collection",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color(0xFF3E2723)
                    )

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF757575)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Statistics
                val unlockedCount = gallery.count { it.isUnlocked }
                val totalCount = gallery.size
                Text(
                    "Unlocked: $unlockedCount / $totalCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gallery sections by rarity
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PlantRarity.values().reversed().forEach { rarity ->
                        val plantsInRarity = gallery.filter { it.rarity == rarity }
                        
                        item {
                            GallerySection(
                                rarity = rarity,
                                plants = plantsInRarity
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Gallery section for a rarity level
 */
@Composable
fun GallerySection(
    rarity: PlantRarity,
    plants: List<GalleryPlant>
) {
    val (sectionColor, sectionIcon) = when (rarity) {
        PlantRarity.SUPER_RARE -> Color(0xFFFFD700) to "⭐"
        PlantRarity.RARE -> Color(0xFF9C27B0) to "💎"
        PlantRarity.UNCOMMON -> Color(0xFF2196F3) to "🔷"
        PlantRarity.COMMON -> Color(0xFF4CAF50) to "🌿"
    }

    val unlockedCount = plants.count { it.isUnlocked }

    Column {
        // Section header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(sectionColor.copy(alpha = 0.1f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(sectionIcon, fontSize = 20.sp)
                Text(
                    rarity.vietnameseName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = sectionColor
                )
            }

            Text(
                "$unlockedCount / ${plants.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = sectionColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Plants grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier
                .fillMaxWidth()
                .height(((plants.size / 4 + 1) * 100).dp.coerceAtMost(200.dp)),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(plants) { plant ->
                GalleryPlantItem(
                    plant = plant,
                    rarityColor = sectionColor
                )
            }
        }
    }
}

/**
 * Individual gallery plant item
 */
@Composable
fun GalleryPlantItem(
    plant: GalleryPlant,
    rarityColor: Color
) {
    val isUnlocked = plant.isUnlocked

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isUnlocked) Color.White else Color(0xFFF5F5F5)
            )
            .border(
                width = 2.dp,
                color = if (isUnlocked) rarityColor else Color(0xFFE0E0E0),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            // Show plant with color
            val colorMatrix = createHueRotationMatrix(plant.flowerColor.colorHue)
            
            Image(
                painter = painterResource(id = R.drawable.blooming),
                contentDescription = plant.flowerColor.displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(colorMatrix)
            )
        } else {
            // Locked state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "🔒",
                    fontSize = 24.sp
                )
                Text(
                    "???",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBDBDBD)
                )
            }
        }
    }
}

/**
 * Seed Selection Dialog when planting new seed
 */
@Composable
fun SeedSelectionDialog(
    inventory: GardenInventory,
    onSeedSelected: (CareItemType) -> Unit,
    onDismiss: () -> Unit
) {
    val seedItems = listOf(
        CareItemType.SEED_NORMAL,
        CareItemType.SEED_RARE,
        CareItemType.SEED_SUPER_RARE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🌱", fontSize = 24.sp)
                Text(
                    "Select Seed",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2723)
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                seedItems.forEach { seedType ->
                    val item = inventory.getItem(seedType) ?: createDefaultItem(seedType)
                    val hasSeeds = item.quantity > 0
                    
                    val (seedColor, seedRarity) = when (seedType) {
                        CareItemType.SEED_NORMAL -> Color(0xFF8D6E63) to "Common"
                        CareItemType.SEED_RARE -> Color(0xFF7B1FA2) to "Rare"
                        CareItemType.SEED_SUPER_RARE -> Color(0xFFFFD700) to "Super Rare"
                        else -> Color.Gray to ""
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (hasSeeds) seedColor.copy(alpha = 0.1f) else Color(0xFFF5F5F5)
                            )
                            .then(
                                if (hasSeeds) Modifier.clickable { onSeedSelected(seedType) }
                                else Modifier
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = item.iconRes),
                                contentDescription = item.name,
                                modifier = Modifier
                                    .size(48.dp)
                                    .then(
                                        if (!hasSeeds) Modifier.graphicsLayer { alpha = 0.5f }
                                        else Modifier
                                    ),
                                contentScale = ContentScale.Fit
                            )

                            Column {
                                Text(
                                    item.vietnameseName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = if (hasSeeds) seedColor else Color(0xFFBDBDBD)
                                )
                                Text(
                                    seedRarity,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasSeeds) seedColor.copy(alpha = 0.7f) else Color(0xFFBDBDBD)
                                )
                            }
                        }

                        // Quantity
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (hasSeeds) seedColor else Color(0xFFBDBDBD)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "x${item.quantity}",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Cancel",
                    color = Color(0xFF757575)
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Plant Death Dialog
 */
@Composable
fun PlantDeathDialog(
    onPlantNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("😢", fontSize = 48.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your plant has died",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3E2723),
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                "Don't worry, you can plant a new one! Remember to take care of your plant regularly.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF757575),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onPlantNew,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Plant New 🌱",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    "Close",
                    color = Color(0xFF757575)
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * Success animation when plant blooms
 */
@Composable
fun PlantBloomCelebration(
    plant: Plant,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Celebration")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CelebrationScale"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Confetti
            Text("🎉✨🎊", fontSize = 32.sp)
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Congratulations!",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color(0xFFE91E63)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Your plant has bloomed!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF3E2723)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Blooming plant preview
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .scale(scale)
            ) {
                val colorMatrix = createHueRotationMatrix(plant.flowerColor.colorHue)
                
                Image(
                    painter = painterResource(id = R.drawable.blooming),
                    contentDescription = "Blooming plant",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(colorMatrix)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Color: ${plant.flowerColor.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF757575)
            )

            Text(
                "Rarity: ${plant.rarity.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = when (plant.rarity) {
                    PlantRarity.SUPER_RARE -> Color(0xFFFFD700)
                    PlantRarity.RARE -> Color(0xFF9C27B0)
                    PlantRarity.UNCOMMON -> Color(0xFF2196F3)
                    PlantRarity.COMMON -> Color(0xFF4CAF50)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE91E63)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "Harvest & Plant New 🌱",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

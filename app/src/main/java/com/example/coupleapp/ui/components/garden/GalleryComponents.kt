package com.example.coupleapp.ui.components.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
 * Gallery Screen showing plant collection - Simplified version
 * Shows plants by PlantType only (not by color combination)
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

                // Statistics - count by PlantType (9 types x 4 rarities = 36 total)
                // Group by plantType+rarity, count if any color is unlocked
                val groupedByTypeRarity = gallery.groupBy { "${it.plantType}_${it.rarity}" }
                val totalTypes = groupedByTypeRarity.size
                val unlockedTypes = groupedByTypeRarity.count { (_, plants) -> plants.any { it.isUnlocked } }
                
                Text(
                    "Unlocked: $unlockedTypes / $totalTypes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Gallery sections by rarity - simplified to show by PlantType only
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    PlantRarity.values().reversed().forEach { rarity ->
                        // Group by PlantType, count unique unlocked colors
                        val plantsInRarity = gallery.filter { it.rarity == rarity }
                        
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

/**
 * Gallery section for a rarity level - shows plants grouped by PlantType
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

    // Group by PlantType - show only one item per plant type (with first unlocked color or locked)
    val plantsByType = plants.groupBy { it.plantType }
    val displayPlants = plantsByType.map { (plantType, plantsOfType) ->
        // Prefer showing an unlocked version if any exists
        plantsOfType.find { it.isUnlocked } ?: plantsOfType.first()
    }
    
    val unlockedTypes = displayPlants.count { it.isUnlocked }

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
                "$unlockedTypes / ${displayPlants.size}",
                style = MaterialTheme.typography.bodyMedium,
                color = sectionColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Plants grid using simple Column + Row (not LazyVerticalGrid to avoid nesting issues)
        val rows = displayPlants.chunked(4)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            rows.forEach { rowPlants ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowPlants.forEach { plant ->
                        Box(modifier = Modifier.weight(1f)) {
                            GalleryPlantItem(
                                plant = plant,
                                rarityColor = sectionColor
                            )
                        }
                    }
                    // Fill empty slots if row is not full
                    repeat(4 - rowPlants.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
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
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isUnlocked) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                // Show plant with color
                val colorMatrix = createHueRotationMatrix(plant.flowerColor.colorHue)
                
                Image(
                    painter = painterResource(id = R.drawable.blooming),
                    contentDescription = plant.flowerColor.displayName,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(colorMatrix)
                )
                
                // Plant name
                Text(
                    text = plant.plantType.vietnameseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = rarityColor,
                    maxLines = 1,
                    fontSize = 8.sp
                )
                
                // Flower color name
                Text(
                    text = plant.flowerColor.vietnameseName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF757575),
                    maxLines = 1,
                    fontSize = 7.sp
                )
            }
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

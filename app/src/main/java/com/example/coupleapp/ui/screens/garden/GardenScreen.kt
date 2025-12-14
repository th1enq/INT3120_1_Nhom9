package com.example.coupleapp.ui.screens.garden

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.model.*
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.garden.*
import com.example.coupleapp.viewmodel.GardenViewModelFirebase
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun GardenScreen(
    onBackClick: () -> Unit,
    onNavigateToStore: () -> Unit,
    viewModel: GardenViewModelFirebase = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGalleryDialog by remember { mutableStateOf(false) }
    var showSeedDialog by remember { mutableStateOf(false) }
    var showDeathDialog by remember { mutableStateOf(false) }
    var showBloomCelebration by remember { mutableStateOf(false) }
    var isItemAnimating by remember { mutableStateOf(false) }

    // Check for plant death
    LaunchedEffect(uiState.plant?.status?.isDead) {
        if (uiState.plant?.status?.isDead == true) {
            showDeathDialog = true
        }
    }

    // Check for blooming
    LaunchedEffect(uiState.plant?.stage) {
        if (uiState.plant?.stage == PlantStage.BLOOMING) {
            showBloomCelebration = true
        }
    }

    // Handle item animation
    LaunchedEffect(uiState.showItemAnimation) {
        isItemAnimating = uiState.showItemAnimation
    }

    // Dialogs
    if (uiState.showRenameDialog && uiState.plant != null) {
        RenamePlantDialog(
            currentName = uiState.plant!!.name,
            onDismiss = { viewModel.showRenameDialog(false) },
            onConfirm = { viewModel.renamePlant(it) }
        )
    }

    if (showGalleryDialog) {
        GalleryDialog(
            gallery = uiState.gallery,
            onDismiss = { showGalleryDialog = false }
        )
    }

    if (showSeedDialog) {
        SeedSelectionDialog(
            inventory = uiState.inventory,
            onSeedSelected = { seedType ->
                viewModel.plantSeed(seedType)
                showSeedDialog = false
            },
            onDismiss = { showSeedDialog = false }
        )
    }

    if (showDeathDialog) {
        PlantDeathDialog(
            onPlantNew = {
                showDeathDialog = false
                showSeedDialog = true
            },
            onDismiss = { showDeathDialog = false }
        )
    }

    if (showBloomCelebration && uiState.plant != null) {
        PlantBloomCelebration(
            plant = uiState.plant!!,
            onDismiss = {
                showBloomCelebration = false
                showSeedDialog = true
            }
        )
    }

    // Error snackbar
    uiState.errorMessage?.let { error ->
        LaunchedEffect(error) {
            delay(3000)
            viewModel.clearError()
        }
    }

    Crossfade(
        targetState = uiState.isLoading,
        animationSpec = tween(durationMillis = 200),  // Faster transition
        label = "LoadingCrossfade"
    ) { loading ->
        if (loading) {
            LoadingScreen()
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                // Background image
                Image(
                    painter = painterResource(id = R.drawable.background_garden),
                    contentDescription = "Garden background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                // Main content
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top bar (no animation)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                    ) {
                        GardenTopBar(
                            plantName = uiState.plant?.name ?: "Garden",
                            onBackClick = onBackClick,
                            onMenuClick = { viewModel.showSettingsMenu(true) },
                            showMenu = uiState.showSettingsMenu,
                            onRenameClick = { viewModel.showRenameDialog(true) },
                            onSettingsClick = { /* Open settings */ },
                            onDismissMenu = { viewModel.showSettingsMenu(false) }
                        )
                    }

                    // Plant area with status panel and side buttons
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.45f)
                    ) {
                        // Status panel (top left) - smaller
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .width(140.dp)
                        ) {
                            uiState.plant?.let { plant ->
                                PlantStatusPanel(status = plant.status)
                            }
                        }

                        // Side buttons (right)
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 12.dp)
                        ) {
                            SideButtons(
                                onGalleryClick = { showGalleryDialog = true },
                                onShopClick = onNavigateToStore
                            )
                        }

                        // Plant display (center-bottom) - positioned on shelf in background
                        // LOCATION: GardenScreen.kt - Plant display section
                        // EDIT offset(y = XX.dp) to move plant up/down
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(top = 15.dp)
                                .offset(y = 35.dp)  // Plant position - increase to move down
                        ) {
                            uiState.plant?.let { plant ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AnimatedPlantDisplay(
                                        plant = plant,
                                        plantThought = uiState.plantThought,
                                        isExcited = uiState.showItemAnimation,
                                        modifier = Modifier.size(220.dp)
                                    )

                                    // Stage label - EDIT offset(y = XX.dp) to move label up/down
                                    PlantStageLabel(
                                        stageName = plant.stage.displayName,
                                        modifier = Modifier.offset(y = (-5).dp)
                                         // Label position
                                    )
                                }
                            }
                        }

                        // No plant message
                        if (uiState.plant == null) {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "🌱",
                                    style = MaterialTheme.typography.displayLarge
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showSeedDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text("Plant New Seed")
                                }
                            }
                        }

                        // Item use animation overlay
                        ItemUseAnimation(
                            item = uiState.animatingItem,
                            isAnimating = uiState.showItemAnimation,
                            onAnimationEnd = { /* Handled by ViewModel */ }
                        )
                    }

                    // Bottom panel - transparent background
                    Box(
                        modifier = Modifier.weight(0.55f).fillMaxSize().padding(top = 4.dp)
                    ) {
                        BottomPanel(
                            plant = uiState.plant,
                            inventory = uiState.inventory,
                            selectedTab = uiState.selectedTab,
                            onTabSelected = { viewModel.selectTab(it) },
                            onItemClick = { item ->
                                if (uiState.plant != null && !uiState.plant!!.status.isDead) {
                                    viewModel.useCareItem(item.type)
                                }
                            }
                        )
                    }
                }

                // Error snackbar
                uiState.errorMessage?.let { error ->
                    Snackbar(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                        containerColor = Color(0xFFFFEBEE)
                    ) {
                        Text(
                            text = error,
                            color = Color(0xFFC62828)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bottom panel with growth progress and care items - transparent background
 * LOCATION: GardenScreen.kt - BottomPanel function
 * EDIT padding(top = XX.dp) to move progress bar up/down
 */
@Composable
fun BottomPanel(
    plant: Plant?,
    inventory: GardenInventory,
    selectedTab: GardenTab,
    onTabSelected: (GardenTab) -> Unit,
    onItemClick: (CareItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 30.dp)  // Progress bar position - increase to move down
    ) {
        // Growth progress timer - compact
        if (plant != null && !plant.status.isDead) {
            GrowthProgressTimer(
                plant = plant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab selector
        CareTabSelector(
            selectedTab = selectedTab,
            onTabSelected = onTabSelected
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Care items grid
        val items = inventory.items.values.toList()
        
        CareItemsGrid(
            items = items,
            selectedTab = selectedTab,
            onItemClick = onItemClick,
            modifier = Modifier.weight(1f)
        )
    }
}

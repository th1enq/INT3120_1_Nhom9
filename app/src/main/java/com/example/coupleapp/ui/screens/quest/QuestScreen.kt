package com.example.coupleapp.ui.screens.quest

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.ui.components.LoadingScreen
import com.example.coupleapp.ui.components.quest.*
import com.example.coupleapp.viewmodel.QuestViewModelFirebase
import kotlinx.coroutines.delay

/**
 * Quest Screen - Daily quests for earning coins (Firebase integrated)
 * Features:
 * 1. Pinned header with progress and coins (synced with Firebase)
 * 2. Special quest for new users (link partner)
 * 3. 5 random daily quests from quest pool
 * 4. Clickable cards to navigate to quest location
 * 5. Bonus rewards for completing all quests
 * 6. Coin synchronization with Store via user_wallets collection
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestScreen(
    onBackClick: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToLocket: () -> Unit = {},
    onNavigateToMissing: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToSleep: () -> Unit = {},
    onNavigateToPartnerLink: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: QuestViewModelFirebase = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var visible by remember { mutableStateOf(false) }
    
    val scrollState = rememberLazyListState()
    
    // Animation timing
    LaunchedEffect(uiState.isLoading) {
        if (uiState.isLoading) {
            visible = false
        } else {
            if (!visible) {
                delay(50)  // Minimal delay for smooth transition
                visible = true
            }
        }
    }
    
    LaunchedEffect(Unit) {
        if (!uiState.isLoading) {
            visible = true
        }
        // Refresh quest data when screen is opened
        viewModel.refreshQuests()
    }

    // Navigate to quest location
    fun navigateToQuest(route: String?) {
        when (route) {
            "locket" -> onNavigateToLocket()
            "missing" -> onNavigateToMissing()
            "store" -> onNavigateToStore()
            "calendar" -> onNavigateToCalendar()
            "sleep_tracker" -> onNavigateToSleep()
            "link_partner" -> onNavigateToPartnerLink()
        }
    }
    
    // Reward dialog
    if (uiState.showRewardDialog && uiState.claimedReward != null) {
        RewardClaimedDialog(
            reward = uiState.claimedReward!!,
            onDismiss = { viewModel.dismissRewardDialog() }
        )
    }
    
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        Crossfade(
            targetState = uiState.isLoading,
            animationSpec = tween(durationMillis = 200),  // Faster transition
            label = "LoadingCrossfade",
            modifier = Modifier.fillMaxSize()
        ) { loading ->
            if (loading) {
                LoadingScreen(message = "Loading quests...")
            } else {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Pastel gradient background (soft pink theme)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFFFFF5F8),  // Very light pink
                                        Color(0xFFFFFBF5),  // Warm white
                                        Color(0xFFFFFAF0)   // Soft cream
                                    )
                                )
                            )
                    )
                    
                    // Background image at top 1/3 with blur effect
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.33f)
                            .align(Alignment.TopCenter)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.background_quest),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(radius = 2.dp)
                                .alpha(0.9f),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                    ) {
                        // PINNED HEADER - Top Bar (transparent, no white background)
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(300)) +
                                    slideInVertically(animationSpec = tween(300)) { -it }
                        ) {
                            QuestTopBar(
                                userCoins = uiState.userCoins,
                                currentStreak = uiState.currentStreak,
                                onBackClick = onBackClick
                            )
                        }

                        // PINNED HEADER - Daily Summary Card
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(animationSpec = tween(400, delayMillis = 100)) +
                                    slideInVertically(animationSpec = tween(400, delayMillis = 100)) { it / 4 }
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                DailySummaryCard(
                                    summary = uiState.dailySummary,
                                    todayDate = uiState.todayDate,
                                    onClaimAll = { viewModel.claimAllRewards() }
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // SCROLLABLE CONTENT - Quests list
                        LazyColumn(
                            state = scrollState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            // Special Quest (for new users - link partner)
                            if (uiState.specialQuest != null) {
                                item {
                                    AnimatedVisibility(
                                        visible = visible,
                                        enter = fadeIn(animationSpec = tween(400, delayMillis = 150)) +
                                                slideInVertically(animationSpec = tween(400, delayMillis = 150)) { it / 4 }
                                    ) {
                                        Column {
                                            QuestSectionHeader(
                                                title = "⭐ Special Quest",
                                                subtitle = "Big reward for new users"
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            SpecialQuestCard(
                                                quest = uiState.specialQuest!!,
                                                onClaimClick = { viewModel.claimReward(uiState.specialQuest!!) },
                                                onGoClick = { navigateToQuest(uiState.specialQuest!!.navigationRoute) },
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            )
                                            Spacer(modifier = Modifier.height(16.dp))
                                        }
                                    }
                                }
                            }
                            
                            // Bonus Reward Banner
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(400, delayMillis = 200)) +
                                            slideInVertically(animationSpec = tween(400, delayMillis = 200)) { it / 4 }
                                ) {
                                    BonusRewardBanner(
                                        isUnlocked = uiState.dailySummary.bonusRewardUnlocked,
                                        currentStreak = uiState.currentStreak,
                                        onClaimBonus = { viewModel.showBonusReward() }
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                            
                            // Watch Ad Section
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(400, delayMillis = 250))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        QuestSectionHeader(
                                            title = "🎬 Watch Ads",
                                            subtitle = "Get coins quickly by watching ads"
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        WatchAdButton(
                                            onClick = { viewModel.watchAd() }
                                        )
                                    }
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(20.dp)) }
                            
                            // Daily Quests Section Header
                            item {
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(400, delayMillis = 300))
                                ) {
                                    QuestSectionHeader(
                                        title = "📋 Daily Quests",
                                        subtitle = "Complete to earn coins every day"
                                    )
                                }
                            }
                            
                            // All daily quests (no tier separation)
                            items(
                                items = uiState.quests,
                                key = { it.id }
                            ) { quest ->
                                AnimatedVisibility(
                                    visible = visible,
                                    enter = fadeIn(animationSpec = tween(400, delayMillis = 350)) +
                                            slideInHorizontally(animationSpec = tween(400, delayMillis = 350)) { it / 4 }
                                ) {
                                    QuestCard(
                                        quest = quest,
                                        onClaimClick = { viewModel.claimReward(quest) },
                                        onGoClick = { navigateToQuest(quest.navigationRoute) },
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            
                            item { Spacer(modifier = Modifier.height(32.dp)) }
                        }
                    }
                }
            }
        }
        
        // FAB for inserting mock data (for testing)
        FloatingActionButton(
            onClick = { viewModel.insertMockQuestData() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Text(text = "📝", style = MaterialTheme.typography.titleLarge)
        }
        
        // Show snackbar for error/success messages
        uiState.errorMessage?.let { message ->
            LaunchedEffect(message) {
                // Message will be shown via state
                delay(3000)
                viewModel.clearError()
            }
        }
    }
}

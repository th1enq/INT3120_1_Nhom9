package com.example.coupleapp.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import kotlinx.coroutines.delay

/**
 * Profile Screen - User profile and settings
 * Features:
 * 1. User avatar and basic info
 * 2. Settings sections (Account, App, Partner, etc.)
 * 3. Logout functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToEditProfile: () -> Unit = {},
    onNavigateToChangePassword: () -> Unit = {},
    onNavigateToManageLink: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToLanguage: () -> Unit = {},
    onNavigateToHelp: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onLogout: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.PROFILE) }
    var visible by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    // Demo user data
    val userName = remember { "You" }
    val userAvatar = remember { "😊" }
    val partnerName = remember { "Partner" }
    val partnerAvatar = remember { "💕" }
    val daysTogethers = remember { 365 }
    val linkCode = remember { "ABC123" }
    
    // Handle navigation
    LaunchedEffect(selectedBottomNavItem) {
        when (selectedBottomNavItem) {
            BottomNavItem.HOME -> {
                onNavigateToHome()
                selectedBottomNavItem = BottomNavItem.PROFILE
            }
            BottomNavItem.FRIENDS -> {
                onNavigateToPartnerHub()
                selectedBottomNavItem = BottomNavItem.PROFILE
            }
            BottomNavItem.ACTIVITIES -> {
                onNavigateToMoments()
                selectedBottomNavItem = BottomNavItem.PROFILE
            }
            else -> {}
        }
    }
    
    // Animation
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    // Logout confirmation dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = "Logout",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to logout from your account?",
                    color = Color(0xFF718096)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text(
                        text = "Logout",
                        color = Color(0xFFFF6B9D),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Cancel",
                        color = Color(0xFF718096)
                    )
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp)
        )
    }
    
    Scaffold(
        bottomBar = {
            CoupleBottomNavigation(
                selectedItem = selectedBottomNavItem,
                onItemSelected = { selectedBottomNavItem = it }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF5F8),
                            Color(0xFFFFFBF5),
                            Color(0xFFFFFAF0)
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Header with avatar
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600)) +
                                slideInVertically(animationSpec = tween(600)) { -it / 4 }
                    ) {
                        ProfileHeader(
                            userName = userName,
                            userAvatar = userAvatar,
                            partnerName = partnerName,
                            partnerAvatar = partnerAvatar,
                            daysTogethe = daysTogethers,
                            linkCode = linkCode
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                // Account Settings Section
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 100)) +
                                slideInVertically(animationSpec = tween(600, delayMillis = 100)) { it / 4 }
                    ) {
                        AccountSettingsSection(
                            onEditProfileClick = onNavigateToEditProfile,
                            onChangePasswordClick = onNavigateToChangePassword,
                            onManageLinkClick = onNavigateToManageLink
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                // App Settings Section
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) +
                                slideInVertically(animationSpec = tween(600, delayMillis = 200)) { it / 4 }
                    ) {
                        AppSettingsSection(
                            onNotificationsClick = onNavigateToNotifications,
                            onLanguageClick = onNavigateToLanguage,
                            onHelpClick = onNavigateToHelp,
                            onAboutClick = onNavigateToAbout
                        )
                    }
                }
                
                item { Spacer(modifier = Modifier.height(24.dp)) }
                
                // Logout Button
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 300)) +
                                slideInVertically(animationSpec = tween(600, delayMillis = 300)) { it / 4 }
                    ) {
                        LogoutButton(onLogoutClick = { showLogoutDialog = true })
                    }
                }
                
                item { Spacer(modifier = Modifier.height(16.dp)) }
                
                // App Version
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 400))
                    ) {
                        Text(
                            text = "Couple App v1.0.0",
                            fontSize = 12.sp,
                            color = Color(0xFFB0B0B0),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    userName: String,
    userAvatar: String,
    partnerName: String,
    partnerAvatar: String,
    daysTogethe: Int,
    linkCode: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFF6B9D).copy(alpha = 0.1f),
                        Color.Transparent
                    )
                )
            )
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Couple avatars
            Row(
                horizontalArrangement = Arrangement.spacedBy((-20).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userAvatar,
                        fontSize = 40.sp
                    )
                }
                
                // Heart in middle
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .offset(y = (-20).dp)
                        .shadow(4.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B9D)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "❤️",
                        fontSize = 20.sp
                    )
                }
                
                // Partner avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = partnerAvatar,
                        fontSize = 40.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Names
            Text(
                text = "$userName & $partnerName",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D3748)
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Days together badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFFF6B9D).copy(alpha = 0.1f)
            ) {
                Text(
                    text = "💕 $daysTogethe days together",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF6B9D),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Link code
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Link code:",
                    fontSize = 12.sp,
                    color = Color(0xFF718096)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8E8E8)
                ) {
                    Text(
                        text = linkCode,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        letterSpacing = 2.sp
                    )
                }
                IconButton(
                    onClick = { /* Copy to clipboard */ },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = Color(0xFF718096),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountSettingsSection(
    onEditProfileClick: () -> Unit,
    onChangePasswordClick: () -> Unit,
    onManageLinkClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "Account",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Filled.Person,
                    title = "Edit Profile",
                    onClick = onEditProfileClick
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsItem(
                    icon = Icons.Filled.Lock,
                    title = "Change Password",
                    onClick = onChangePasswordClick
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsItem(
                    icon = Icons.Filled.Link,
                    title = "Manage Link",
                    onClick = onManageLinkClick
                )
            }
        }
    }
}

@Composable
private fun AppSettingsSection(
    onNotificationsClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onHelpClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Text(
            text = "App Settings",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF2D3748)
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 2.dp
        ) {
            Column {
                SettingsItem(
                    icon = Icons.Filled.Notifications,
                    title = "Notifications",
                    onClick = onNotificationsClick
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsItem(
                    icon = Icons.Filled.Language,
                    title = "Language",
                    subtitle = "English",
                    onClick = onLanguageClick
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsItem(
                    icon = Icons.Filled.Help,
                    title = "Help & Support",
                    onClick = onHelpClick
                )
                HorizontalDivider(color = Color(0xFFF0F0F0))
                SettingsItem(
                    icon = Icons.Filled.Info,
                    title = "About App",
                    onClick = onAboutClick
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF718096),
            modifier = Modifier.size(24.dp)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF2D3748),
            modifier = Modifier.weight(1f)
        )
        
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = Color(0xFF718096)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFB0B0B0),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun LogoutButton(onLogoutClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "scale"
    )
    
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is androidx.compose.foundation.interaction.PressInteraction.Press -> isPressed = true
                is androidx.compose.foundation.interaction.PressInteraction.Release,
                is androidx.compose.foundation.interaction.PressInteraction.Cancel -> isPressed = false
            }
        }
    }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onLogoutClick
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFEBEE)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                contentDescription = null,
                tint = Color(0xFFE53935),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Logout",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFE53935)
            )
        }
    }
}

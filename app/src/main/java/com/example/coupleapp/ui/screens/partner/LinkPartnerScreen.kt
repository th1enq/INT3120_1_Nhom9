package com.example.coupleapp.ui.screens.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.data.model.FirebaseUser
import com.example.coupleapp.ui.components.home.BottomNavItem
import com.example.coupleapp.ui.components.home.CoupleBottomNavigation
import com.example.coupleapp.ui.theme.*
import com.example.coupleapp.viewmodel.LinkPartnerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPartnerScreen(
    onBackClick: () -> Unit,
    onLinkSuccess: () -> Unit,
    onNavigateToHome: () -> Unit = {},
    onNavigateToPartnerHub: () -> Unit = {},
    onNavigateToMoments: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: LinkPartnerViewModel = viewModel()
) {
    val linkCode by viewModel.linkCode.collectAsState()
    val myLinkCode by viewModel.myLinkCode.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val foundUser by viewModel.foundUser.collectAsState()
    val error by viewModel.error.collectAsState()
    val isSendingRequest by viewModel.isSendingRequest.collectAsState()
    val requestSent by viewModel.requestSent.collectAsState()
    
    val clipboardManager = LocalClipboardManager.current
    var showCopiedMessage by remember { mutableStateOf(false) }
    var selectedBottomNavItem by remember { mutableStateOf(BottomNavItem.FRIENDS) }
    
    // Handle bottom navigation
    LaunchedEffect(selectedBottomNavItem) {
        when (selectedBottomNavItem) {
            BottomNavItem.HOME -> onNavigateToHome()
            BottomNavItem.FRIENDS -> { /* Already on partner screen */ }
            BottomNavItem.ACTIVITIES -> onNavigateToMoments()
            BottomNavItem.PROFILE -> onNavigateToProfile()
        }
    }
    
    // Animation khi gửi yêu cầu thành công
    LaunchedEffect(requestSent) {
        if (requestSent) {
            delay(2000)
            onLinkSuccess()
        }
    }
    
    // Hiển thị thông báo đã copy
    LaunchedEffect(showCopiedMessage) {
        if (showCopiedMessage) {
            delay(2000)
            showCopiedMessage = false
        }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
            // Header
            LinkPartnerHeader(onBackClick = onBackClick)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // My Link Code Section
            MyLinkCodeSection(
                myLinkCode = myLinkCode,
                onCopyClick = {
                    clipboardManager.setText(AnnotatedString(myLinkCode))
                    showCopiedMessage = true
                }
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Divider với text
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE0E0E0)
                )
                Text(
                    text = stringResource(R.string.or_text),
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = TextSecondary,
                    fontSize = 14.sp
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    color = Color(0xFFE0E0E0)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Enter Link Code Section
            EnterLinkCodeSection(
                linkCode = linkCode,
                onLinkCodeChanged = viewModel::onLinkCodeChanged,
                onSearchClick = viewModel::searchByLinkCode,
                isSearching = isSearching,
                error = error
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Found User Card
            AnimatedVisibility(
                visible = foundUser != null,
                enter = fadeIn() + slideInVertically { -it / 2 },
                exit = fadeOut() + slideOutVertically { -it / 2 }
            ) {
                foundUser?.let { user ->
                    FoundUserCard(
                        user = user,
                        isSendingRequest = isSendingRequest,
                        requestSent = requestSent,
                        onSendRequest = { viewModel.sendLinkRequest() }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
        
        // Copied Message Snackbar
        AnimatedVisibility(
            visible = showCopiedMessage,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFF2D2D2D),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.link_code_copied),
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun LinkPartnerHeader(onBackClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Quay lại",
                tint = TextPrimary
            )
        }
        
        Text(
            text = stringResource(R.string.link_partner),
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun MyLinkCodeSection(
    myLinkCode: String,
    onCopyClick: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(300)
        isVisible = true
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(500)) + 
                slideInVertically(animationSpec = tween(500)) { -it / 4 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AccentPink, SoftPink)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Mã liên kết của bạn",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.share_code_to_link),
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Link Code Display
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BackgroundWhite,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Link code characters
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        myLinkCode.forEach { char ->
                            LinkCodeChar(char = char)
                        }
                    }
                    
                    // Copy button
                    FilledTonalIconButton(
                        onClick = onCopyClick,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = PastelPink
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Sao chép",
                            tint = AccentPink
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkCodeChar(char: Char) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF5F5F5)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = char.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun EnterLinkCodeSection(
    linkCode: String,
    onLinkCodeChanged: (String) -> Unit,
    onSearchClick: () -> Unit,
    isSearching: Boolean,
    error: String?
) {
    val focusManager = LocalFocusManager.current
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.enter_partner_link_code),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Input field with visible text field
        val focusRequester = remember { FocusRequester() }
        
        LaunchedEffect(Unit) {
            delay(300)
            focusRequester.requestFocus()
        }
        
        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Visual boxes
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BackgroundWhite,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    repeat(6) { index ->
                        val char = linkCode.getOrNull(index)?.toString() ?: ""
                        LinkCodeInputBox(
                            value = char,
                            isActive = index == linkCode.length && linkCode.length < 6
                        )
                    }
                }
            }
            
            // Transparent text field overlay
            BasicTextField(
                value = linkCode,
                onValueChange = onLinkCodeChanged,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        focusManager.clearFocus()
                        onSearchClick()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .focusRequester(focusRequester),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Invisible text field - just for keyboard input
                        Box(modifier = Modifier.size(0.dp)) {
                            innerTextField()
                        }
                    }
                }
            )
        }
        
        // Error message
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            error?.let {
                Text(
                    text = it,
                    color = ErrorColor,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Search button
        Button(
            onClick = {
                focusManager.clearFocus()
                onSearchClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentPink,
                disabledContainerColor = AccentPink.copy(alpha = 0.5f)
            ),
            enabled = linkCode.length == 6 && !isSearching
        ) {
            if (isSearching) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tìm kiếm",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun LinkCodeInputBox(
    value: String,
    isActive: Boolean
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            value.isNotEmpty() -> AccentPink
            isActive -> AccentPink.copy(alpha = 0.5f)
            else -> Color(0xFFE0E0E0)
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (value.isNotEmpty()) PastelPink.copy(alpha = 0.3f) else Color(0xFFF5F5F5))
            .border(2.dp, borderColor, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
    }
}

@Composable
private fun FoundUserCard(
    user: FirebaseUser,
    isSendingRequest: Boolean,
    requestSent: Boolean,
    onSendRequest: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (requestSent) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .scale(scale),
        shape = RoundedCornerShape(20.dp),
        color = BackgroundWhite,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success checkmark or Avatar
            if (requestSent) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AccentGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = stringResource(R.string.request_sent),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen
                )
                
                Text(
                    text = stringResource(R.string.waiting_for_user, user.displayName ?: stringResource(R.string.user)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                // Avatar
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(SoftBlue, AccentBlue)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = user.displayName?.firstOrNull()?.toString() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = user.displayName ?: "Người dùng",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                
                user.bio?.takeIf { it.isNotEmpty() }?.let { bio ->
                    Text(
                        text = "\"$bio\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onSendRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPink,
                        disabledContainerColor = AccentPink.copy(alpha = 0.5f)
                    ),
                    enabled = !isSendingRequest
                ) {
                    if (isSendingRequest) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gửi yêu cầu liên kết",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

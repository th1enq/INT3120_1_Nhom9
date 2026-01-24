package com.example.coupleapp.ui.screens.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Help Screen - Provides help and support options
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    var expandedFaq by remember { mutableStateOf<Int?>(null) }
    
    val faqs = listOf(
        Pair(
            stringResource(R.string.faq_link_partner),
            stringResource(R.string.faq_link_partner_answer)
        ),
        Pair(
            stringResource(R.string.faq_send_locket),
            stringResource(R.string.faq_send_locket_answer)
        ),
        Pair(
            stringResource(R.string.faq_quests),
            stringResource(R.string.faq_quests_answer)
        ),
        Pair(
            stringResource(R.string.faq_sleep_tracking),
            stringResource(R.string.faq_sleep_tracking_answer)
        ),
        Pair(
            stringResource(R.string.faq_change_code),
            stringResource(R.string.faq_change_code_answer)
        ),
        Pair(
            stringResource(R.string.faq_unlink),
            stringResource(R.string.faq_unlink_answer)
        )
    )
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.help_support),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color(0xFF2D3748)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
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
                    .padding(paddingValues)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Contact section
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600)) +
                                slideInVertically(animationSpec = tween(600)) { it / 4 }
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.contact_us),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF718096),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Column {
                                    HelpContactItem(
                                        icon = Icons.Filled.Email,
                                        title = stringResource(R.string.email_support),
                                        subtitle = stringResource(R.string.support_email),
                                        onClick = { }
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                    HelpContactItem(
                                        icon = Icons.Filled.Chat,
                                        title = stringResource(R.string.live_chat),
                                        subtitle = stringResource(R.string.live_chat_desc),
                                        onClick = { }
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                    HelpContactItem(
                                        icon = Icons.Filled.BugReport,
                                        title = stringResource(R.string.report_bug),
                                        subtitle = stringResource(R.string.report_bug_desc),
                                        onClick = { }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // FAQ section
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 100)) +
                                slideInVertically(animationSpec = tween(600, delayMillis = 100)) { it / 4 }
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.faq),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF718096),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Column {
                                    faqs.forEachIndexed { index, (question, answer) ->
                                        FaqItem(
                                            question = question,
                                            answer = answer,
                                            isExpanded = expandedFaq == index,
                                            onClick = {
                                                expandedFaq = if (expandedFaq == index) null else index
                                            }
                                        )
                                        if (index < faqs.lastIndex) {
                                            HorizontalDivider(color = Color(0xFFF0F0F0))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Feedback section
                item {
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) +
                                slideInVertically(animationSpec = tween(600, delayMillis = 200)) { it / 4 }
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.feedback),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF718096),
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White,
                                shadowElevation = 2.dp
                            ) {
                                Column {
                                    HelpContactItem(
                                        icon = Icons.Filled.Star,
                                        title = stringResource(R.string.rate_us),
                                        subtitle = stringResource(R.string.rate_us_desc),
                                        onClick = { }
                                    )
                                    HorizontalDivider(color = Color(0xFFF0F0F0))
                                    HelpContactItem(
                                        icon = Icons.Filled.Feedback,
                                        title = stringResource(R.string.send_feedback),
                                        subtitle = stringResource(R.string.send_feedback_desc),
                                        onClick = { }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HelpContactItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFF6B9D).copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFFF6B9D),
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2D3748)
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF718096)
            )
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
private fun FaqItem(
    question: String,
    answer: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.QuestionAnswer,
                contentDescription = null,
                tint = Color(0xFF718096),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = question,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF2D3748),
                modifier = Modifier.weight(1f)
            )
            
            Icon(
                imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = Color(0xFF718096),
                modifier = Modifier.size(24.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = answer,
                fontSize = 13.sp,
                color = Color(0xFF718096),
                modifier = Modifier.padding(top = 12.dp, start = 32.dp),
                lineHeight = 20.sp
            )
        }
    }
}

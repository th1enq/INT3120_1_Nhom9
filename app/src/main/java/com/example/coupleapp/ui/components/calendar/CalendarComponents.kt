package com.example.coupleapp.ui.components.calendar

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.coupleapp.R
import com.example.coupleapp.util.createImageLoaderWithBase64Support

/**
 * Background image for Calendar screen
 */
@Composable
fun BackgroundImage(
    imageUrl: String?,
    useDefault: Boolean
) {
    val context = LocalContext.current
    val imageLoader = remember { createImageLoaderWithBase64Support(context) }
    
    if (useDefault || imageUrl.isNullOrEmpty()) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.background_counter),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else {
        // Load custom background image from URL or base64
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            imageLoader = imageLoader,
            contentDescription = "Calendar Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            error = painterResource(id = R.drawable.background_counter),
            placeholder = painterResource(id = R.drawable.background_counter)
        )
    }
}

/**
 * Top bar with back, calendar and settings buttons
 */
@Composable
fun CalendarTopBar(
    onBackClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button with emoji
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(alpha = 0.9f), CircleShape)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "←",
                fontSize = 24.sp,
                color = Color(0xFFFF6B9D)
            )
        }
        
        // Action buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Calendar button with emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .clickable(onClick = onCalendarClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📅",
                    fontSize = 20.sp
                )
            }
            
            // Settings button with emoji
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.9f), CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚙️",
                    fontSize = 20.sp
                )
            }
        }
    }
}

/**
 * Circular love days counter display
 */
@Composable
fun CircleLoveDaysDisplay(totalDays: Long) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    
    Box(
        modifier = Modifier.size(260.dp),
        contentAlignment = Alignment.Center
    ) {
        // Background Circle
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20.dp.toPx()
            drawCircle(
                color = Color(0xFFFFE8F5),
                radius = size.minDimension / 2 - strokeWidth / 2,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth
                )
            )
        }
        
        // Inner glow
        Canvas(
            modifier = Modifier.fillMaxSize(0.85f)
        ) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFD6E8).copy(alpha = 0.3f * pulseScale),
                        Color.Transparent
                    )
                ),
                radius = size.minDimension / 2
            )
        }
        
        // Center Content - Only number
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = totalDays.toString(),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF6B9D),
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = stringResource(R.string.day_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF757575),
                letterSpacing = 3.sp
            )
        }
    }
}

/**
 * Square love days counter display
 */
@Composable
fun SquareLoveDaysDisplay(
    years: Int,
    months: Int,
    days: Int
) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(160.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFE8F5),
                            Color.White
                        )
                    )
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.we_have_loved),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF757575)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                if (years > 0) {
                    TimeBox(value = years, unit = "Năm", color = Color(0xFFFF6B9D))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                if (months > 0) {
                    TimeBox(value = months, unit = "Tháng", color = Color(0xFF9ED9FF))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                TimeBox(value = days, unit = "Ngày", color = Color(0xFF98E4C8))
            }
        }
    }
}

/**
 * Time box for square display
 */
@Composable
private fun TimeBox(value: Int, unit: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = value.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = unit,
            fontSize = 12.sp,
            color = Color(0xFF757575)
        )
    }
}

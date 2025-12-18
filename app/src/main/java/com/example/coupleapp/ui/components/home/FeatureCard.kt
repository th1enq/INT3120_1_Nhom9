package com.example.coupleapp.ui.components.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R

data class FeatureItem(
    val imageRes: Int,
    val titleResId: Int,
    val iconTint: Color,
    val navigateTo: String
)

@Composable
fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val title = stringResource(feature.titleResId)
    
    // Simplified animation for better performance
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100), // Fast, simple animation
        label = "featureScale"
    )
    
    Column(
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Image with circular shape
        Image(
            painter = painterResource(id = feature.imageRes),
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            ),
            color = Color(0xFF2D2D2D),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FeaturesRow(
    onFeatureClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val features = listOf(
        FeatureItem(
            imageRes = R.drawable.store,
            titleResId = R.string.feature_store,
            iconTint = Color(0xFFFF6B9D),
            navigateTo = "Store"
        ),
        FeatureItem(
            imageRes = R.drawable.calendar_button,
            titleResId = R.string.feature_calendar,
            iconTint = Color(0xFFFFB74D),
            navigateTo = "Calendar"
        ),
        FeatureItem(
            imageRes = R.drawable.quest,
            titleResId = R.string.feature_quest,
            iconTint = Color(0xFF66BB6A),
            navigateTo = "Quest"
        ),
        FeatureItem(
            imageRes = R.drawable.garden,
            titleResId = R.string.feature_garden,
            iconTint = Color(0xFF9C27B0),
            navigateTo = "Garden"
        )
    )
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        features.forEach { feature ->
            FeatureCard(
                feature = feature,
                onClick = { onFeatureClick(feature.navigateTo) }
            )
        }
    }
}

package com.example.coupleapp.ui.components.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R

data class WidgetItem(
    val titleResId: Int,
    val descriptionResId: Int,
    val imageRes: Int,
    val gradient: Brush,
    val navigateTo: String
)

@Composable
fun WidgetCard(
    widget: WidgetItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val title = stringResource(widget.titleResId)
    val description = stringResource(widget.descriptionResId)
    
    // Simplified animation for better performance
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(100), // Fast, simple animation
        label = "widgetScale"
    )
    
    Box(
        modifier = modifier
            .scale(scale)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(widget.gradient)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Title and description
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    color = Color(0xFF2D2D2D)
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = Color(0xFF757575),
                    maxLines = 2
                )
            }
            
            // Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.BottomEnd
            ) {
                Image(
                    painter = painterResource(id = widget.imageRes),
                    contentDescription = null,
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun WidgetsGrid(
    onWidgetClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val widgets = listOf(
        WidgetItem(
            titleResId = R.string.feature_sleep,
            descriptionResId = R.string.widget_sleep_desc,
            imageRes = R.drawable.sleep_widget,
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE8D6FF),
                    Color(0xFFF0E8FF)
                )
            ),
            navigateTo = "Sleep"
        ),
        WidgetItem(
            titleResId = R.string.feature_locket,
            descriptionResId = R.string.widget_locket_desc,
            imageRes = R.drawable.locket,
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFF5E8),
                    Color(0xFFFFFAF0)
                )
            ),
            navigateTo = "Locket"
        ),
        WidgetItem(
            titleResId = R.string.feature_missing,
            descriptionResId = R.string.widget_missing_desc,
            imageRes = R.drawable.missing,
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFE8F0),
                    Color(0xFFFFF0F5)
                )
            ),
            navigateTo = "Missing"
        ),
        WidgetItem(
            titleResId = R.string.feature_location,
            descriptionResId = R.string.widget_location_desc,
            imageRes = R.drawable.distance,
            gradient = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFE8F5FF),
                    Color(0xFFF0F8FF)
                )
            ),
            navigateTo = "Location"
        )
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Row 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WidgetCard(
                widget = widgets[0],
                onClick = { onWidgetClick(widgets[0].navigateTo) },
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                widget = widgets[1],
                onClick = { onWidgetClick(widgets[1].navigateTo) },
                modifier = Modifier.weight(1f)
            )
        }
        
        // Row 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WidgetCard(
                widget = widgets[2],
                onClick = { onWidgetClick(widgets[2].navigateTo) },
                modifier = Modifier.weight(1f)
            )
            WidgetCard(
                widget = widgets[3],
                onClick = { onWidgetClick(widgets[3].navigateTo) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

package com.example.coupleapp.ui.components.store

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.SeedRarity

/**
 * Rarity badge for seed items (Normal, Rare, Super Rare)
 */
@Composable
fun RarityBadge(
    rarity: SeedRarity,
    modifier: Modifier = Modifier
) {
    val (color, bgColor) = when (rarity) {
        SeedRarity.NORMAL -> Color(0xFF4CAF50) to Color(0xFFC8E6C9)
        SeedRarity.RARE -> Color(0xFF2196F3) to Color(0xFFBBDEFB)
        SeedRarity.SUPER_RARE -> Color(0xFF9C27B0) to Color(0xFFE1BEE7)
    }

    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-6).dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(bgColor)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when (rarity) {
                SeedRarity.NORMAL -> "N"
                SeedRarity.RARE -> "R"
                SeedRarity.SUPER_RARE -> "S"
            },
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Duration badge for fertilizer items showing hours
 */
@Composable
fun DurationBadge(
    hours: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .offset(x = 6.dp, y = (-6).dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF2196F3))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${hours}h",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

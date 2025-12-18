package com.example.coupleapp.ui.components.store

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.R
import com.example.coupleapp.data.model.PurchaseType
import com.example.coupleapp.data.model.StoreItem

/**
 * Price button component for store items
 */
@Composable
fun PriceButton(
    item: StoreItem,
    userCoins: Int,
    canClaimFree: Boolean,
    cooldownDays: Int
) {
    val (buttonColor, textColor, text, iconType) = when (item.purchaseType) {
        PurchaseType.FREE_DAILY -> {
            if (canClaimFree) {
                Quadruple(
                    Brush.horizontalGradient(listOf(Color(0xFF81C784), Color(0xFF66BB6A))),
                    Color.White,
                    "Claim",
                    "FREE"
                )
            } else {
                Quadruple(
                    Brush.horizontalGradient(listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD))),
                    Color.White,
                    "~${cooldownDays}d",
                    "TIME"
                )
            }
        }
        PurchaseType.WATCH_AD -> Quadruple(
            Brush.horizontalGradient(listOf(Color(0xFFFF6F00), Color(0xFFFF8F00))),
            Color.White,
            "Watch",
            "AD"
        )
        PurchaseType.COIN -> {
            val canAfford = userCoins >= item.coinPrice
            Quadruple(
                if (canAfford)
                    Brush.horizontalGradient(listOf(Color(0xFFFFD54F), Color(0xFFFFCA28)))
                else
                    Brush.horizontalGradient(listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD))),
                Color.White,
                "${item.coinPrice}",
                "COIN"
            )
        }
        PurchaseType.REAL_MONEY -> Quadruple(
            Brush.horizontalGradient(listOf(Color(0xFF42A5F5), Color(0xFF1E88E5))),
            Color.White,
            "$${String.format("%.0f", item.realPrice / 1000)}k",
            "MONEY"
        )
    }

    Box(
        modifier = Modifier
            .width(90.dp)
            .height(36.dp)
            .shadow(2.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(buttonColor)
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.3f),
                shape = RoundedCornerShape(18.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            // Icon based on type
            when (iconType) {
                "COIN" -> {
                    Canvas(modifier = Modifier.size(16.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                            )
                        )
                        drawCircle(
                            color = Color(0xFFB8860B),
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                "AD" -> {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                "FREE" -> {
                    // Sparkle or star icon could go here
                }
                "MONEY" -> {
                    // Money/dollar icon
                }
            }

            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

/**
 * Price information for coin purchases in dialog
 */
@Composable
fun CoinPriceInfo(
    unitPrice: Int,
    quantity: Int,
    totalCost: Int,
    userCoins: Int
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Unit price
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.unit_price),
                fontSize = 13.sp,
                color = Color(0xFF8D6E63)
            )
            Canvas(modifier = Modifier.size(16.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                    )
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$unitPrice",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037)
            )
        }
        
        // Total cost
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.total),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037)
            )
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFFD700), Color(0xFFDAA520))
                    )
                )
                drawCircle(
                    color = Color(0xFFB8860B),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$totalCost",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5D4037)
            )
        }
        
        // Balance
        Text(
            text = stringResource(R.string.your_balance, userCoins),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (userCoins >= totalCost) Color(0xFF4CAF50) else Color(0xFFF44336)
        )
    }
}

/**
 * Free gift price info
 */
@Composable
fun FreePriceInfo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "🎁 Free Gift!",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
    }
}

/**
 * Cooldown info display
 */
@Composable
fun CooldownInfo(cooldownDays: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "⏳ On Cooldown",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9E9E9E)
        )
        Text(
            text = stringResource(R.string.available_in_days, cooldownDays),
            fontSize = 13.sp,
            color = Color(0xFFBDBDBD)
        )
    }
}

/**
 * Ad price info display
 */
@Composable
fun AdPriceInfo() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "📺 Watch Ad to Claim",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF9C27B0)
        )
    }
}

/**
 * Real money price info display
 */
@Composable
fun RealMoneyPriceInfo(price: Double) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "💎 Premium Purchase",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E88E5)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$$${String.format("%.2f", price / 1000)}",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1976D2)
        )
    }
}

// Helper data class for quadruple values
internal data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

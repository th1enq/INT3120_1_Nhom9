package com.example.coupleapp.ui.components.quest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.coupleapp.R
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coupleapp.data.model.Quest
import com.example.coupleapp.data.model.QuestStatus
import java.util.Locale

/**
 * Special quest card for new users (e.g., Link partner)
 * Compact design - clickable card without separate Go button
 */
@Composable
fun SpecialQuestCard(
    quest: Quest,
    onClaimClick: () -> Unit,
    onGoClick: () -> Unit,
    modifier: Modifier = Modifier,
    isClaimingReward: Boolean = false
) {
    val gradientColors = listOf(
        Color(0xFFFFB8D6),
        Color(0xFFFF9ECE),
        Color(0xFFE8B5FF)
    )
    
    // Only clickable for navigation when NOT completed and NOT claimed
    val canNavigate = quest.status != QuestStatus.CLAIMED && quest.status != QuestStatus.COMPLETED
    val isVietnamese = Locale.getDefault().language == "vi"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canNavigate) Modifier.clickable { onGoClick() }
                else Modifier
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(gradientColors))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Content column - takes most space
                Column(modifier = Modifier.weight(1f)) {
                    // Special badge
                    Text(
                        text = if (isVietnamese) "⭐ Nhiệm vụ đặc biệt" else "⭐ Special Quest",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Title
                    Text(
                        text = quest.getLocalizedTitle(isVietnamese),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Description
                    Text(
                        text = quest.getLocalizedDescription(isVietnamese),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 13.sp
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Reward preview
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.25f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MonetizationOn,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "+${quest.reward.coins}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                        if (quest.reward.bonusItem != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "🎁",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Action section
                when (quest.status) {
                    QuestStatus.CLAIMED -> {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Completed",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    QuestStatus.COMPLETED -> {
                        Button(
                            onClick = onClaimClick,
                            enabled = !isClaimingReward,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.7f)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            if (isClaimingReward) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFFFF9ECE),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.claim),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFFF9ECE)
                                )
                            }
                        }
                    }
                    else -> {
                        // Arrow indicator for clickable card
                        Text(
                            text = stringResource(R.string.go_arrow),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

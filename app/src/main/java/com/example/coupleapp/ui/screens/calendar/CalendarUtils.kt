package com.example.coupleapp.ui.screens.calendar

import androidx.compose.ui.graphics.Color
import com.example.coupleapp.data.model.AnniversaryType
import com.example.coupleapp.ui.theme.*

/**
 * Get color for event type
 */
fun getEventColor(type: AnniversaryType): Color {
    return when (type) {
        AnniversaryType.RELATIONSHIP_START -> AccentPink
        AnniversaryType.FIRST_MEET -> SoftPink
        AnniversaryType.FIRST_DATE -> PastelPink
        AnniversaryType.ENGAGEMENT -> SoftLavender
        AnniversaryType.WEDDING -> AccentPink
        AnniversaryType.BIRTHDAY -> AccentBlue
        AnniversaryType.TRIP -> AccentGreen
        AnniversaryType.SPECIAL_MOMENT -> SoftMint
        AnniversaryType.CUSTOM -> TextSecondary
    }
}

package com.example.coupleapp.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.coupleapp.data.model.LocationType

/**
 * Utility functions for location-related UI elements
 * Centralized to avoid duplication across components
 */
object LocationUtils {
    
    /**
     * Get the icon for a location type
     */
    fun getLocationTypeIcon(type: LocationType): ImageVector {
        return when (type) {
            LocationType.HOME -> Icons.Filled.Home
            LocationType.WORK -> Icons.Filled.Work
            LocationType.CAFE -> Icons.Filled.LocalCafe
            LocationType.RESTAURANT -> Icons.Filled.Restaurant
            LocationType.SHOPPING -> Icons.Filled.ShoppingBag
            LocationType.GYM -> Icons.Filled.FitnessCenter
            LocationType.PARK -> Icons.Filled.Park
            LocationType.ENTERTAINMENT -> Icons.Filled.TheaterComedy
            LocationType.SCHOOL -> Icons.Filled.School
            LocationType.HOSPITAL -> Icons.Filled.LocalHospital
            LocationType.OTHER -> Icons.Filled.Place
        }
    }
    
    /**
     * Get the color for a location type
     */
    fun getLocationTypeColor(type: LocationType): Color {
        return when (type) {
            LocationType.HOME -> Color(0xFFFF9ECE)
            LocationType.WORK -> Color(0xFF9ED9FF)
            LocationType.CAFE -> Color(0xFFD4A574)
            LocationType.RESTAURANT -> Color(0xFFFF8A65)
            LocationType.SHOPPING -> Color(0xFFE0B8FF)
            LocationType.GYM -> Color(0xFF81C784)
            LocationType.PARK -> Color(0xFF98E4C8)
            LocationType.ENTERTAINMENT -> Color(0xFFFFD54F)
            LocationType.SCHOOL -> Color(0xFF64B5F6)
            LocationType.HOSPITAL -> Color(0xFFEF5350)
            LocationType.OTHER -> Color(0xFFB0B0B0)
        }
    }
    
    /**
     * Get the emoji for a location type
     */
    fun getLocationTypeEmoji(type: LocationType): String {
        return when (type) {
            LocationType.HOME -> "🏠"
            LocationType.WORK -> "💼"
            LocationType.CAFE -> "☕"
            LocationType.RESTAURANT -> "🍽️"
            LocationType.SHOPPING -> "🛍️"
            LocationType.GYM -> "💪"
            LocationType.PARK -> "🌳"
            LocationType.ENTERTAINMENT -> "🎬"
            LocationType.SCHOOL -> "📚"
            LocationType.HOSPITAL -> "🏥"
            LocationType.OTHER -> "📍"
        }
    }
}

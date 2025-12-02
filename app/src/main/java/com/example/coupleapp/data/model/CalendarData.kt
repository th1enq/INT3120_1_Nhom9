package com.example.coupleapp.data.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Represents the couple's relationship data
 */
data class CoupleProfile(
    val user1: CalendarUserProfile,
    val user2: CalendarUserProfile,
    val relationshipStartDate: LocalDateTime,
    val backgroundImageUrl: String = ""
)

/**
 * Represents individual user profile for calendar
 */
data class CalendarUserProfile(
    val id: String,
    val name: String,
    val nickname: String,
    val avatarUrl: String,
    val dateOfBirth: LocalDate,
    val zodiacSign: ZodiacSign
) {
    val age: Int
        get() {
            val today = LocalDate.now()
            var age = today.year - dateOfBirth.year
            if (today.monthValue < dateOfBirth.monthValue ||
                (today.monthValue == dateOfBirth.monthValue && today.dayOfMonth < dateOfBirth.dayOfMonth)
            ) {
                age--
            }
            return age
        }
}

/**
 * Zodiac signs
 */
enum class ZodiacSign(val displayName: String, val symbol: String) {
    ARIES("Bạch Dương", "♈"),
    TAURUS("Kim Ngưu", "♉"),
    GEMINI("Song Tử", "♊"),
    CANCER("Cự Giải", "♋"),
    LEO("Sư Tử", "♌"),
    VIRGO("Xử Nữ", "♍"),
    LIBRA("Thiên Bình", "♎"),
    SCORPIO("Bọ Cạp", "♏"),
    SAGITTARIUS("Nhân Mã", "♐"),
    CAPRICORN("Ma Kết", "♑"),
    AQUARIUS("Bảo Bình", "♒"),
    PISCES("Song Ngư", "♓");

    companion object {
        fun fromDate(date: LocalDate): ZodiacSign {
            return when (date.monthValue) {
                1 -> if (date.dayOfMonth < 20) CAPRICORN else AQUARIUS
                2 -> if (date.dayOfMonth < 19) AQUARIUS else PISCES
                3 -> if (date.dayOfMonth < 21) PISCES else ARIES
                4 -> if (date.dayOfMonth < 20) ARIES else TAURUS
                5 -> if (date.dayOfMonth < 21) TAURUS else GEMINI
                6 -> if (date.dayOfMonth < 21) GEMINI else CANCER
                7 -> if (date.dayOfMonth < 23) CANCER else LEO
                8 -> if (date.dayOfMonth < 23) LEO else VIRGO
                9 -> if (date.dayOfMonth < 23) VIRGO else LIBRA
                10 -> if (date.dayOfMonth < 23) LIBRA else SCORPIO
                11 -> if (date.dayOfMonth < 22) SCORPIO else SAGITTARIUS
                12 -> if (date.dayOfMonth < 22) SAGITTARIUS else CAPRICORN
                else -> ARIES
            }
        }
    }
}

/**
 * Represents an anniversary or special event
 */
data class Anniversary(
    val id: String,
    val title: String,
    val description: String = "",
    val date: LocalDateTime,
    val type: AnniversaryType,
    val isRecurring: Boolean = false,
    val reminderEnabled: Boolean = true,
    val imageUrl: String = ""
)

/**
 * Types of anniversaries
 */
enum class AnniversaryType(val displayName: String, val emoji: String) {
    FIRST_MEET("Lần đầu gặp", "👫"),
    FIRST_DATE("Hẹn hò đầu tiên", "💕"),
    RELATIONSHIP_START("Ngày yêu nhau", "❤️"),
    ENGAGEMENT("Đính hôn", "💍"),
    WEDDING("Kết hôn", "💒"),
    BIRTHDAY("Sinh nhật", "🎂"),
    TRIP("Chuyến đi", "✈️"),
    SPECIAL_MOMENT("Khoảnh khắc đặc biệt", "⭐"),
    CUSTOM("Tùy chỉnh", "📅")
}

/**
 * Calendar event for UI display
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val type: AnniversaryType,
    val daysUntil: Long
)

/**
 * Love days counter data
 */
data class LoveDaysCounter(
    val totalDays: Long,
    val years: Int,
    val months: Int,
    val days: Int,
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val progressPercentage: Float = 0f
)

/**
 * Calendar view mode
 */
enum class CalendarViewMode {
    CIRCLE_COUNTER,  // Main circular progress view
    GRID_CALENDAR    // Grid calendar view with dates
}

/**
 * Settings for calendar background and preferences
 */
data class CalendarSettings(
    val backgroundImageUrl: String = "",
    val useDefaultBackground: Boolean = true,
    val showHeartbeatAnimation: Boolean = true,
    val reminderHoursBefore: Int = 24
)

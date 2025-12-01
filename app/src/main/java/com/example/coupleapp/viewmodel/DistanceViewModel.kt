package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.coupleapp.data.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

class DistanceViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(DistanceUiState())
    val uiState: StateFlow<DistanceUiState> = _uiState.asStateFlow()
    
    private val _photosState = MutableStateFlow(SharedPlacePhotosState())
    val photosState: StateFlow<SharedPlacePhotosState> = _photosState.asStateFlow()
    
    init {
        loadInitialData()
    }
    
    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Simulate API call delay
            delay(1200)
            
            val myLocation = getMockMyLocation()
            val partnerLocation = getMockPartnerLocation()
            val distance = calculateDistance(
                myLocation.coordinate,
                partnerLocation.coordinate
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    myLocation = myLocation,
                    partnerLocation = partnerLocation,
                    distanceInMeters = distance,
                    distanceText = formatDistance(distance),
                    lastSyncTime = formatLastSync(LocalDateTime.now()),
                    myLocationHistory = getMockMyLocationHistory(),
                    partnerLocationHistory = getMockPartnerLocationHistory(),
                    sharedPlaces = getMockSharedPlaces()
                )
            }
        }
    }
    
    fun refreshLocations() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            delay(800)
            
            val myLocation = getMockMyLocation()
            val partnerLocation = getMockPartnerLocation()
            val distance = calculateDistance(
                myLocation.coordinate,
                partnerLocation.coordinate
            )
            
            _uiState.update {
                it.copy(
                    isLoading = false,
                    myLocation = myLocation,
                    partnerLocation = partnerLocation,
                    distanceInMeters = distance,
                    distanceText = formatDistance(distance),
                    lastSyncTime = formatLastSync(LocalDateTime.now())
                )
            }
        }
    }
    
    fun selectUser(user: UserLocation?) {
        _uiState.update {
            it.copy(
                selectedUser = user,
                showUserInfoSheet = user != null
            )
        }
    }
    
    fun dismissUserInfoSheet() {
        _uiState.update {
            it.copy(
                showUserInfoSheet = false,
                selectedUser = null
            )
        }
    }
    
    fun showSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }
    
    fun dismissSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }
    
    fun loadPlacePhotos(placeId: String) {
        viewModelScope.launch {
            _photosState.update { it.copy(isLoading = true) }
            delay(600)
            
            val place = _uiState.value.sharedPlaces.find { it.id == placeId }
            val photos = getMockPhotosForPlace(placeId)
            
            _photosState.update {
                it.copy(
                    isLoading = false,
                    place = place,
                    photos = photos
                )
            }
        }
    }
    
    fun addPhotoToPlace(placeId: String, photoUrl: String) {
        viewModelScope.launch {
            _photosState.update { it.copy(isAddingPhoto = true) }
            delay(500)
            
            val newPhoto = SharedPlacePhoto(
                id = "photo_${System.currentTimeMillis()}",
                photoUrl = photoUrl,
                takenAt = LocalDateTime.now(),
                takenByUserId = "user_me",
                caption = null
            )
            
            _photosState.update {
                it.copy(
                    isAddingPhoto = false,
                    photos = it.photos + newPhoto
                )
            }
        }
    }
    
    // Helper functions
    private fun calculateDistance(coord1: LocationCoordinate, coord2: LocationCoordinate): Double {
        val earthRadius = 6371000.0 // meters
        
        val lat1Rad = Math.toRadians(coord1.latitude)
        val lat2Rad = Math.toRadians(coord2.latitude)
        val deltaLat = Math.toRadians(coord2.latitude - coord1.latitude)
        val deltaLon = Math.toRadians(coord2.longitude - coord1.longitude)
        
        val a = sin(deltaLat / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    private fun formatDistance(meters: Double): String {
        return when {
            meters < 1000 -> "${meters.toInt()} m"
            meters < 10000 -> String.format("%.1f km", meters / 1000)
            else -> String.format("%.0f km", meters / 1000)
        }
    }
    
    private fun formatLastSync(dateTime: LocalDateTime): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        return "Updated ${dateTime.format(formatter)}"
    }
    
    // Mock Data
    private fun getMockMyLocation(): UserLocation {
        return UserLocation(
            userId = "user_me",
            userName = "Anh 🌸",
            avatarUrl = "avatar_me",
            coordinate = LocationCoordinate(
                latitude = 21.0285,  // Near Keangnam Landmark
                longitude = 105.7823
            ),
            address = "Keangnam Landmark 72, Phạm Hùng, Mễ Trì, Nam Từ Liêm",
            lastUpdated = LocalDateTime.now().minusMinutes(5),
            batteryLevel = 82,
            isOnline = true
        )
    }
    
    private fun getMockPartnerLocation(): UserLocation {
        return UserLocation(
            userId = "user_partner",
            userName = "Em 🍋",
            avatarUrl = "avatar_partner",
            coordinate = LocationCoordinate(
                latitude = 21.0380,  // Dịch Vọng area
                longitude = 105.7880
            ),
            address = "Dịch Vọng, Cầu Giấy, Hà Nội",
            lastUpdated = LocalDateTime.now().minusMinutes(2),
            batteryLevel = 65,
            isOnline = true
        )
    }
    
    private fun getMockMyLocationHistory(): List<LocationHistory> {
        val now = LocalDateTime.now()
        return listOf(
            LocationHistory(
                id = "loc_1",
                locationName = "Home",
                address = "123 Nguyễn Trãi, Thanh Xuân",
                coordinate = LocationCoordinate(21.0033, 105.8000),
                arrivalTime = now.minusHours(10),
                departureTime = now.minusHours(8),
                durationMinutes = 120,
                locationType = LocationType.HOME
            ),
            LocationHistory(
                id = "loc_2",
                locationName = "The Coffee House",
                address = "30 Láng Hạ, Đống Đa",
                coordinate = LocationCoordinate(21.0178, 105.8199),
                arrivalTime = now.minusHours(7),
                departureTime = now.minusHours(6),
                durationMinutes = 60,
                locationType = LocationType.CAFE
            ),
            LocationHistory(
                id = "loc_3",
                locationName = "Vincom Center",
                address = "191 Bà Triệu, Hai Bà Trưng",
                coordinate = LocationCoordinate(21.0122, 105.8498),
                arrivalTime = now.minusHours(5),
                departureTime = now.minusHours(3),
                durationMinutes = 120,
                locationType = LocationType.SHOPPING
            ),
            LocationHistory(
                id = "loc_4",
                locationName = "Công viên Cầu Giấy",
                address = "Dịch Vọng, Cầu Giấy",
                coordinate = LocationCoordinate(21.0350, 105.7950),
                arrivalTime = now.minusHours(2),
                departureTime = now.minusHours(1),
                durationMinutes = 60,
                locationType = LocationType.PARK
            ),
            LocationHistory(
                id = "loc_5",
                locationName = "Keangnam Landmark",
                address = "Phạm Hùng, Mễ Trì",
                coordinate = LocationCoordinate(21.0285, 105.7823),
                arrivalTime = now.minusMinutes(30),
                departureTime = null,
                durationMinutes = 30,
                locationType = LocationType.WORK
            )
        )
    }
    
    private fun getMockPartnerLocationHistory(): List<LocationHistory> {
        val now = LocalDateTime.now()
        return listOf(
            LocationHistory(
                id = "ploc_1",
                locationName = "Home",
                address = "45 Kim Mã, Ba Đình",
                coordinate = LocationCoordinate(21.0305, 105.8270),
                arrivalTime = now.minusHours(12),
                departureTime = now.minusHours(9),
                durationMinutes = 180,
                locationType = LocationType.HOME
            ),
            LocationHistory(
                id = "ploc_2",
                locationName = "Đại học Bách khoa",
                address = "1 Đại Cồ Việt, Hai Bà Trưng",
                coordinate = LocationCoordinate(21.0053, 105.8428),
                arrivalTime = now.minusHours(8),
                departureTime = now.minusHours(4),
                durationMinutes = 240,
                locationType = LocationType.SCHOOL
            ),
            LocationHistory(
                id = "ploc_3",
                locationName = "Highlands Coffee",
                address = "Tầng 1, Vincom Bà Triệu",
                coordinate = LocationCoordinate(21.0125, 105.8495),
                arrivalTime = now.minusHours(3),
                departureTime = now.minusHours(2),
                durationMinutes = 60,
                locationType = LocationType.CAFE
            ),
            LocationHistory(
                id = "ploc_4",
                locationName = "Dịch Vọng",
                address = "Dịch Vọng, Cầu Giấy, Hà Nội",
                coordinate = LocationCoordinate(21.0380, 105.7880),
                arrivalTime = now.minusMinutes(45),
                departureTime = null,
                durationMinutes = 45,
                locationType = LocationType.OTHER
            )
        )
    }
    
    private fun getMockSharedPlaces(): List<SharedPlace> {
        return listOf(
            SharedPlace(
                id = "shared_1",
                placeName = "Lotte Center Hà Nội",
                address = "54 Liễu Giai, Ba Đình",
                coordinate = LocationCoordinate(21.0296, 105.8132),
                representativePhotoUrl = "shared_place_1",
                visitDate = LocalDateTime.now().minusDays(3),
                durationMinutes = 180,
                photosCount = 12,
                locationType = LocationType.SHOPPING
            ),
            SharedPlace(
                id = "shared_2",
                placeName = "Hồ Tây",
                address = "Hồ Tây, Tây Hồ",
                coordinate = LocationCoordinate(21.0533, 105.8210),
                representativePhotoUrl = "shared_place_2",
                visitDate = LocalDateTime.now().minusDays(5),
                durationMinutes = 120,
                photosCount = 8,
                locationType = LocationType.PARK
            ),
            SharedPlace(
                id = "shared_3",
                placeName = "CGV Vincom Royal City",
                address = "72A Nguyễn Trãi, Thanh Xuân",
                coordinate = LocationCoordinate(21.0010, 105.8156),
                representativePhotoUrl = "shared_place_3",
                visitDate = LocalDateTime.now().minusDays(7),
                durationMinutes = 150,
                photosCount = 5,
                locationType = LocationType.ENTERTAINMENT
            ),
            SharedPlace(
                id = "shared_4",
                placeName = "Phố đi bộ Hồ Gươm",
                address = "Hoàn Kiếm, Hà Nội",
                coordinate = LocationCoordinate(21.0288, 105.8525),
                representativePhotoUrl = "shared_place_4",
                visitDate = LocalDateTime.now().minusDays(10),
                durationMinutes = 240,
                photosCount = 24,
                locationType = LocationType.PARK
            ),
            SharedPlace(
                id = "shared_5",
                placeName = "Pizza 4P's",
                address = "24 Lý Quốc Sư, Hoàn Kiếm",
                coordinate = LocationCoordinate(21.0305, 105.8485),
                representativePhotoUrl = "shared_place_5",
                visitDate = LocalDateTime.now().minusDays(14),
                durationMinutes = 90,
                photosCount = 6,
                locationType = LocationType.RESTAURANT
            )
        )
    }
    
    private fun getMockPhotosForPlace(placeId: String): List<SharedPlacePhoto> {
        val now = LocalDateTime.now()
        return listOf(
            SharedPlacePhoto(
                id = "photo_1",
                photoUrl = "photo_couple_1",
                takenAt = now.minusDays(3).minusHours(2),
                takenByUserId = "user_me",
                caption = "Beautiful day! 💕"
            ),
            SharedPlacePhoto(
                id = "photo_2",
                photoUrl = "photo_couple_2",
                takenAt = now.minusDays(3).minusHours(1).minusMinutes(30),
                takenByUserId = "user_partner",
                caption = "Yummy food 🍕"
            ),
            SharedPlacePhoto(
                id = "photo_3",
                photoUrl = "photo_couple_3",
                takenAt = now.minusDays(3).minusHours(1),
                takenByUserId = "user_me",
                caption = null
            ),
            SharedPlacePhoto(
                id = "photo_4",
                photoUrl = "photo_couple_4",
                takenAt = now.minusDays(3).minusMinutes(45),
                takenByUserId = "user_partner",
                caption = "Love this place! 🌸"
            ),
            SharedPlacePhoto(
                id = "photo_5",
                photoUrl = "photo_couple_5",
                takenAt = now.minusDays(3).minusMinutes(30),
                takenByUserId = "user_me",
                caption = null
            ),
            SharedPlacePhoto(
                id = "photo_6",
                photoUrl = "photo_couple_6",
                takenAt = now.minusDays(3).minusMinutes(15),
                takenByUserId = "user_partner",
                caption = "See you next time! 👋"
            )
        )
    }
}

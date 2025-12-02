package com.example.coupleapp.ui.components.distance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.coupleapp.data.model.LocationCoordinate
import com.example.coupleapp.data.model.SharedPlace
import com.example.coupleapp.data.model.UserLocation
import com.example.coupleapp.ui.theme.PastelGreen
import com.example.coupleapp.ui.theme.PastelPink
import com.example.coupleapp.ui.theme.PastelPurple
import com.example.coupleapp.ui.theme.SoftPink
import com.example.coupleapp.ui.theme.SoftLavender
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.*
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch

/**
 * State holder for camera animation triggers
 */
class CoupleMapCameraState {
    var animateToMyLocation: (() -> Unit)? = null
    var animateToPartnerLocation: (() -> Unit)? = null
    var animateToSharedPlace: ((SharedPlace) -> Unit)? = null
}

/**
 * Google Maps component for Distance feature
 * Displays real map with custom avatar markers
 */
@Composable
fun CoupleGoogleMap(
    myLocation: UserLocation?,
    partnerLocation: UserLocation?,
    sharedPlaces: List<SharedPlace> = emptyList(),
    onMyMarkerClick: () -> Unit,
    onPartnerMarkerClick: () -> Unit,
    onSharedPlaceClick: (SharedPlace) -> Unit = {},
    onMyLocationButtonClick: () -> Unit = {},
    cameraState: CoupleMapCameraState? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Calculate center point between two users - NO ANIMATION
    val centerLat = if (myLocation != null && partnerLocation != null) {
        (myLocation.coordinate.latitude + partnerLocation.coordinate.latitude) / 2
    } else {
        myLocation?.coordinate?.latitude ?: partnerLocation?.coordinate?.latitude ?: 21.0285
    }
    
    val centerLng = if (myLocation != null && partnerLocation != null) {
        (myLocation.coordinate.longitude + partnerLocation.coordinate.longitude) / 2
    } else {
        myLocation?.coordinate?.longitude ?: partnerLocation?.coordinate?.longitude ?: 105.8542
    }
    
    // Calculate initial zoom to fit both markers
    val initialZoom = if (myLocation != null && partnerLocation != null) {
        calculateZoomLevel(myLocation.coordinate, partnerLocation.coordinate)
    } else {
        14f
    }
    
    // Camera position state - Start directly at the target position
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(centerLat, centerLng),
            initialZoom
        )
    }
    
    // REMOVED: Initial animation that zooms from world view
    // Now starts directly at the correct position
    
    // Function to animate camera to a specific user location
    fun animateToLocation(location: UserLocation) {
        coroutineScope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.coordinate.latitude, location.coordinate.longitude),
                    17f // Closer zoom when focusing on a user
                ),
                durationMs = 800
            )
        }
    }
    
    // Function to animate camera to my location directly (no reload)
    fun targetMyLocation() {
        myLocation?.let { location ->
            coroutineScope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(location.coordinate.latitude, location.coordinate.longitude),
                        17f
                    ),
                    durationMs = 500
                )
            }
        }
    }
    
    // Function to animate to shared place
    fun animateToSharedPlace(place: SharedPlace) {
        coroutineScope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(place.coordinate.latitude, place.coordinate.longitude),
                    17f
                ),
                durationMs = 800
            )
        }
    }
    
    // Register animation callbacks with cameraState
    LaunchedEffect(cameraState, myLocation, partnerLocation) {
        cameraState?.animateToMyLocation = {
            myLocation?.let { targetMyLocation() }
        }
        cameraState?.animateToPartnerLocation = {
            partnerLocation?.let { animateToLocation(it) }
        }
        cameraState?.animateToSharedPlace = { place ->
            animateToSharedPlace(place)
        }
    }
    
    // Map UI settings
    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = false,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = false,
            compassEnabled = false
        )
    }
    
    // Map properties - custom style for cute look
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false
        )
    }
    
    GoogleMap(
        modifier = modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        uiSettings = uiSettings,
        properties = mapProperties
    ) {
        // My location marker with arrow
        myLocation?.let { user ->
            CoupleMapMarker(
                position = LatLng(user.coordinate.latitude, user.coordinate.longitude),
                title = user.userName,
                isMe = true,
                batteryLevel = user.batteryLevel,
                isOnline = user.isOnline,
                onClick = {
                    animateToLocation(user)
                    onMyMarkerClick()
                    true
                }
            )
        }
        
        // Partner location marker with arrow
        partnerLocation?.let { user ->
            CoupleMapMarker(
                position = LatLng(user.coordinate.latitude, user.coordinate.longitude),
                title = user.userName,
                isMe = false,
                batteryLevel = user.batteryLevel,
                isOnline = user.isOnline,
                onClick = {
                    animateToLocation(user)
                    onPartnerMarkerClick()
                    true
                }
            )
        }
        
        // Shared places markers
        sharedPlaces.forEach { place ->
            SharedPlaceMapMarker(
                position = LatLng(place.coordinate.latitude, place.coordinate.longitude),
                place = place,
                onClick = {
                    animateToSharedPlace(place)
                    onSharedPlaceClick(place)
                    true
                }
            )
        }
        
        // Draw line between two users (optional cute connection)
        if (myLocation != null && partnerLocation != null) {
            Polyline(
                points = listOf(
                    LatLng(myLocation.coordinate.latitude, myLocation.coordinate.longitude),
                    LatLng(partnerLocation.coordinate.latitude, partnerLocation.coordinate.longitude)
                ),
                color = SoftPink.copy(alpha = 0.6f),
                width = 8f,
                pattern = listOf(Dot(), Gap(12f), Dot(), Gap(12f))
            )
        }
    }
}

/**
 * Calculate appropriate zoom level based on distance between two points
 */
private fun calculateZoomLevel(coord1: LocationCoordinate, coord2: LocationCoordinate): Float {
    val earthRadius = 6371000.0
    val lat1Rad = Math.toRadians(coord1.latitude)
    val lat2Rad = Math.toRadians(coord2.latitude)
    val deltaLat = Math.toRadians(coord2.latitude - coord1.latitude)
    val deltaLon = Math.toRadians(coord2.longitude - coord1.longitude)
    
    val a = kotlin.math.sin(deltaLat / 2).let { it * it } +
            kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) * 
            kotlin.math.sin(deltaLon / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    val distance = earthRadius * c
    
    return when {
        distance < 500 -> 16f
        distance < 1000 -> 15f
        distance < 2000 -> 14f
        distance < 5000 -> 13f
        distance < 10000 -> 12f
        else -> 11f
    }
}

/**
 * Custom marker for couple app with emoji avatar and arrow
 */
@Composable
private fun CoupleMapMarker(
    position: LatLng,
    title: String,
    isMe: Boolean,
    batteryLevel: Int,
    isOnline: Boolean,
    onClick: () -> Boolean
) {
    // Create custom marker bitmap with arrow
    val markerIcon = remember(isMe, batteryLevel, isOnline) {
        createCoupleMarkerBitmapWithArrow(
            isMe = isMe,
            batteryLevel = batteryLevel,
            isOnline = isOnline
        )
    }
    
    MarkerComposable(
        keys = arrayOf(isMe, batteryLevel, isOnline),
        state = MarkerState(position = position),
        title = title,
        onClick = { onClick() }
    ) {
        // Use composable marker content
        AvatarMapMarkerWithArrow(
            user = UserLocation(
                userId = if (isMe) "me" else "partner",
                userName = title,
                avatarUrl = "",
                coordinate = LocationCoordinate(position.latitude, position.longitude),
                address = "",
                lastUpdated = java.time.LocalDateTime.now(),
                batteryLevel = batteryLevel,
                isOnline = isOnline
            ),
            isMe = isMe,
            onClick = { },
            size = 56.dp
        )
    }
}

/**
 * Marker for shared places with photo preview
 */
@Composable
private fun SharedPlaceMapMarker(
    position: LatLng,
    place: SharedPlace,
    onClick: () -> Boolean
) {
    val markerIcon = remember(place.id) {
        createSharedPlaceMarkerBitmap(place)
    }
    
    MarkerComposable(
        keys = arrayOf(place.id),
        state = MarkerState(position = position),
        title = place.placeName,
        onClick = { onClick() }
    ) {
        SharedPlaceMapMarkerContent(
            place = place,
            size = 52.dp
        )
    }
}

/**
 * Create bitmap for marker with arrow pointing down and circular battery
 */
private fun createCoupleMarkerBitmapWithArrow(
    isMe: Boolean,
    batteryLevel: Int,
    isOnline: Boolean
): BitmapDescriptor {
    val size = 140
    val arrowHeight = 20
    val totalHeight = size + arrowHeight
    val bitmap = Bitmap.createBitmap(size, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val backgroundColor = if (isMe) {
        PastelPink.toArgb()
    } else {
        PastelGreen.toArgb()
    }
    
    val borderColor = if (isMe) {
        SoftPink.toArgb()
    } else {
        Color(0xFF98E4C8).toArgb()
    }
    
    val centerX = size / 2f
    val circleRadius = size / 2f - 20
    val circleCenterY = size / 2f - 10
    
    // Draw outer pulse circle (if online)
    if (isOnline) {
        val pulsePaint = Paint().apply {
            color = borderColor
            alpha = 60
            isAntiAlias = true
        }
        canvas.drawCircle(centerX, circleCenterY, circleRadius + 12, pulsePaint)
    }
    
    // Draw main circle background
    val bgPaint = Paint().apply {
        color = backgroundColor
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, circleCenterY, circleRadius, bgPaint)
    
    // Draw border
    val borderPaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, circleCenterY, circleRadius - 3, borderPaint)
    
    // Draw arrow pointing down (cute triangle)
    val arrowPaint = Paint().apply {
        color = borderColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val arrowPath = Path().apply {
        moveTo(centerX - 12f, circleCenterY + circleRadius - 5)
        lineTo(centerX + 12f, circleCenterY + circleRadius - 5)
        lineTo(centerX, circleCenterY + circleRadius + arrowHeight)
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)
    
    // Draw circular battery indicator (ring around the avatar)
    val batteryPaint = Paint().apply {
        color = when {
            batteryLevel > 50 -> android.graphics.Color.parseColor("#4CAF50")
            batteryLevel > 20 -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#FF5252")
        }
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }
    val batteryAngle = (batteryLevel / 100f) * 360f
    val batteryRect = RectF(
        centerX - circleRadius + 10,
        circleCenterY - circleRadius + 10,
        centerX + circleRadius - 10,
        circleCenterY + circleRadius - 10
    )
    canvas.drawArc(batteryRect, -90f, batteryAngle, false, batteryPaint)
    
    // Battery background ring
    val batteryBgPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#E0E0E0")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    canvas.drawArc(batteryRect, -90f + batteryAngle, 360f - batteryAngle, false, batteryBgPaint)
    
    // Draw online indicator
    if (isOnline) {
        val onlinePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#4CAF50")
            isAntiAlias = true
        }
        canvas.drawCircle(centerX + circleRadius - 15, circleCenterY + circleRadius - 25, 10f, onlinePaint)
        
        // White border for online indicator
        val whiteBorder = Paint().apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
        canvas.drawCircle(centerX + circleRadius - 15, circleCenterY + circleRadius - 25, 10f, whiteBorder)
    }
    
    // Draw emoji in center
    val emojiPaint = Paint().apply {
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    val emoji = if (isMe) "🌸" else "🍋"
    canvas.drawText(emoji, centerX, circleCenterY + 12f, emojiPaint)
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

/**
 * Create bitmap for shared place marker
 */
private fun createSharedPlaceMarkerBitmap(place: SharedPlace): BitmapDescriptor {
    val size = 120
    val arrowHeight = 16
    val totalHeight = size + arrowHeight
    val bitmap = Bitmap.createBitmap(size, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    val backgroundColor = PastelPurple.toArgb()
    val borderColor = SoftLavender.toArgb()
    
    val centerX = size / 2f
    val circleRadius = size / 2f - 16
    val circleCenterY = size / 2f - 8
    
    // Draw main circle background
    val bgPaint = Paint().apply {
        color = backgroundColor
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, circleCenterY, circleRadius, bgPaint)
    
    // Draw border
    val borderPaint = Paint().apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    canvas.drawCircle(centerX, circleCenterY, circleRadius - 2, borderPaint)
    
    // Draw arrow
    val arrowPaint = Paint().apply {
        color = borderColor
        isAntiAlias = true
        style = Paint.Style.FILL
    }
    val arrowPath = Path().apply {
        moveTo(centerX - 10f, circleCenterY + circleRadius - 4)
        lineTo(centerX + 10f, circleCenterY + circleRadius - 4)
        lineTo(centerX, circleCenterY + circleRadius + arrowHeight)
        close()
    }
    canvas.drawPath(arrowPath, arrowPaint)
    
    // Draw camera/photo emoji
    val emojiPaint = Paint().apply {
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText("📸", centerX, circleCenterY + 10f, emojiPaint)
    
    // Draw photo count badge
    if (place.photosCount > 0) {
        val badgePaint = Paint().apply {
            color = android.graphics.Color.parseColor("#FF6B9D")
            isAntiAlias = true
        }
        canvas.drawCircle(centerX + circleRadius - 10, circleCenterY - circleRadius + 20, 14f, badgePaint)
        
        val countPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 12f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
        val countText = if (place.photosCount > 99) "99+" else place.photosCount.toString()
        canvas.drawText(countText, centerX + circleRadius - 10, circleCenterY - circleRadius + 25, countPaint)
    }
    
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

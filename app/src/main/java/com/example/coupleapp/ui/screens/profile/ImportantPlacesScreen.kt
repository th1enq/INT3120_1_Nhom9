package com.example.coupleapp.ui.screens.profile

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.service.SmartGeofenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/**
 * Important Places Screen
 * Configure Home, Work, Partner's locations for Smart Geofencing
 * 
 * Features:
 * - Add/Edit/Delete important places
 * - Automatic address lookup using Geocoder
 * - Manual coordinate input
 * - Toggle geofencing per location
 */

data class ImportantPlace(
    val id: String,
    val name: String,
    val type: PlaceType,
    val latitude: Double,
    val longitude: Double,
    val address: String = "",
    val geofenceEnabled: Boolean = true
)

enum class PlaceType(val icon: ImageVector, val displayName: String, val color: Color) {
    HOME(Icons.Filled.Home, "Nhà", Color(0xFF4CAF50)),
    WORK(Icons.Filled.Work, "Nơi làm việc", Color(0xFF2196F3)),
    PARTNER(Icons.Filled.Favorite, "Nhà người yêu", Color(0xFFE91E63)),
    SCHOOL(Icons.Filled.School, "Trường học", Color(0xFFFF9800)),
    GYM(Icons.Filled.FitnessCenter, "Phòng gym", Color(0xFF9C27B0)),
    OTHER(Icons.Filled.Place, "Khác", Color(0xFF607D8B))
}

class ImportantPlacesViewModel(
    private val smartGeofenceManager: SmartGeofenceManager
) : ViewModel() {
    
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    private val _places = MutableStateFlow<List<ImportantPlace>>(emptyList())
    val places = _places.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    
    init {
        loadPlaces()
    }
    
    class Factory(private val context: android.content.Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ImportantPlacesViewModel(
                SmartGeofenceManager.getInstance(context)
            ) as T
        }
    }
    
    private fun loadPlaces() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch
                
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("important_places")
                    .get()
                    .await()
                
                _places.value = snapshot.documents.mapNotNull { doc ->
                    try {
                        ImportantPlace(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            type = PlaceType.valueOf(doc.getString("type") ?: "OTHER"),
                            latitude = doc.getDouble("latitude") ?: 0.0,
                            longitude = doc.getDouble("longitude") ?: 0.0,
                            address = doc.getString("address") ?: "",
                            geofenceEnabled = doc.getBoolean("geofenceEnabled") ?: true
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun addOrUpdatePlace(place: ImportantPlace) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                val placeId = if (place.id.isBlank()) {
                    firestore.collection("users")
                        .document(userId)
                        .collection("important_places")
                        .document()
                        .id
                } else {
                    place.id
                }
                
                val data = mapOf(
                    "name" to place.name,
                    "type" to place.type.name,
                    "latitude" to place.latitude,
                    "longitude" to place.longitude,
                    "address" to place.address,
                    "geofenceEnabled" to place.geofenceEnabled,
                    "updatedAt" to com.google.firebase.Timestamp.now()
                )
                
                firestore.collection("users")
                    .document(userId)
                    .collection("important_places")
                    .document(placeId)
                    .set(data)
                    .await()
                
                // Update geofence
                if (place.geofenceEnabled) {
                    // Map PlaceType to ImportantPlaceType
                    val geofenceType = when (place.type) {
                        PlaceType.HOME -> SmartGeofenceManager.ImportantPlaceType.HOME
                        PlaceType.WORK -> SmartGeofenceManager.ImportantPlaceType.WORK
                        PlaceType.PARTNER -> SmartGeofenceManager.ImportantPlaceType.PARTNER_HOME
                        PlaceType.SCHOOL, PlaceType.GYM -> SmartGeofenceManager.ImportantPlaceType.FAVORITE
                        PlaceType.OTHER -> SmartGeofenceManager.ImportantPlaceType.OTHER
                    }
                    smartGeofenceManager.addGeofence(
                        placeId = placeId,
                        name = place.name,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        type = geofenceType
                    )
                } else {
                    smartGeofenceManager.removeGeofence(placeId)
                }
                
                loadPlaces()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun deletePlace(placeId: String) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                
                firestore.collection("users")
                    .document(userId)
                    .collection("important_places")
                    .document(placeId)
                    .delete()
                    .await()
                
                smartGeofenceManager.removeGeofence(placeId)
                loadPlaces()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun toggleGeofence(place: ImportantPlace) {
        addOrUpdatePlace(place.copy(geofenceEnabled = !place.geofenceEnabled))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportantPlacesScreen(
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: ImportantPlacesViewModel = viewModel(
        factory = ImportantPlacesViewModel.Factory(context)
    )
    val places by viewModel.places.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var editingPlace by remember { mutableStateOf<ImportantPlace?>(null) }
    var visible by remember { mutableStateOf(false) }
    
    // Check location permission
    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        visible = true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Địa điểm quan trọng",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2D3748)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF2D3748)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showAddDialog = true }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Add Place",
                            tint = Color(0xFFFF6B9D)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFFF6B9D),
                contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
            }
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFFF5F8),
                            Color(0xFFFFFBF5),
                            Color(0xFFFFFAF0)
                        )
                    )
                )
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFFF6B9D)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Info card
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { -it / 4 }
                    ) {
                        InfoCard(hasLocationPermission)
                    }
                    
                    if (!hasLocationPermission) {
                        // Show permission warning
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400, 100)) { it / 4 }
                        ) {
                            PermissionWarningCard()
                        }
                    }
                    
                    // Quick add buttons for common places
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(400, 150)) + slideInVertically(tween(400, 150)) { it / 4 }
                    ) {
                        QuickAddSection(
                            existingTypes = places.map { it.type }.toSet(),
                            onAddClick = { type ->
                                editingPlace = ImportantPlace(
                                    id = "",
                                    name = type.displayName,
                                    type = type,
                                    latitude = 0.0,
                                    longitude = 0.0
                                )
                                showAddDialog = true
                            }
                        )
                    }
                    
                    // Places list
                    if (places.isNotEmpty()) {
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, 200)) + slideInVertically(tween(400, 200)) { it / 4 }
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "ĐỊA ĐIỂM ĐÃ LƯU",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF718096),
                                    letterSpacing = 1.sp
                                )
                                
                                places.forEach { place ->
                                    PlaceCard(
                                        place = place,
                                        onEditClick = {
                                            editingPlace = place
                                            showAddDialog = true
                                        },
                                        onDeleteClick = {
                                            viewModel.deletePlace(place.id)
                                        },
                                        onToggleGeofence = {
                                            viewModel.toggleGeofence(place)
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Empty state
                        AnimatedVisibility(
                            visible = visible,
                            enter = fadeIn(tween(400, 200))
                        ) {
                            EmptyState()
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog) {
        AddEditPlaceDialog(
            place = editingPlace,
            onDismiss = {
                showAddDialog = false
                editingPlace = null
            },
            onSave = { place ->
                viewModel.addOrUpdatePlace(place)
                showAddDialog = false
                editingPlace = null
            }
        )
    }
}

@Composable
private fun InfoCard(hasLocationPermission: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFE3F2FD),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2196F3)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart Geofencing",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1565C0)
                )
                Text(
                    text = "Thêm địa điểm để nhận thông báo thông minh khi bạn hoặc người yêu đến/rời đi",
                    fontSize = 12.sp,
                    color = Color(0xFF1976D2)
                )
            }
        }
    }
}

@Composable
private fun PermissionWarningCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFFFFF3E0)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = Color(0xFFE65100),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Cần quyền vị trí để sử dụng tính năng này. Vui lòng cấp quyền trong Cài đặt quyền.",
                fontSize = 13.sp,
                color = Color(0xFFE65100)
            )
        }
    }
}

@Composable
private fun QuickAddSection(
    existingTypes: Set<PlaceType>,
    onAddClick: (PlaceType) -> Unit
) {
    val suggestedTypes = listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.PARTNER)
        .filter { it !in existingTypes }
    
    if (suggestedTypes.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "GỢI Ý THÊM NHANH",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF718096),
                letterSpacing = 1.sp
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestedTypes.forEach { type ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onAddClick(type) },
                        shape = RoundedCornerShape(12.dp),
                        color = type.color.copy(alpha = 0.1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = type.icon,
                                contentDescription = null,
                                tint = type.color,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "+ ${type.displayName}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = type.color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(
    place: ImportantPlace,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleGeofence: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEditClick() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(place.type.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = place.type.icon,
                        contentDescription = null,
                        tint = place.type.color,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF2D3748)
                    )
                    if (place.address.isNotBlank()) {
                        Text(
                            text = place.address,
                            fontSize = 12.sp,
                            color = Color(0xFF718096),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = "📍 ${String.format("%.4f", place.latitude)}, ${String.format("%.4f", place.longitude)}",
                        fontSize = 11.sp,
                        color = Color(0xFFA0AEC0)
                    )
                }
                
                // Geofence toggle
                Switch(
                    checked = place.geofenceEnabled,
                    onCheckedChange = { onToggleGeofence() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4CAF50),
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color(0xFFBDBDBD)
                    )
                )
            }
            
            // Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF718096)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sửa", color = Color(0xFF718096), fontSize = 13.sp)
                }
                
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFFE53935)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Xóa", color = Color(0xFFE53935), fontSize = 13.sp)
                }
            }
        }
    }
    
    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Xóa địa điểm?") },
            text = { Text("Bạn có chắc muốn xóa \"${place.name}\"? Geofencing sẽ bị tắt cho địa điểm này.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Xóa", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.AddLocation,
            contentDescription = null,
            tint = Color(0xFFBDBDBD),
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = "Chưa có địa điểm nào",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF9E9E9E)
        )
        Text(
            text = "Thêm Nhà, Nơi làm việc để nhận thông báo thông minh",
            fontSize = 13.sp,
            color = Color(0xFFBDBDBD)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddEditPlaceDialog(
    place: ImportantPlace?,
    onDismiss: () -> Unit,
    onSave: (ImportantPlace) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    
    var name by remember { mutableStateOf(place?.name ?: "") }
    var selectedType by remember { mutableStateOf(place?.type ?: PlaceType.HOME) }
    var latitudeText by remember { mutableStateOf(if (place?.latitude != 0.0) place?.latitude.toString() else "") }
    var longitudeText by remember { mutableStateOf(if (place?.longitude != 0.0) place?.longitude.toString() else "") }
    var address by remember { mutableStateOf(place?.address ?: "") }
    var geofenceEnabled by remember { mutableStateOf(place?.geofenceEnabled ?: true) }
    
    var isLookingUpAddress by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Look up address from coordinates
    fun lookupAddress() {
        val lat = latitudeText.toDoubleOrNull()
        val lng = longitudeText.toDoubleOrNull()
        
        if (lat != null && lng != null) {
            isLookingUpAddress = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1) { addresses ->
                        address = addresses.firstOrNull()?.getAddressLine(0) ?: ""
                        isLookingUpAddress = false
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val addresses = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
                    address = addresses?.firstOrNull()?.getAddressLine(0) ?: ""
                    isLookingUpAddress = false
                }
            } catch (e: Exception) {
                isLookingUpAddress = false
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = if (place?.id?.isNotBlank() == true) "Chỉnh sửa địa điểm" else "Thêm địa điểm",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
                
                // Place type selector
                Text(
                    text = "Loại địa điểm",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF4A5568)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaceType.values().take(3).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { 
                                selectedType = type
                                if (name.isBlank() || PlaceType.values().any { it.displayName == name }) {
                                    name = type.displayName
                                }
                            },
                            label = { Text(type.displayName, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = type.color.copy(alpha = 0.2f),
                                selectedLabelColor = type.color,
                                selectedLeadingIconColor = type.color
                            )
                        )
                    }
                }
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaceType.values().drop(3).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { 
                                selectedType = type
                                if (name.isBlank() || PlaceType.values().any { it.displayName == name }) {
                                    name = type.displayName
                                }
                            },
                            label = { Text(type.displayName, fontSize = 12.sp) },
                            leadingIcon = {
                                Icon(
                                    imageVector = type.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = type.color.copy(alpha = 0.2f),
                                selectedLabelColor = type.color,
                                selectedLeadingIconColor = type.color
                            )
                        )
                    }
                }
                
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên địa điểm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6B9D),
                        focusedLabelColor = Color(0xFFFF6B9D)
                    )
                )
                
                // Coordinates
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = latitudeText,
                        onValueChange = { latitudeText = it },
                        label = { Text("Vĩ độ") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B9D),
                            focusedLabelColor = Color(0xFFFF6B9D)
                        )
                    )
                    
                    OutlinedTextField(
                        value = longitudeText,
                        onValueChange = { longitudeText = it },
                        label = { Text("Kinh độ") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            lookupAddress()
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF6B9D),
                            focusedLabelColor = Color(0xFFFF6B9D)
                        )
                    )
                }
                
                // Hint to get coordinates
                Text(
                    text = "💡 Mở Google Maps → Long press vào vị trí → Copy tọa độ",
                    fontSize = 11.sp,
                    color = Color(0xFF718096)
                )
                
                // Address lookup button
                OutlinedButton(
                    onClick = { lookupAddress() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = latitudeText.toDoubleOrNull() != null && longitudeText.toDoubleOrNull() != null && !isLookingUpAddress
                ) {
                    if (isLookingUpAddress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tra cứu địa chỉ từ tọa độ")
                }
                
                // Address (auto-filled or manual)
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Địa chỉ (tùy chọn)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFF6B9D),
                        focusedLabelColor = Color(0xFFFF6B9D)
                    )
                )
                
                // Geofence toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bật Geofencing",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2D3748)
                        )
                        Text(
                            text = "Nhận thông báo khi đến/rời địa điểm này",
                            fontSize = 12.sp,
                            color = Color(0xFF718096)
                        )
                    }
                    Switch(
                        checked = geofenceEnabled,
                        onCheckedChange = { geofenceEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50)
                        )
                    )
                }
                
                // Error
                error?.let {
                    Text(
                        text = it,
                        fontSize = 12.sp,
                        color = Color(0xFFE53935)
                    )
                }
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = {
                            val lat = latitudeText.toDoubleOrNull()
                            val lng = longitudeText.toDoubleOrNull()
                            
                            when {
                                name.isBlank() -> error = "Vui lòng nhập tên địa điểm"
                                lat == null -> error = "Vĩ độ không hợp lệ"
                                lng == null -> error = "Kinh độ không hợp lệ"
                                lat < -90 || lat > 90 -> error = "Vĩ độ phải từ -90 đến 90"
                                lng < -180 || lng > 180 -> error = "Kinh độ phải từ -180 đến 180"
                                else -> {
                                    onSave(
                                        ImportantPlace(
                                            id = place?.id ?: "",
                                            name = name,
                                            type = selectedType,
                                            latitude = lat,
                                            longitude = lng,
                                            address = address,
                                            geofenceEnabled = geofenceEnabled
                                        )
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B9D)
                        )
                    ) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

package com.example.coupleapp.ui.screens.profile

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.R
import com.example.coupleapp.data.repository.FirebaseStorageRepository
import com.example.coupleapp.util.ImageCropHelper
import com.example.coupleapp.viewmodel.ProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Edit Profile Screen - Allows user to edit their profile information
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    onBackClick: () -> Unit = {},
    onSaveClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = viewModel()
) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRepository = remember { FirebaseStorageRepository() }
    
    // Get data from ViewModel
    val uiState by viewModel.uiState.collectAsState()
    
    // Initialize fields with Firebase data
    var displayName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var dateOfBirth by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var uploadedImageUrl by remember { mutableStateOf<String?>(null) }
    var isUploading by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showImageSourcePicker by remember { mutableStateOf(false) }
    var showDateOfBirthPicker by remember { mutableStateOf(false) }
    var showGenderPicker by remember { mutableStateOf(false) }
    
    // Camera photo file URI
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    // Function to process and upload image (crop to circle)
    fun processAndUploadImage(uri: Uri) {
        scope.launch {
            isUploading = true
            try {
                // Load and crop image to circle
                val bitmap = withContext(Dispatchers.IO) {
                    ImageCropHelper.loadBitmapFromUri(context, uri, 800)
                }
                
                if (bitmap != null) {
                    // Crop to circle
                    val circularBitmap = withContext(Dispatchers.IO) {
                        ImageCropHelper.cropToCircle(bitmap)
                    }
                    
                    // Save to cache
                    val croppedFile = withContext(Dispatchers.IO) {
                        ImageCropHelper.saveBitmapToCache(context, circularBitmap, "avatar_${System.currentTimeMillis()}.jpg")
                    }
                    
                    // Recycle bitmaps
                    if (circularBitmap != bitmap) {
                        circularBitmap.recycle()
                    }
                    bitmap.recycle()
                    
                    if (croppedFile != null) {
                        val croppedUri = Uri.fromFile(croppedFile)
                        
                        // Upload cropped image
                        val result = storageRepository.uploadImageWithContext(
                            context = context,
                            uri = croppedUri,
                            path = FirebaseStorageRepository.PROFILE_IMAGES_PATH,
                            filename = "profile_${System.currentTimeMillis()}.jpg"
                        )
                        result.onSuccess { url ->
                            uploadedImageUrl = url
                        }
                        result.onFailure { error ->
                            errorMessage = error.message
                        }
                        
                        // Delete temp file
                        croppedFile.delete()
                    } else {
                        errorMessage = "Không thể xử lý ảnh"
                    }
                } else {
                    errorMessage = "Không thể đọc ảnh"
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Lỗi xử lý ảnh"
            }
            isUploading = false
        }
    }
    
    // Image picker launcher (from gallery)
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            processAndUploadImage(it)
        }
    }
    
    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
            processAndUploadImage(tempCameraUri!!)
        }
    }
    
    // Create temp file for camera
    fun createTempImageFile(): Uri? {
        return try {
            val tempFile = File.createTempFile("camera_", ".jpg", context.cacheDir)
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                tempFile
            )
        } catch (e: Exception) {
            android.util.Log.e("EditProfileScreen", "Error creating temp file", e)
            null
        }
    }
    
    // Load user data when available
    LaunchedEffect(uiState.currentUser) {
        uiState.currentUser?.let { user ->
            displayName = user.displayName
            email = user.email
            phone = user.phoneNumber
            dateOfBirth = user.dateOfBirth
            gender = user.gender
            bio = user.bio
            uploadedImageUrl = user.profileImageUrl
        }
    }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    // Success/Error snackbars
    LaunchedEffect(showSuccess) {
        if (showSuccess) {
            delay(2000)
            showSuccess = false
        }
    }
    
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            delay(3000)
            errorMessage = null
        }
    }
    
    // Image source picker dialog
    if (showImageSourcePicker) {
        AlertDialog(
            onDismissRequest = { showImageSourcePicker = false },
            title = {
                Text(
                    text = stringResource(R.string.choose_avatar),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Camera option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageSourcePicker = false
                                tempCameraUri = createTempImageFile()
                                tempCameraUri?.let { uri ->
                                    cameraLauncher.launch(uri)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFFFF6B9D),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.take_photo_title),
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2D3748)
                                )
                                Text(
                                    text = stringResource(R.string.take_photo_desc),
                                    fontSize = 12.sp,
                                    color = Color(0xFF718096)
                                )
                            }
                        }
                    }
                    
                    // Gallery option
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showImageSourcePicker = false
                                imagePickerLauncher.launch("image/*")
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F5F5)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = Color(0xFF6B9DFF),
                                modifier = Modifier.size(28.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(R.string.choose_from_gallery),
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF2D3748)
                                )
                                Text(
                                    text = stringResource(R.string.choose_from_gallery_desc),
                                    fontSize = 12.sp,
                                    color = Color(0xFF718096)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourcePicker = false }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF718096))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.edit_profile),
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
                    TextButton(
                        onClick = {
                            scope.launch {
                                viewModel.updateProfile(
                                    displayName = displayName,
                                    email = email,
                                    phoneNumber = phone,
                                    dateOfBirth = dateOfBirth,
                                    gender = gender,
                                    bio = bio,
                                    profileImageUrl = uploadedImageUrl,
                                    onSuccess = {
                                        scope.launch {
                                            showSuccess = true
                                            // Reload profile data immediately
                                            delay(500)
                                            onSaveClick()
                                        }
                                    },
                                    onError = { error ->
                                        errorMessage = error
                                    }
                                )
                            }
                        },
                        enabled = !isUploading
                    ) {
                        Text(
                            text = if (isUploading) "Uploading..." else "Save",
                            color = if (isUploading) Color.Gray else Color(0xFFFF6B9D),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Avatar
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600)) +
                            scaleIn(animationSpec = tween(600))
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .shadow(8.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable { showImageSourcePicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uploadedImageUrl != null && uploadedImageUrl!!.isNotEmpty()) {
                            // Display uploaded image
                            coil.compose.AsyncImage(
                                model = uploadedImageUrl,
                                contentDescription = "Profile Picture",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // Default icon
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Default Profile",
                                modifier = Modifier.size(60.dp),
                                tint = Color(0xFFE0E0E0)
                            )
                        }
                        
                        // Upload indicator
                        if (isUploading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color.White)
                            }
                        }
                        
                        // Edit overlay
                        if (!isUploading) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6B9D)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PhotoCamera,
                                    contentDescription = "Change Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Form fields
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(animationSpec = tween(600, delayMillis = 200)) +
                            slideInVertically(animationSpec = tween(600, delayMillis = 200)) { it / 4 }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Display Name
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(stringResource(R.string.display_name)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF718096)
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF6B9D),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        
                        // Email
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text(stringResource(R.string.email)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Email,
                                    contentDescription = null,
                                    tint = Color(0xFF718096)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF6B9D),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        
                        // Phone
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(stringResource(R.string.phone_optional)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Phone,
                                    contentDescription = null,
                                    tint = Color(0xFF718096)
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF6B9D),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        
                        // Date of Birth (clickable field)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDateOfBirthPicker = true }
                        ) {
                            OutlinedTextField(
                                value = if (dateOfBirth.isNotEmpty()) {
                                    try {
                                        val date = java.time.LocalDate.parse(dateOfBirth, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                                        date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                    } catch (e: Exception) {
                                        dateOfBirth
                                    }
                                } else "",
                                onValueChange = { },
                                label = { Text(stringResource(R.string.date_of_birth)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Cake,
                                        contentDescription = null,
                                        tint = Color(0xFF718096)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.CalendarMonth,
                                        contentDescription = "Chọn ngày",
                                        tint = Color(0xFFFF6B9D)
                                    )
                                },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledContainerColor = Color.White,
                                    disabledTextColor = Color(0xFF2D3748),
                                    disabledLabelColor = Color(0xFF718096),
                                    disabledLeadingIconColor = Color(0xFF718096),
                                    disabledTrailingIconColor = Color(0xFFFF6B9D)
                                )
                            )
                        }
                        
                        // Gender (clickable field)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showGenderPicker = true }
                        ) {
                            OutlinedTextField(
                                value = when (gender.lowercase()) {
                                    "male" -> "Nam"
                                    "female" -> "Nữ"
                                    "other" -> "Khác"
                                    else -> gender
                                },
                                onValueChange = { },
                                label = { Text(stringResource(R.string.gender)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (gender.lowercase() == "male") Icons.Filled.Male 
                                                      else if (gender.lowercase() == "female") Icons.Filled.Female
                                                      else Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF718096)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Chọn",
                                        tint = Color(0xFFFF6B9D)
                                    )
                                },
                                readOnly = true,
                                enabled = false,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    disabledBorderColor = Color(0xFFE0E0E0),
                                    disabledContainerColor = Color.White,
                                    disabledTextColor = Color(0xFF2D3748),
                                    disabledLabelColor = Color(0xFF718096),
                                    disabledLeadingIconColor = Color(0xFF718096),
                                    disabledTrailingIconColor = Color(0xFFFF6B9D)
                                )
                            )
                        }
                        
                        // Bio
                        OutlinedTextField(
                            value = bio,
                            onValueChange = { bio = it },
                            label = { Text(stringResource(R.string.bio_optional)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF718096)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFF6B9D),
                                unfocusedBorderColor = Color(0xFFE0E0E0),
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            maxLines = 4
                        )
                    }
                }
            }
            
            // Success message
            if (showSuccess) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.profile_updated),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
            
            // Error message
            errorMessage?.let { error ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Warning,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Date of Birth Picker Dialog
    if (showDateOfBirthPicker) {
        val initialDateMillis = try {
            if (dateOfBirth.isNotEmpty()) {
                val date = java.time.LocalDate.parse(dateOfBirth, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                date.toEpochDay() * 24 * 60 * 60 * 1000
            } else {
                // Default to 25 years ago
                java.time.LocalDate.now().minusYears(25).toEpochDay() * 24 * 60 * 60 * 1000
            }
        } catch (e: Exception) {
            java.time.LocalDate.now().minusYears(25).toEpochDay() * 24 * 60 * 60 * 1000
        }
        
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDateMillis
        )
        
        DatePickerDialog(
            onDismissRequest = { showDateOfBirthPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                            dateOfBirth = selectedDate.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                        }
                        showDateOfBirthPicker = false
                    }
                ) {
                    Text(stringResource(R.string.confirm), color = Color(0xFFFF6B9D))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDateOfBirthPicker = false }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF718096))
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    selectedDayContainerColor = Color(0xFFFF6B9D),
                    todayContentColor = Color(0xFFFF6B9D),
                    todayDateBorderColor = Color(0xFFFF6B9D)
                )
            )
        }
    }
    
    // Gender Picker Dialog
    if (showGenderPicker) {
        AlertDialog(
            onDismissRequest = { showGenderPicker = false },
            title = {
                Text(
                    text = stringResource(R.string.select_gender),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2D3748)
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "male" to "Nam",
                        "female" to "Nữ",
                        "other" to "Khác"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    gender = value
                                    showGenderPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = gender.lowercase() == value,
                                onClick = {
                                    gender = value
                                    showGenderPicker = false
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFF6B9D)
                                )
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Icon(
                                imageVector = when (value) {
                                    "male" -> Icons.Filled.Male
                                    "female" -> Icons.Filled.Female
                                    else -> Icons.Filled.Person
                                },
                                contentDescription = null,
                                tint = Color(0xFF718096),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = Color(0xFF2D3748)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGenderPicker = false }) {
                    Text(stringResource(R.string.cancel), color = Color(0xFF718096))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

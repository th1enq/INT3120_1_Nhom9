package com.example.coupleapp.util

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Helper class for photo picking and camera capture functionality
 */
class PhotoPickerHelper(private val context: Context) {
    
    private var pendingPhotoUri: Uri? = null
    
    /**
     * Create a temporary URI for camera capture
     */
    fun createImageUri(): Uri? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val contentValues = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CoupleApp")
                }
                context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
            } else {
                // Use FileProvider for older versions
                val imageFile = createImageFile()
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    imageFile
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Create a temporary file for camera capture (for older Android versions)
     */
    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir = context.getExternalFilesDir("Pictures")
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        )
    }
    
    /**
     * Get the real path from a content URI
     */
    fun getRealPathFromUri(uri: Uri): String? {
        return try {
            if (uri.scheme == "content") {
                // Try to get the display name first
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val displayNameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                        if (displayNameIndex != -1) {
                            return it.getString(displayNameIndex)
                        }
                    }
                }
            }
            uri.toString()
        } catch (e: Exception) {
            uri.toString()
        }
    }
}

/**
 * State holder for photo picker composable
 */
class PhotoPickerState {
    var showCameraPermissionRequest by mutableStateOf(false)
    var capturedPhotoUri by mutableStateOf<Uri?>(null)
    var selectedPhotoUri by mutableStateOf<Uri?>(null)
    var pendingCameraUri by mutableStateOf<Uri?>(null)
    
    fun reset() {
        capturedPhotoUri = null
        selectedPhotoUri = null
        pendingCameraUri = null
    }
}

/**
 * Composable that provides photo picker and camera functionality
 */
@Composable
fun rememberPhotoPickerState(): PhotoPickerState {
    return remember { PhotoPickerState() }
}

/**
 * Permissions required for camera functionality
 */
val cameraPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.CAMERA)
} else {
    arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

/**
 * Permissions required for gallery access
 */
val galleryPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
} else {
    arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )
}

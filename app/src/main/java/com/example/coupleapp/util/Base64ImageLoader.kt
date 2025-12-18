package com.example.coupleapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import coil.map.Mapper
import com.example.coupleapp.data.repository.FirebaseStorageRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import okio.Buffer
import java.nio.ByteBuffer

/**
 * Wrapper class to identify base64 image URLs
 */
data class Base64ImageUrl(val imageId: String)

/**
 * Mapper to convert String URLs to Base64ImageUrl for our custom fetcher
 */
class Base64ImageMapper : Mapper<String, Base64ImageUrl> {
    companion object {
        private const val BASE64_DATA_MIN_LENGTH = 500 // Inline base64 data is very long
        // Characters that are valid in base64 but indicate raw base64 content
        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]+$")
    }
    
    override fun map(data: String, options: Options): Base64ImageUrl? {
        android.util.Log.d("Base64ImageMapper", "=== MAPPER INPUT ===")
        android.util.Log.d("Base64ImageMapper", "Input data: ${data.take(100)}...")
        android.util.Log.d("Base64ImageMapper", "Total length: ${data.length}")
        
        return when {
            // Format 1: base64://imageId (old legacy format)
            data.startsWith("base64://") -> {
                val imageId = data.removePrefix("base64://")
                android.util.Log.d("Base64ImageMapper", "✓ Legacy format (base64://), imageId: $imageId")
                Base64ImageUrl("LEGACY:$imageId")
            }
            // Format 2 & 3: data:image/...;base64,<content>
            // Need to distinguish between imageId (short) and actual data (long)
            data.startsWith("data:image/") && data.contains("base64,") -> {
                val commaIndex = data.indexOf(",")
                if (commaIndex == -1 || commaIndex >= data.length - 1) {
                    android.util.Log.e("Base64ImageMapper", "✗ Invalid format: no data after comma")
                    return null
                }
                
                val afterComma = data.substring(commaIndex + 1)
                val isInlineData = afterComma.length > BASE64_DATA_MIN_LENGTH
                
                android.util.Log.d("Base64ImageMapper", "Content after comma length: ${afterComma.length}")
                android.util.Log.d("Base64ImageMapper", "Is inline data? $isInlineData")
                
                if (isInlineData) {
                    // This is actual base64 image data (from WorkManager)
                    android.util.Log.d("Base64ImageMapper", "✓ Inline base64 data (long)")
                    Base64ImageUrl("INLINE:$data")
                } else {
                    // This is just an imageId (from app upload) - need to fetch from Firestore
                    android.util.Log.d("Base64ImageMapper", "✓ Image ID format (short), ID: $afterComma")
                    Base64ImageUrl("LEGACY:$afterComma")
                }
            }
            // Format 4: RAW base64 string (from LocketFirebaseRepository)
            // This is base64 data stored directly without any prefix
            data.length > BASE64_DATA_MIN_LENGTH && isLikelyBase64(data) -> {
                android.util.Log.d("Base64ImageMapper", "✓ Raw base64 data detected (${data.length} chars)")
                Base64ImageUrl("RAW:$data")
            }
            else -> {
                android.util.Log.e("Base64ImageMapper", "✗ Unknown format")
                null
            }
        }
    }
    
    /**
     * Check if string looks like raw base64 encoded data
     */
    private fun isLikelyBase64(data: String): Boolean {
        // Check first and last part for base64 characters
        // Full validation would be expensive for large strings
        val sample = data.take(100) + data.takeLast(100)
        val isValid = sample.all { c -> 
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
        }
        android.util.Log.d("Base64ImageMapper", "isLikelyBase64 check: $isValid")
        return isValid
    }
}

/**
 * Custom Coil Fetcher to handle base64 image URLs stored in Firestore
 */
class Base64ImageFetcher(
    private val data: Base64ImageUrl,
    private val options: Options
) : Fetcher {
    
    override suspend fun fetch(): FetchResult {
        val imageId = data.imageId
        
        android.util.Log.d("Base64ImageFetcher", "=== FETCHER START ===")
        android.util.Log.d("Base64ImageFetcher", "Received imageId: ${imageId.take(100)}...")
        android.util.Log.d("Base64ImageFetcher", "Starts with INLINE:? ${imageId.startsWith("INLINE:")}")
        android.util.Log.d("Base64ImageFetcher", "Starts with LEGACY:? ${imageId.startsWith("LEGACY:")}")
        android.util.Log.d("Base64ImageFetcher", "Starts with RAW:? ${imageId.startsWith("RAW:")}")
        
        val base64Data: String = when {
            imageId.startsWith("INLINE:") -> {
                // Remove marker and get actual data URL
                val dataUrl = imageId.removePrefix("INLINE:")
                android.util.Log.d("Base64ImageFetcher", "✓ Processing inline format")
                android.util.Log.d("Base64ImageFetcher", "Data URL: ${dataUrl.take(100)}...")
                
                val commaIndex = dataUrl.indexOf(",")
                if (commaIndex == -1 || commaIndex >= dataUrl.length - 1) {
                    android.util.Log.e("Base64ImageFetcher", "✗ Invalid format: comma at $commaIndex")
                    throw Exception("Invalid inline base64 format: no data after comma")
                }
                val extracted = dataUrl.substring(commaIndex + 1)
                android.util.Log.d("Base64ImageFetcher", "✓ Extracted ${extracted.length} chars of base64")
                extracted
            }
            imageId.startsWith("RAW:") -> {
                // Raw base64 data stored directly (from LocketFirebaseRepository)
                val rawData = imageId.removePrefix("RAW:")
                android.util.Log.d("Base64ImageFetcher", "✓ Processing RAW base64 format")
                android.util.Log.d("Base64ImageFetcher", "RAW data length: ${rawData.length} chars")
                rawData
            }
            imageId.startsWith("LEGACY:") -> {
                // Remove marker and fetch from Firestore
                val docId = imageId.removePrefix("LEGACY:")
                android.util.Log.d("Base64ImageFetcher", "✓ Fetching legacy from Firestore: $docId")
                
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection(FirebaseStorageRepository.IMAGES_COLLECTION)
                    .document(docId)
                    .get()
                    .await()
                
                if (!doc.exists()) {
                    android.util.Log.e("Base64ImageFetcher", "✗ Document not found: $docId")
                    throw Exception("Image not found: $docId")
                }
                
                val data = doc.getString("data")
                if (data.isNullOrEmpty()) {
                    android.util.Log.e("Base64ImageFetcher", "✗ Empty data in document: $docId")
                    throw Exception("Image data is empty")
                }
                android.util.Log.d("Base64ImageFetcher", "✓ Fetched ${data.length} chars from Firestore")
                data
            }
            else -> {
                android.util.Log.e("Base64ImageFetcher", "✗ Unknown format: $imageId")
                throw Exception("Unknown image ID format: ${imageId.take(50)}")
            }
        }
        
        // Decode base64 to bytes
        val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
        
        android.util.Log.d("Base64ImageFetcher", "Decoded ${bytes.size} bytes")
        
        // Create an ImageSource from the bytes
        val buffer = Buffer().write(bytes)
        val imageSource = ImageSource(buffer, options.context)
        
        return SourceResult(
            source = imageSource,
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK
        )
    }
    
    class Factory : Fetcher.Factory<Base64ImageUrl> {
        override fun create(data: Base64ImageUrl, options: Options, imageLoader: ImageLoader): Fetcher {
            android.util.Log.d("Base64ImageFetcher.Factory", "Creating fetcher for: ${data.imageId.take(50)}...")
            return Base64ImageFetcher(data, options)
        }
    }
}

/**
 * Utility object to decode base64 images
 */
object Base64ImageDecoder {
    
    /**
     * Decode base64 string to Bitmap
     */
    fun decodeBase64ToBitmap(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            android.util.Log.e("Base64ImageDecoder", "Failed to decode base64", e)
            null
        }
    }
    
    /**
     * Check if URL is a base64 image reference
     */
    fun isBase64ImageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.startsWith(FirebaseStorageRepository.BASE64_PREFIX) ||
               (url.startsWith("data:image/") && url.contains("base64,"))
    }
    
    /**
     * Check if URL is a valid image URL (http, content, or base64)
     */
    fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        return url.startsWith("http") || 
               url.startsWith("content") || 
               url.startsWith(FirebaseStorageRepository.BASE64_PREFIX) ||
               (url.startsWith("data:image/") && url.contains("base64,"))
    }
    
    /**
     * Extract image ID from base64 URL
     */
    fun extractImageId(url: String): String? {
        return if (isBase64ImageUrl(url)) {
            url.removePrefix(FirebaseStorageRepository.BASE64_PREFIX)
        } else {
            null
        }
    }
}

/**
 * Create a custom ImageLoader that supports base64 images from Firestore
 */
fun createImageLoaderWithBase64Support(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
        .components {
            // Add mapper first to convert String -> Base64ImageUrl
            add(Base64ImageMapper())
            // Then add fetcher to handle Base64ImageUrl
            add(Base64ImageFetcher.Factory())
        }
        .crossfade(true)
        .build()
}

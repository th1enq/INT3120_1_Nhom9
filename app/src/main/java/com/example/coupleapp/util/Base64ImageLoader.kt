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
        private const val TAG = "Base64ImageMapper"
        // Characters that are valid in base64 but indicate raw base64 content
        private val BASE64_PATTERN = Regex("^[A-Za-z0-9+/=]+$")
    }
    
    override fun map(data: String, options: Options): Base64ImageUrl? {
        // Reduce logging to avoid performance overhead
        // android.util.Log.d(TAG, "Mapping input length: ${data.length}")
        
        return when {
            // Format 1: base64://imageId (old legacy format)
            data.startsWith("base64://") -> {
                val imageId = data.removePrefix("base64://")
                Base64ImageUrl("LEGACY:$imageId")
            }
            // Format 2 & 3: data:image/...;base64,<content>
            // Need to distinguish between imageId (short) and actual data (long)
            data.startsWith("data:image/") && data.contains("base64,") -> {
                val commaIndex = data.indexOf(",")
                if (commaIndex == -1 || commaIndex >= data.length - 1) {
                    return null
                }
                
                val afterComma = data.substring(commaIndex + 1)
                val isInlineData = afterComma.length > BASE64_DATA_MIN_LENGTH
                
                if (isInlineData) {
                    // This is actual base64 image data (from WorkManager)
                    Base64ImageUrl("INLINE:$data")
                } else {
                    // This is just an imageId (from app upload) - need to fetch from Firestore
                    Base64ImageUrl("LEGACY:$afterComma")
                }
            }
            // Format 4: RAW base64 string (from LocketFirebaseRepository)
            // This is base64 data stored directly without any prefix
            data.length > BASE64_DATA_MIN_LENGTH && isLikelyBase64(data) -> {
                Base64ImageUrl("RAW:$data")
            }
            else -> null
        }
    }
    
    /**
     * Check if string looks like raw base64 encoded data
     */
    private fun isLikelyBase64(data: String): Boolean {
        // Check first and last part for base64 characters
        // Full validation would be expensive for large strings
        val sample = data.take(100) + data.takeLast(100)
        return sample.all { c -> 
            c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '+' || c == '/' || c == '='
        }
    }
}

/**
 * Custom Coil Fetcher to handle base64 image URLs stored in Firestore
 * Memory optimized with reduced logging
 */
class Base64ImageFetcher(
    private val data: Base64ImageUrl,
    private val options: Options
) : Fetcher {
    
    companion object {
        private const val TAG = "Base64ImageFetcher"
    }
    
    override suspend fun fetch(): FetchResult {
        val imageId = data.imageId
        
        val base64Data: String = when {
            imageId.startsWith("INLINE:") -> {
                // Remove marker and get actual data URL
                val dataUrl = imageId.removePrefix("INLINE:")
                val commaIndex = dataUrl.indexOf(",")
                if (commaIndex == -1 || commaIndex >= dataUrl.length - 1) {
                    throw Exception("Invalid inline base64 format: no data after comma")
                }
                dataUrl.substring(commaIndex + 1)
            }
            imageId.startsWith("RAW:") -> {
                // Raw base64 data stored directly (from LocketFirebaseRepository)
                imageId.removePrefix("RAW:")
            }
            imageId.startsWith("LEGACY:") -> {
                // Remove marker and fetch from Firestore
                val docId = imageId.removePrefix("LEGACY:")
                
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection(FirebaseStorageRepository.IMAGES_COLLECTION)
                    .document(docId)
                    .get()
                    .await()
                
                if (!doc.exists()) {
                    throw Exception("Image not found: $docId")
                }
                
                val fetchedData = doc.getString("data")
                if (fetchedData.isNullOrEmpty()) {
                    throw Exception("Image data is empty")
                }
                fetchedData
            }
            else -> {
                throw Exception("Unknown image ID format")
            }
        }
        
        // Decode base64 to bytes
        val bytes = Base64.decode(base64Data, Base64.NO_WRAP)
        
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
            return Base64ImageFetcher(data, options)
        }
    }
}

/**
 * Utility object to decode base64 images
 */
object Base64ImageDecoder {
    
    /**
     * Decode base64 string to Bitmap with memory-safe handling
     * Uses sampling to prevent OOM on low-RAM devices
     */
    fun decodeBase64ToBitmap(base64: String, maxWidth: Int = 1024, maxHeight: Int = 1024): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            
            // First, decode just the bounds to calculate sample size
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)
            
            // Calculate sample size for memory efficiency
            val sampleSize = calculateInSampleSize(boundsOptions, maxWidth, maxHeight)
            
            // Now decode with the calculated sample size
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // Uses less memory than ARGB_8888
            }
            
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("Base64ImageDecoder", "OOM while decoding base64, trying with smaller size", e)
            // Try again with more aggressive sampling
            try {
                val bytes = Base64.decode(base64, Base64.NO_WRAP)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = 4 // Reduce to 1/4 size
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            } catch (e2: Exception) {
                android.util.Log.e("Base64ImageDecoder", "Failed to decode even with reduced size", e2)
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("Base64ImageDecoder", "Failed to decode base64", e)
            null
        }
    }
    
    /**
     * Calculate an appropriate sample size for BitmapFactory
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
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
 * with memory optimization for low-RAM devices
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
        // Memory optimization for low-RAM devices like Xiaomi Redmi Note 5
        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
        .diskCachePolicy(coil.request.CachePolicy.ENABLED)
        // Limit memory cache size (default is 25% of app memory, reduce to 15% for low-RAM)
        .memoryCache {
            coil.memory.MemoryCache.Builder(context)
                .maxSizePercent(0.15) // 15% of available memory
                .build()
        }
        // Enable bitmap pooling to reduce allocations
        .bitmapFactoryMaxParallelism(2) // Limit parallel decoding
        .build()
}

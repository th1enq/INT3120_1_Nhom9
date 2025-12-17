package com.example.coupleapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * Helper class for image cropping to circular shape
 */
object ImageCropHelper {
    
    /**
     * Crop image to circular shape
     * @param bitmap Original bitmap
     * @return Circular cropped bitmap
     */
    fun cropToCircle(bitmap: Bitmap): Bitmap {
        // Get the smaller dimension for square cropping
        val size = min(bitmap.width, bitmap.height)
        
        // Create square bitmap first (center crop)
        val squareBitmap = centerCropToSquare(bitmap)
        
        // Create circular bitmap
        val outputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outputBitmap)
        
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
            isDither = true
        }
        
        // Draw circle
        canvas.drawCircle(
            size / 2f,
            size / 2f,
            size / 2f,
            paint
        )
        
        // Set xfermode to draw image only within circle
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        
        // Draw the square bitmap
        canvas.drawBitmap(squareBitmap, 0f, 0f, paint)
        
        // Cleanup
        if (squareBitmap != bitmap) {
            squareBitmap.recycle()
        }
        
        return outputBitmap
    }
    
    /**
     * Center crop bitmap to square
     */
    private fun centerCropToSquare(bitmap: Bitmap): Bitmap {
        val size = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - size) / 2
        val y = (bitmap.height - size) / 2
        
        return Bitmap.createBitmap(bitmap, x, y, size, size)
    }
    
    /**
     * Crop bitmap with specified rect
     */
    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        return Bitmap.createBitmap(
            bitmap,
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            (rect.width()).coerceIn(1, bitmap.width - rect.left.coerceIn(0, bitmap.width - 1)),
            (rect.height()).coerceIn(1, bitmap.height - rect.top.coerceIn(0, bitmap.height - 1))
        )
    }
    
    /**
     * Load and resize bitmap from URI
     */
    fun loadBitmapFromUri(context: Context, uri: Uri, maxSize: Int = 1024): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.use { stream ->
                // First, decode bounds only to get image dimensions
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeStream(stream, null, options)
                
                // Calculate sample size
                options.inSampleSize = calculateInSampleSize(options, maxSize, maxSize)
                options.inJustDecodeBounds = false
                
                // Re-open stream and decode
                context.contentResolver.openInputStream(uri)?.use { newStream ->
                    BitmapFactory.decodeStream(newStream, null, options)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageCropHelper", "Error loading bitmap", e)
            null
        }
    }
    
    /**
     * Calculate sample size for downsampling
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
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
     * Save bitmap to cache directory and return file
     */
    fun saveBitmapToCache(context: Context, bitmap: Bitmap, filename: String = "cropped_image.jpg"): File? {
        return try {
            val cacheDir = context.cacheDir
            val file = File(cacheDir, filename)
            
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            
            file
        } catch (e: Exception) {
            android.util.Log.e("ImageCropHelper", "Error saving bitmap", e)
            null
        }
    }
    
    /**
     * Save bitmap to cache and return URI
     */
    fun saveBitmapToCacheUri(context: Context, bitmap: Bitmap, filename: String = "cropped_image.jpg"): Uri? {
        val file = saveBitmapToCache(context, bitmap, filename) ?: return null
        return Uri.fromFile(file)
    }
}

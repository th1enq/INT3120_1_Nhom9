package com.example.coupleapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.coupleapp.data.local.dao.*
import com.example.coupleapp.data.local.entity.*

/**
 * Room Database for CoupleApp.
 * Acts as the single source of truth for synced partner data.
 * 
 * Benefits:
 * - Offline-first: Widget always has data even without network
 * - Reactive: Flow-based queries enable automatic widget updates
 * - Battery efficient: Reduces network calls with local caching
 * - Doze-mode friendly: Workers can read/write even in restricted states
 */
@Database(
    entities = [
        PartnerSleepEntity::class,
        PartnerLocationEntity::class,
        PartnerPhotoEntity::class,
        SyncMetadataEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class CoupleAppDatabase : RoomDatabase() {
    
    abstract fun partnerSleepDao(): PartnerSleepDao
    abstract fun partnerLocationDao(): PartnerLocationDao
    abstract fun partnerPhotoDao(): PartnerPhotoDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    
    companion object {
        private const val DATABASE_NAME = "couple_app_database"
        
        @Volatile
        private var INSTANCE: CoupleAppDatabase? = null
        
        /**
         * Get singleton instance of the database.
         * Thread-safe with double-checked locking.
         */
        fun getInstance(context: Context): CoupleAppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): CoupleAppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CoupleAppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration() // For development; use proper migrations in production
                .enableMultiInstanceInvalidation() // For widget process isolation
                .build()
        }
        
        /**
         * Close database instance.
         * Call when user logs out.
         */
        fun closeDatabase() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}

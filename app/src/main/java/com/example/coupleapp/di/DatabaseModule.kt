package com.example.coupleapp.di

import android.content.Context
import com.example.coupleapp.data.local.CoupleAppDatabase
import com.example.coupleapp.data.local.dao.*
import com.example.coupleapp.data.sync.PartnerSyncRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing database and repository dependencies.
 * 
 * This module provides:
 * - Room Database instance (singleton)
 * - DAO instances for each table
 * - Repository instances
 * 
 * Usage with Hilt:
 * - Annotate Application class with @HiltAndroidApp
 * - Annotate Activities/Fragments with @AndroidEntryPoint
 * - Inject dependencies using @Inject constructor or field injection
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    /**
     * Provide singleton Room Database instance.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): CoupleAppDatabase {
        return CoupleAppDatabase.getInstance(context)
    }
    
    /**
     * Provide PartnerSleepDao.
     */
    @Provides
    @Singleton
    fun providePartnerSleepDao(
        database: CoupleAppDatabase
    ): PartnerSleepDao {
        return database.partnerSleepDao()
    }
    
    /**
     * Provide PartnerLocationDao.
     */
    @Provides
    @Singleton
    fun providePartnerLocationDao(
        database: CoupleAppDatabase
    ): PartnerLocationDao {
        return database.partnerLocationDao()
    }
    
    /**
     * Provide PartnerPhotoDao.
     */
    @Provides
    @Singleton
    fun providePartnerPhotoDao(
        database: CoupleAppDatabase
    ): PartnerPhotoDao {
        return database.partnerPhotoDao()
    }
    
    /**
     * Provide SyncMetadataDao.
     */
    @Provides
    @Singleton
    fun provideSyncMetadataDao(
        database: CoupleAppDatabase
    ): SyncMetadataDao {
        return database.syncMetadataDao()
    }
    
    /**
     * Provide PartnerSyncRepository.
     */
    @Provides
    @Singleton
    fun providePartnerSyncRepository(
        @ApplicationContext context: Context
    ): PartnerSyncRepository {
        return PartnerSyncRepository.getInstance(context)
    }
}

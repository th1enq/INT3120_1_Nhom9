package com.example.coupleapp.di

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.coupleapp.data.sync.PartnerSyncRepository
import com.example.coupleapp.worker.PartnerDataSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Custom WorkerFactory for Hilt dependency injection in Workers.
 * 
 * This allows Workers to receive dependencies via constructor injection.
 * Without this, Workers would need to manually retrieve dependencies.
 * 
 * Usage:
 * 1. Register this factory in WorkManager configuration
 * 2. Workers can then have @Inject constructor with dependencies
 */
@Singleton
class HiltWorkerFactory @Inject constructor(
    private val partnerSyncRepository: PartnerSyncRepository
) : WorkerFactory() {
    
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        // Return null to fall back to default reflection-based instantiation
        // This is fine for workers that don't need DI
        // For workers that need DI, we can add specific cases here
        
        return when (workerClassName) {
            PartnerDataSyncWorker::class.java.name -> {
                // PartnerDataSyncWorker doesn't use constructor injection currently
                // It gets repository via singleton pattern
                // Return null to use default instantiation
                null
            }
            else -> null
        }
    }
}

package com.example.coupleapp.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Repository for Firebase Firestore operations
 */
class FirebaseFirestoreRepository {
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    // Collection references
    companion object {
        const val USERS_COLLECTION = "users"
        const val COUPLES_COLLECTION = "couples"
        const val MESSAGES_COLLECTION = "messages"
        const val MOMENTS_COLLECTION = "moments"
        const val SLEEP_RECORDS_COLLECTION = "sleep_records"
        const val LOCKET_POSTS_COLLECTION = "locket_posts"
        const val LOCATIONS_COLLECTION = "locations"
        const val SHARED_PLACES_COLLECTION = "shared_places"
        const val QA_QUESTIONS_COLLECTION = "qa_questions"
        const val CALENDAR_EVENTS_COLLECTION = "calendar_events"
    }

    /**
     * Add a new document to a collection
     */
    suspend fun <T> addDocument(collection: String, data: T): Result<String> {
        return try {
            val docRef = db.collection(collection).add(data as Any).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set a document with specific ID
     */
    suspend fun <T> setDocument(collection: String, documentId: String, data: T, merge: Boolean = false): Result<Unit> {
        return try {
            if (merge) {
                db.collection(collection).document(documentId).set(data as Any, SetOptions.merge()).await()
            } else {
                db.collection(collection).document(documentId).set(data as Any).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single document
     */
    suspend fun <T> getDocument(collection: String, documentId: String, clazz: Class<T>): Result<T?> {
        return try {
            val snapshot = db.collection(collection).document(documentId).get().await()
            Result.success(snapshot.toObject(clazz))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update specific fields in a document
     */
    suspend fun updateDocument(collection: String, documentId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a document
     */
    suspend fun deleteDocument(collection: String, documentId: String): Result<Unit> {
        return try {
            db.collection(collection).document(documentId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all documents from a collection
     */
    suspend fun <T> getCollection(collection: String, clazz: Class<T>): Result<List<T>> {
        return try {
            val snapshot = db.collection(collection).get().await()
            val items = snapshot.documents.mapNotNull { it.toObject(clazz) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Query documents with a where clause
     */
    suspend fun <T> queryDocuments(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>
    ): Result<List<T>> {
        return try {
            val snapshot = db.collection(collection)
                .whereEqualTo(field, value)
                .get()
                .await()
            val items = snapshot.documents.mapNotNull { it.toObject(clazz) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Listen to a document in real-time
     */
    fun <T> listenToDocument(collection: String, documentId: String, clazz: Class<T>): Flow<T?> = callbackFlow {
        val subscription = db.collection(collection).document(documentId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toObject(clazz))
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Listen to a collection in real-time
     */
    fun <T> listenToCollection(collection: String, clazz: Class<T>): Flow<List<T>> = callbackFlow {
        val subscription = db.collection(collection)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val items = snapshot?.documents?.mapNotNull { it.toObject(clazz) } ?: emptyList()
                trySend(items)
            }

        awaitClose { subscription.remove() }
    }

    /**
     * Listen to a query in real-time
     */
    fun <T> listenToQuery(
        collection: String,
        field: String,
        value: Any,
        clazz: Class<T>,
        orderBy: String? = null,
        descending: Boolean = false
    ): Flow<List<T>> = callbackFlow {
        var query: Query = db.collection(collection).whereEqualTo(field, value)
        
        if (orderBy != null) {
            query = if (descending) {
                query.orderBy(orderBy, Query.Direction.DESCENDING)
            } else {
                query.orderBy(orderBy, Query.Direction.ASCENDING)
            }
        }

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { it.toObject(clazz) } ?: emptyList()
            trySend(items)
        }

        awaitClose { subscription.remove() }
    }

    /**
     * Batch write operations
     */
    suspend fun batchWrite(operations: (com.google.firebase.firestore.WriteBatch) -> Unit): Result<Unit> {
        return try {
            val batch = db.batch()
            operations(batch)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

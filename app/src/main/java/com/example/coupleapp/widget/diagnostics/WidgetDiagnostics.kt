package com.example.coupleapp.widget.diagnostics

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Widget Diagnostic Tool
 * Helps identify common widget loading issues
 */
object WidgetDiagnostics {
    private const val TAG = "WidgetDiagnostics"
    private const val TIMEOUT_MS = 10_000L // 10 seconds timeout
    
    /**
     * Run comprehensive widget health check
     */
    suspend fun runHealthCheck(context: Context): WidgetHealthReport {
        return withContext(Dispatchers.IO) {
            val report = WidgetHealthReport()
            
            // 1. Check Authentication
            report.authStatus = checkAuthenticationStatus()
            
            // 2. Check Network/Firebase connectivity
            report.networkStatus = checkNetworkConnectivity()
            
            // 3. Check Partner relationship
            report.partnerStatus = checkPartnerStatus()
            
            // 4. Check Widget permissions
            report.permissionStatus = checkWidgetPermissions(context)
            
            Log.d(TAG, "Widget health check completed: $report")
            report
        }
    }
    
    private suspend fun checkAuthenticationStatus(): AuthStatus {
        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser
            
            when {
                currentUser == null -> AuthStatus.NOT_AUTHENTICATED
                currentUser.isAnonymous -> AuthStatus.ANONYMOUS
                else -> AuthStatus.AUTHENTICATED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking auth status", e)
            AuthStatus.ERROR
        }
    }
    
    private suspend fun checkNetworkConnectivity(): NetworkStatus {
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val db = FirebaseFirestore.getInstance()
                // Try a simple read operation
                db.collection("system").document("health_check").get().await()
                NetworkStatus.CONNECTED
            } ?: NetworkStatus.TIMEOUT
        } catch (e: Exception) {
            Log.e(TAG, "Network connectivity check failed", e)
            when {
                e.message?.contains("network", ignoreCase = true) == true -> NetworkStatus.NO_NETWORK
                e.message?.contains("permission", ignoreCase = true) == true -> NetworkStatus.PERMISSION_DENIED
                else -> NetworkStatus.ERROR
            }
        }
    }
    
    private suspend fun checkPartnerStatus(): PartnerStatus {
        return try {
            val auth = FirebaseAuth.getInstance()
            val currentUser = auth.currentUser ?: return PartnerStatus.NO_AUTH
            
            withTimeoutOrNull(TIMEOUT_MS) {
                val db = FirebaseFirestore.getInstance()
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val partnerId = userDoc.getString("partnerId")
                
                when {
                    partnerId.isNullOrEmpty() -> PartnerStatus.NO_PARTNER
                    else -> {
                        // Check if partner exists
                        val partnerDoc = db.collection("users").document(partnerId).get().await()
                        if (partnerDoc.exists()) {
                            PartnerStatus.PARTNER_FOUND
                        } else {
                            PartnerStatus.PARTNER_INVALID
                        }
                    }
                }
            } ?: PartnerStatus.TIMEOUT
        } catch (e: Exception) {
            Log.e(TAG, "Error checking partner status", e)
            PartnerStatus.ERROR
        }
    }
    
    private fun checkWidgetPermissions(context: Context): PermissionStatus {
        return try {
            // Check basic widget permissions
            // For now, return OK - can be expanded based on specific permissions needed
            PermissionStatus.OK
        } catch (e: Exception) {
            Log.e(TAG, "Error checking widget permissions", e)
            PermissionStatus.ERROR
        }
    }
}

data class WidgetHealthReport(
    var authStatus: AuthStatus = AuthStatus.UNKNOWN,
    var networkStatus: NetworkStatus = NetworkStatus.UNKNOWN,
    var partnerStatus: PartnerStatus = PartnerStatus.UNKNOWN,
    var permissionStatus: PermissionStatus = PermissionStatus.UNKNOWN
) {
    fun hasAnyIssues(): Boolean {
        return authStatus == AuthStatus.NOT_AUTHENTICATED || 
               authStatus == AuthStatus.ERROR ||
               networkStatus == NetworkStatus.NO_NETWORK ||
               networkStatus == NetworkStatus.ERROR ||
               partnerStatus == PartnerStatus.NO_PARTNER ||
               partnerStatus == PartnerStatus.ERROR ||
               permissionStatus == PermissionStatus.ERROR
    }
    
    fun getMainIssue(): String? {
        return when {
            authStatus == AuthStatus.NOT_AUTHENTICATED -> "Cần đăng nhập lại"
            networkStatus == NetworkStatus.NO_NETWORK -> "Không có kết nối mạng"
            partnerStatus == PartnerStatus.NO_PARTNER -> "Chưa có người yêu"
            permissionStatus == PermissionStatus.ERROR -> "Lỗi quyền truy cập"
            else -> null
        }
    }
}

enum class AuthStatus {
    UNKNOWN, NOT_AUTHENTICATED, ANONYMOUS, AUTHENTICATED, ERROR
}

enum class NetworkStatus {
    UNKNOWN, CONNECTED, NO_NETWORK, TIMEOUT, PERMISSION_DENIED, ERROR
}

enum class PartnerStatus {
    UNKNOWN, NO_AUTH, NO_PARTNER, PARTNER_FOUND, PARTNER_INVALID, TIMEOUT, ERROR
}

enum class PermissionStatus {
    UNKNOWN, OK, ERROR
}
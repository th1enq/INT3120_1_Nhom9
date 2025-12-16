package com.example.coupleapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Repository for Firebase Authentication operations
 */
class FirebaseAuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isUserLoggedIn: Boolean
        get() = currentUser != null

    /**
     * Sign in with email and password
     */
    suspend fun signInWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Không thể đăng nhập"))
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is FirebaseAuthException -> {
                    when (e.errorCode) {
                        "ERROR_INVALID_EMAIL" -> "Email không hợp lệ"
                        "ERROR_WRONG_PASSWORD" -> "Số điện thoại hoặc mật khẩu không đúng"
                        "ERROR_USER_NOT_FOUND" -> "Tài khoản không tồn tại. Vui lòng đăng ký"
                        "ERROR_USER_DISABLED" -> "Tài khoản đã bị vô hiệu hóa"
                        "ERROR_TOO_MANY_REQUESTS" -> "Quá nhiều lần thử. Vui lòng thử lại sau"
                        "ERROR_INVALID_CREDENTIAL" -> "Số điện thoại hoặc mật khẩu không đúng"
                        else -> "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin"
                    }
                }
                else -> "Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Sign in with phone number credential
     */
    suspend fun signInWithPhoneCredential(credential: PhoneAuthCredential): Result<FirebaseUser> {
        return try {
            val result = auth.signInWithCredential(credential).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("User is null"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create new user with email and password
     */
    suspend fun createUserWithEmailAndPassword(email: String, password: String): Result<FirebaseUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let {
                Result.success(it)
            } ?: Result.failure(Exception("Không thể tạo tài khoản"))
        } catch (e: Exception) {
            val errorMessage = when (e) {
                is FirebaseAuthException -> {
                    when (e.errorCode) {
                        "ERROR_EMAIL_ALREADY_IN_USE" -> "Số điện thoại này đã được đăng ký"
                        "ERROR_INVALID_EMAIL" -> "Số điện thoại không hợp lệ"
                        "ERROR_WEAK_PASSWORD" -> "Mật khẩu quá yếu. Vui lòng dùng mật khẩu mạnh hơn"
                        "ERROR_TOO_MANY_REQUESTS" -> "Quá nhiều lần thử. Vui lòng thử lại sau"
                        else -> "Đăng ký thất bại. Vui lòng thử lại"
                    }
                }
                else -> "Đăng ký thất bại. Vui lòng thử lại"
            }
            Result.failure(Exception(errorMessage))
        }
    }

    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update user password
     */
    suspend fun updatePassword(newPassword: String): Result<Unit> {
        return try {
            currentUser?.updatePassword(newPassword)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete current user account
     */
    suspend fun deleteAccount(): Result<Unit> {
        return try {
            currentUser?.delete()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

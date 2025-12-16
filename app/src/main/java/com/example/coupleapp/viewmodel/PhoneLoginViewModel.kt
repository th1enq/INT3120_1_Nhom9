package com.example.coupleapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * ViewModel for Phone Login Screen
 * Manages login state, validation, and user interactions
 */
class PhoneLoginViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(PhoneLoginUiState())
    val uiState: StateFlow<PhoneLoginUiState> = _uiState.asStateFlow()
    
    /**
     * Update phone number and validate
     */
    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, phoneError = null) }
        validatePhone(phone)
    }
    
    /**
     * Update password
     */
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
    }
    
    /**
     * Validate phone number
     */
    private fun validatePhone(phone: String) {
        val error = when {
            phone.isEmpty() -> null // Don't show error on empty unless submitted
            !phone.matches(Regex("^0[0-9]{9,10}$")) -> "Invalid phone number"
            else -> null
        }
        _uiState.update { it.copy(phoneError = error) }
    }
    
    /**
     * Validate password
     */
    private fun validatePassword(): Boolean {
        val password = _uiState.value.password
        val error = when {
            password.isEmpty() -> "Password is required"
            password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        _uiState.update { it.copy(passwordError = error) }
        return error == null
    }
    
    /**
     * Perform login (Frontend validation only)
     * Backend authentication will be handled by BE team
     */
    fun login(onSuccess: () -> Unit) {
        // Validate all fields
        val phone = _uiState.value.phoneNumber
        val phoneError = when {
            phone.isEmpty() -> "Phone number is required"
            !phone.matches(Regex("^0[0-9]{9,10}$")) -> "Invalid phone number"
            else -> null
        }
        
        _uiState.update { it.copy(phoneError = phoneError) }
        
        val isPasswordValid = validatePassword()
        
        if (phoneError != null || !isPasswordValid) {
            return
        }
        
        // Show loading state
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                // Simulate network delay (Backend will handle actual authentication)
                delay(1500)
                
                // Mock success for now
                _uiState.update { 
                    it.copy(isLoading = false, loginSuccess = true) 
                }
                onSuccess()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "An error occurred. Please try again."
                    ) 
                }
            }
        }
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Check if form is valid
     */
    fun isFormValid(): Boolean {
        val state = _uiState.value
        return state.phoneNumber.isNotEmpty() && 
               state.password.isNotEmpty() &&
               state.phoneError == null &&
               state.passwordError == null
    }
}

/**
 * UI State for Phone Login Screen
 */
data class PhoneLoginUiState(
    val phoneNumber: String = "",
    val password: String = "",
    val phoneError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val loginSuccess: Boolean = false,
    val errorMessage: String? = null
)

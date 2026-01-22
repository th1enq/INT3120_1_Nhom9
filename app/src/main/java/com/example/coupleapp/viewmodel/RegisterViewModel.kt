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
 * ViewModel for Register Screen
 * Manages registration state, validation, and user interactions
 */
class RegisterViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()
    
    /**
     * Update full name
     */
    fun updateFullName(name: String) {
        _uiState.update { it.copy(fullName = name, fullNameError = null) }
    }
    
    /**
     * Update date of birth
     * Stores only digits (DDMMYYYY), formatting is done by DateTextField VisualTransformation
     */
    fun updateDateOfBirth(date: String) {
        // Store only digits (max 8 for DDMMYYYY)
        val digits = date.filter { it.isDigit() }.take(8)
        _uiState.update { it.copy(dateOfBirth = digits, dateOfBirthError = null) }
    }
    
    /**
     * Set date of birth from DatePicker (already formatted)
     */
    fun setDateOfBirth(day: Int, month: Int, year: Int) {
        // Store as digits only: DDMMYYYY
        val formatted = String.format("%02d%02d%04d", day, month, year)
        _uiState.update { it.copy(dateOfBirth = formatted, dateOfBirthError = null) }
    }
    
    /**
     * Update phone number
     */
    fun updatePhone(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, phoneError = null) }
    }
    
    /**
     * Update password
     */
    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password, passwordError = null) }
        // Revalidate confirm password if it's already filled
        if (_uiState.value.confirmPassword.isNotEmpty()) {
            validateConfirmPassword()
        }
    }
    
    /**
     * Update confirm password
     */
    fun updateConfirmPassword(confirmPassword: String) {
        _uiState.update { it.copy(confirmPassword = confirmPassword, confirmPasswordError = null) }
    }
    
    /**
     * Validate all fields
     */
    private fun validateAllFields(): Boolean {
        val state = _uiState.value
        var isValid = true
        
        // Validate full name
        val nameError = when {
            state.fullName.isEmpty() -> "Full name is required"
            state.fullName.length < 3 -> "Full name must be at least 3 characters"
            else -> null
        }
        if (nameError != null) isValid = false
        
        // Validate date of birth (now stored as 8 digits: DDMMYYYY)
        val dateError = when {
            state.dateOfBirth.isEmpty() -> "Date of birth is required"
            state.dateOfBirth.length != 8 -> "Invalid date format (DD/MM/YYYY)"
            !state.dateOfBirth.matches(Regex("^\\d{8}$")) -> "Invalid date format (DD/MM/YYYY)"
            else -> {
                // Validate day and month values
                val day = state.dateOfBirth.substring(0, 2).toIntOrNull() ?: 0
                val month = state.dateOfBirth.substring(2, 4).toIntOrNull() ?: 0
                when {
                    day < 1 || day > 31 -> "Invalid day (01-31)"
                    month < 1 || month > 12 -> "Invalid month (01-12)"
                    else -> null
                }
            }
        }
        if (dateError != null) isValid = false
        
        // Validate phone
        val phoneError = when {
            state.phoneNumber.isEmpty() -> "Phone number is required"
            !state.phoneNumber.matches(Regex("^0[0-9]{9,10}$")) -> "Invalid phone number"
            else -> null
        }
        if (phoneError != null) isValid = false
        
        // Validate password
        val passwordError = when {
            state.password.isEmpty() -> "Password is required"
            state.password.length < 6 -> "Password must be at least 6 characters"
            else -> null
        }
        if (passwordError != null) isValid = false
        
        // Validate confirm password
        val confirmError = when {
            state.confirmPassword.isEmpty() -> "Please confirm your password"
            state.confirmPassword != state.password -> "Passwords do not match"
            else -> null
        }
        if (confirmError != null) isValid = false
        
        _uiState.update {
            it.copy(
                fullNameError = nameError,
                dateOfBirthError = dateError,
                phoneError = phoneError,
                passwordError = passwordError,
                confirmPasswordError = confirmError
            )
        }
        
        return isValid
    }
    
    /**
     * Validate confirm password
     */
    private fun validateConfirmPassword() {
        val state = _uiState.value
        val error = when {
            state.confirmPassword.isEmpty() -> null
            state.confirmPassword != state.password -> "Passwords do not match"
            else -> null
        }
        _uiState.update { it.copy(confirmPasswordError = error) }
    }
    
    /**
     * Perform registration (Frontend validation only)
     * Backend registration will be handled by BE team
     */
    fun register(onSuccess: () -> Unit) {
        if (!validateAllFields()) {
            return
        }
        
        // Show loading state
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        
        viewModelScope.launch {
            try {
                // Minimal delay for visual feedback (Backend will handle actual registration)
                delay(300)
                
                // Mock success for now
                _uiState.update { 
                    it.copy(isLoading = false, registerSuccess = true) 
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
        return state.fullName.isNotEmpty() && 
               state.dateOfBirth.isNotEmpty() && 
               state.phoneNumber.isNotEmpty() && 
               state.password.isNotEmpty() && 
               state.confirmPassword.isNotEmpty() &&
               state.password == state.confirmPassword &&
               state.fullNameError == null &&
               state.dateOfBirthError == null &&
               state.phoneError == null &&
               state.passwordError == null &&
               state.confirmPasswordError == null
    }
}

/**
 * UI State for Register Screen
 */
data class RegisterUiState(
    val fullName: String = "",
    val dateOfBirth: String = "",
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val fullNameError: String? = null,
    val dateOfBirthError: String? = null,
    val phoneError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val isLoading: Boolean = false,
    val registerSuccess: Boolean = false,
    val errorMessage: String? = null
)

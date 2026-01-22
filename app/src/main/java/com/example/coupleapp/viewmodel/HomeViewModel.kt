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
 * ViewModel for Home Screen
 * Manages home screen state and data
 */
class HomeViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    init {
        loadHomeData()
    }
    
    /**
     * Load home screen data
     */
    private fun loadHomeData() {
        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            try {
                // No fake delay - load data immediately for better UX
                // Mock data loading (Backend will provide actual data)
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        isDataLoaded = true
                    ) 
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        errorMessage = "Unable to load data. Please try again."
                    ) 
                }
            }
        }
    }
    
    /**
     * Refresh home data (for pull-to-refresh)
     */
    fun refreshData() {
        _uiState.update { it.copy(isRefreshing = true) }
        
        viewModelScope.launch {
            try {
                // Minimal delay for visual feedback on pull-to-refresh
                delay(300)
                
                _uiState.update { 
                    it.copy(
                        isRefreshing = false,
                        isDataLoaded = true,
                        errorMessage = null
                    ) 
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isRefreshing = false, 
                        errorMessage = "Unable to refresh data."
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
     * Update selected bottom navigation item
     */
    fun updateSelectedNavItem(item: String) {
        _uiState.update { it.copy(selectedBottomNavItem = item) }
    }
}

/**
 * UI State for Home Screen
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isDataLoaded: Boolean = false,
    val selectedBottomNavItem: String = "HOME",
    val errorMessage: String? = null
)

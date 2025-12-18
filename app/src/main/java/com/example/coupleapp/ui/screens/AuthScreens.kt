package com.example.coupleapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.ui.components.CustomSnackbar
import com.example.coupleapp.ui.components.SnackbarType
import com.example.coupleapp.viewmodel.AuthState
import com.example.coupleapp.viewmodel.AuthViewModel
import kotlinx.coroutines.delay

/**
 * Wrapper screen for Register with Firebase integration
 */
@Composable
fun RegisterWithFirebaseScreen(
    onRegisterSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    var showError by remember { mutableStateOf<String?>(null) }
    var isRegistering by remember { mutableStateOf(false) }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                isRegistering = false
                onRegisterSuccess()
            }
            is AuthState.Error -> {
                isRegistering = false
                showError = (authState as AuthState.Error).message
                delay(3000)
                showError = null
                authViewModel.clearError()
            }
            is AuthState.Loading -> {
                isRegistering = true
            }
            else -> {}
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        RegisterScreen(
            onRegisterClick = { fullName, dateOfBirth, phone, password, confirmPassword ->
                if (password != confirmPassword) {
                    showError = "Mật khẩu xác nhận không khớp"
                } else if (password.length < 6) {
                    showError = "Mật khẩu phải có ít nhất 6 ký tự"
                } else {
                    // Convert phone to email format for Firebase Auth
                    val email = "${phone}@coupleapp.temp"
                    
                    // Format date of birth from DDMMYYYY to DD/MM/YYYY for storage
                    val formattedDob = if (dateOfBirth.length == 8) {
                        "${dateOfBirth.substring(0, 2)}/${dateOfBirth.substring(2, 4)}/${dateOfBirth.substring(4, 8)}"
                    } else {
                        dateOfBirth
                    }
                    
                    // Call AuthViewModel to register
                    authViewModel.registerUser(
                        email = email,
                        password = password,
                        fullName = fullName,
                        phoneNumber = phone,
                        dateOfBirth = formattedDob,
                        gender = "" // Can add gender selection later
                    )
                }
            },
            onBackClick = onBackClick,
            onLoginClick = onLoginClick
        )
        
        // Loading overlay
        if (isRegistering) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.creating_account),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Error message
        showError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                CustomSnackbar(
                    message = error,
                    type = SnackbarType.ERROR,
                    onDismiss = { showError = null }
                )
            }
        }
    }
}

/**
 * Wrapper screen for Login with Firebase integration
 */
@Composable
fun LoginWithFirebaseScreen(
    onLoginSuccess: () -> Unit,
    onBackClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    authViewModel: AuthViewModel = viewModel()
) {
    val authState by authViewModel.authState.collectAsState()
    var showError by remember { mutableStateOf<String?>(null) }
    var isLoggingIn by remember { mutableStateOf(false) }
    
    // Handle auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                isLoggingIn = false
                onLoginSuccess()
            }
            is AuthState.Error -> {
                isLoggingIn = false
                showError = (authState as AuthState.Error).message
                delay(3000)
                showError = null
                authViewModel.clearError()
            }
            is AuthState.Loading -> {
                isLoggingIn = true
            }
            else -> {}
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        PhoneLoginScreen(
            onLoginClick = { phone, password ->
                // Convert phone to email format for Firebase Auth
                val email = "${phone}@coupleapp.temp"
                
                // Call AuthViewModel to login
                authViewModel.signInWithEmail(email, password)
            },
            onBackClick = onBackClick,
            onForgotPasswordClick = onForgotPasswordClick
        )
        
        // Loading overlay
        if (isLoggingIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .padding(32.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.logging_in),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        
        // Error message
        showError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Spacer(modifier = Modifier.height(60.dp))
                CustomSnackbar(
                    message = error,
                    type = SnackbarType.ERROR,
                    onDismiss = { showError = null }
                )
            }
        }
    }
}

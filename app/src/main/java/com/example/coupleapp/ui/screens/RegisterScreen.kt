package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.ui.components.CustomTextField
import com.example.coupleapp.ui.components.GradientButton
import com.example.coupleapp.ui.components.CustomSnackbar
import com.example.coupleapp.ui.components.SnackbarType
import com.example.coupleapp.viewmodel.RegisterViewModel
import kotlinx.coroutines.delay

@Composable
fun RegisterScreen(
    onRegisterClick: (String, String, String, String, String) -> Unit,
    onBackClick: () -> Unit,
    onLoginClick: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFFFF5F8),
                        Color(0xFFFFFBF5),
                        Color(0xFFF5F8FF)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            
            // Back button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "← Back",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF757575),
                    modifier = Modifier.clickable { onBackClick() }
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Title Section
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800)) + 
                        slideInVertically(animationSpec = tween(800)) { -it }
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Avatar placeholder
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFFFB8D6),
                                        Color(0xFFFFD6E8)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "♥",
                            fontSize = 40.sp,
                            color = Color.White
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Create Account",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = Color(0xFF2D2D2D)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Fill in the information to start your journey",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Form Section
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(800, delayMillis = 200)) + 
                        slideInVertically(animationSpec = tween(800, delayMillis = 200)) { it }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Full Name Field
                    CustomTextField(
                        value = uiState.fullName,
                        onValueChange = { viewModel.updateFullName(it) },
                        placeholder = "Full name",
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.fullNameError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Date of Birth Field
                    CustomTextField(
                        value = uiState.dateOfBirth,
                        onValueChange = { viewModel.updateDateOfBirth(it) },
                        placeholder = "Date of birth (DD/MM/YYYY)",
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.dateOfBirthError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Phone Number Field
                    CustomTextField(
                        value = uiState.phoneNumber,
                        onValueChange = { viewModel.updatePhone(it) },
                        placeholder = "Phone number",
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.phoneError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Field
                    CustomTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        placeholder = "Password",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.passwordError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Confirm Password Field
                    CustomTextField(
                        value = uiState.confirmPassword,
                        onValueChange = { viewModel.updateConfirmPassword(it) },
                        placeholder = "Confirm password",
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.confirmPasswordError
                    )
                    
                    // Password match indicator
                    if (uiState.confirmPassword.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.password == uiState.confirmPassword) "✓ Passwords match" else "✗ Passwords do not match",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.password == uiState.confirmPassword) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Register Button
                    GradientButton(
                        text = "Sign Up",
                        onClick = { 
                            viewModel.register {
                                onRegisterClick(
                                    uiState.fullName,
                                    uiState.dateOfBirth,
                                    uiState.phoneNumber,
                                    uiState.password,
                                    uiState.confirmPassword
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        gradient = Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFFFFB8D6),
                                Color(0xFFFFD6E8)
                            )
                        ),
                        enabled = viewModel.isFormValid() && !uiState.isLoading,
                        isLoading = uiState.isLoading
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Login text
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Already have an account? ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = "Login",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = Color(0xFFFF9ECE),
                            modifier = Modifier.clickable { onLoginClick() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
        
        // Error Snackbar
        uiState.errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            ) {
                CustomSnackbar(
                    message = error,
                    type = SnackbarType.ERROR,
                    onDismiss = { viewModel.clearError() }
                )
            }
        }
    }
}

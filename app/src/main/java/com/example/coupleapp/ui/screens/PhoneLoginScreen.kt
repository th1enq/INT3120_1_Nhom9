package com.example.coupleapp.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.example.coupleapp.viewmodel.PhoneLoginViewModel
import kotlinx.coroutines.delay

@Composable
fun PhoneLoginScreen(
    onLoginClick: (String, String) -> Unit,
    onBackClick: () -> Unit,
    onForgotPasswordClick: () -> Unit,
    viewModel: PhoneLoginViewModel = viewModel()
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
            
            Spacer(modifier = Modifier.height(40.dp))
            
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
                    Text(
                        text = "Login",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 36.sp
                        ),
                        color = Color(0xFF2D2D2D)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Enter your information to continue",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF757575),
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(48.dp))
            
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
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Forgot Password
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgot password?",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color(0xFFFF9ECE),
                            modifier = Modifier.clickable { onForgotPasswordClick() }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Login Button
                    GradientButton(
                        text = "Login",
                        onClick = { 
                            viewModel.login {
                                onLoginClick(uiState.phoneNumber, uiState.password)
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
                    
                    // Decorative text
                    Text(
                        text = "Or login with",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFB0B0B0),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                    
                    // Social login options could go here if needed
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
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

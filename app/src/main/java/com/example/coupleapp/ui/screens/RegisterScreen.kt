package com.example.coupleapp.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.example.coupleapp.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.coupleapp.ui.components.CustomTextField
import com.example.coupleapp.ui.components.DateTextField
import com.example.coupleapp.ui.components.GradientButton
import com.example.coupleapp.ui.components.CustomSnackbar
import com.example.coupleapp.ui.components.SnackbarType
import com.example.coupleapp.viewmodel.RegisterViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

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
                    text = stringResource(R.string.back_arrow),
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
                        text = stringResource(R.string.create_account),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp
                        ),
                        color = Color(0xFF2D2D2D)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(R.string.register_subtitle),
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
                        placeholder = stringResource(R.string.full_name),
                        keyboardType = KeyboardType.Text,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.fullNameError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Date of Birth Field with DatePicker
                    val context = LocalContext.current
                    val calendar = remember { Calendar.getInstance() }
                    
                    // Set default to 18 years ago
                    LaunchedEffect(Unit) {
                        calendar.add(Calendar.YEAR, -18)
                    }
                    
                    val datePickerDialog = remember {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                viewModel.setDateOfBirth(dayOfMonth, month + 1, year)
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).apply {
                            // Set max date to today (user cannot be born in the future)
                            datePicker.maxDate = System.currentTimeMillis()
                            // Set min date to 120 years ago
                            val minCalendar = Calendar.getInstance()
                            minCalendar.add(Calendar.YEAR, -120)
                            datePicker.minDate = minCalendar.timeInMillis
                        }
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        DateTextField(
                            value = uiState.dateOfBirth,
                            onValueChange = { viewModel.updateDateOfBirth(it) },
                            placeholder = stringResource(R.string.date_of_birth),
                            modifier = Modifier.weight(1f),
                            errorMessage = uiState.dateOfBirthError
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Calendar button to open DatePicker
                        IconButton(
                            onClick = { datePickerDialog.show() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFFFFB8D6),
                                            Color(0xFFFFD6E8)
                                        )
                                    )
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = stringResource(R.string.date_of_birth),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Phone Number Field
                    CustomTextField(
                        value = uiState.phoneNumber,
                        onValueChange = { viewModel.updatePhone(it) },
                        placeholder = stringResource(R.string.phone_number),
                        keyboardType = KeyboardType.Phone,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.phoneError
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Password Field
                    CustomTextField(
                        value = uiState.password,
                        onValueChange = { viewModel.updatePassword(it) },
                        placeholder = stringResource(R.string.password),
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
                        placeholder = stringResource(R.string.confirm_password),
                        isPassword = true,
                        keyboardType = KeyboardType.Password,
                        modifier = Modifier.fillMaxWidth(),
                        errorMessage = uiState.confirmPasswordError
                    )
                    
                    // Password match indicator
                    if (uiState.confirmPassword.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.password == uiState.confirmPassword) stringResource(R.string.passwords_match) else stringResource(R.string.passwords_not_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.password == uiState.confirmPassword) Color(0xFF4CAF50) else Color(0xFFFF6B6B),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Register Button
                    GradientButton(
                        text = stringResource(R.string.sign_up),
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
                            text = stringResource(R.string.already_have_account) + " ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF757575)
                        )
                        Text(
                            text = stringResource(R.string.login),
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

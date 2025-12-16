package com.example.coupleapp.ui.components.partner

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.coupleapp.ui.theme.*

/**
 * Dialog để tạo câu hỏi mới
 */
@Composable
fun CreateQuestionDialog(
    partnerName: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            shape = RoundedCornerShape(24.dp),
            color = BackgroundWhite,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Icon(
                    imageVector = Icons.Outlined.QuestionAnswer,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(48.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Ask a Question to $partnerName",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Ask an interesting question to get to know each other better",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Question input
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { 
                        questionText = it
                        isError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Ví dụ: Điều gì khiến em yêu anh?",
                            color = TextLight
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF4CAF50),
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        errorBorderColor = ErrorColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    isError = isError,
                    supportingText = if (isError) {
                        { Text("Please enter a question", color = ErrorColor) }
                    } else null,
                    minLines = 3,
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = TextSecondary
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            if (questionText.isBlank()) {
                                isError = true
                            } else {
                                onCreate(questionText.trim())
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send")
                    }
                }
            }
        }
    }
}

/**
 * Suggested questions chips
 */
@Composable
fun SuggestedQuestions(
    onQuestionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = listOf(
        "Điều gì khiến em yêu anh?",
        "Kỷ niệm đẹp nhất của chúng ta?",
        "Em muốn đi đâu nhất?",
        "Món ăn yêu thích của em?",
        "Điều gì làm em hạnh phúc?"
    )
    
    Column(modifier = modifier) {
        Text(
            text = "Question Suggestions:",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        suggestions.forEach { question ->
            SuggestionChip(
                onClick = { onQuestionSelected(question) },
                label = {
                    Text(
                        text = question,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFFF5F5F5)
                ),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

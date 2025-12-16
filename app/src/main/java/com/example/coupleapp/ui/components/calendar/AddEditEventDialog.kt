package com.example.coupleapp.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.coupleapp.data.model.Anniversary
import com.example.coupleapp.data.model.AnniversaryType
import com.example.coupleapp.ui.theme.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditEventDialog(
    anniversary: Anniversary?,
    selectedDate: LocalDate?,
    onDismiss: () -> Unit,
    onSave: (Anniversary) -> Unit,
    onDelete: (String) -> Unit
) {
    var title by remember { mutableStateOf(anniversary?.title ?: "") }
    var description by remember { mutableStateOf(anniversary?.description ?: "") }
    var selectedType by remember { mutableStateOf(anniversary?.type ?: AnniversaryType.CUSTOM) }
    var date by remember { mutableStateOf(anniversary?.date?.toLocalDate() ?: selectedDate ?: LocalDate.now()) }
    var time by remember { mutableStateOf(anniversary?.date?.toLocalTime() ?: LocalTime.of(12, 0)) }
    var isRecurring by remember { mutableStateOf(anniversary?.isRecurring ?: false) }
    var reminderEnabled by remember { mutableStateOf(anniversary?.reminderEnabled ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (anniversary == null) "Thêm kỷ niệm" else "Sửa kỷ niệm",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Form fields
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title
                    item {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Tiêu đề *") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPink,
                                focusedLabelColor = AccentPink
                            ),
                            singleLine = true
                        )
                    }
                    
                    // Description
                    item {
                        OutlinedTextField(
                            value = description,
                            onValueChange = { description = it },
                            label = { Text("Mô tả") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentPink,
                                focusedLabelColor = AccentPink
                            ),
                            maxLines = 3
                        )
                    }
                    
                    // Event Type
                    item {
                        Column {
                            Text(
                                text = "Loại sự kiện",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { showTypePicker = true }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = selectedType.emoji, fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = selectedType.displayName,
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Date
                    item {
                        Column {
                            Text(
                                text = "Ngày",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { showDatePicker = true }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = null,
                                            tint = AccentPink
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Time
                    item {
                        Column {
                            Text(
                                text = "Giờ",
                                fontSize = 14.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, AccentPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                    .clickable { showTimePicker = true }
                                    .padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = null,
                                            tint = AccentPink
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = time.format(DateTimeFormatter.ofPattern("HH:mm")),
                                            fontSize = 16.sp,
                                            color = TextPrimary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Recurring toggle
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isRecurring = !isRecurring }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Lặp lại hàng năm",
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Nhắc nhở vào ngày này mỗi năm",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = isRecurring,
                                onCheckedChange = { isRecurring = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPink
                                )
                            )
                        }
                    }
                    
                    // Reminder toggle
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { reminderEnabled = !reminderEnabled }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Bật nhắc nhở",
                                    fontSize = 16.sp,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Nhận thông báo trước sự kiện",
                                    fontSize = 12.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { reminderEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = AccentPink
                                )
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (anniversary != null) {
                        Button(
                            onClick = { onDelete(anniversary.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorColor
                            ),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Xóa")
                        }
                    }
                    
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                val newAnniversary = Anniversary(
                                    id = anniversary?.id ?: UUID.randomUUID().toString(),
                                    title = title,
                                    description = description,
                                    date = LocalDateTime.of(date, time),
                                    type = selectedType,
                                    isRecurring = isRecurring,
                                    reminderEnabled = reminderEnabled
                                )
                                onSave(newAnniversary)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPink
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = title.isNotBlank()
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Lưu")
                    }
                }
            }
        }
    }
    
    // Type Picker Dialog
    if (showTypePicker) {
        Dialog(onDismissRequest = { showTypePicker = false }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Chọn loại sự kiện",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    AnniversaryType.values().forEach { type ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedType = type
                                    showTypePicker = false
                                }
                                .background(
                                    if (selectedType == type) PastelPink.copy(alpha = 0.3f)
                                    else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = type.emoji, fontSize = 24.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = type.displayName,
                                fontSize = 16.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Date Picker (simplified - in production, use DatePickerDialog)
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 24 * 60 * 60 * 1000
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        date = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    
    // Time Picker (simplified)
    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(timePickerState.hour, timePickerState.minute)
                    showTimePicker = false
                }) {
                    Text("OK", color = AccentPink)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Hủy", color = TextSecondary)
                }
            },
            text = {
                TimePicker(state = timePickerState)
            }
        )
    }
}

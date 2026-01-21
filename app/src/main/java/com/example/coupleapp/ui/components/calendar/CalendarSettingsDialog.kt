package com.example.coupleapp.ui.components.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import com.example.coupleapp.R
import com.example.coupleapp.data.model.CalendarSettings
import com.example.coupleapp.data.model.CoupleProfile
import com.example.coupleapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarSettingsDialog(
    settings: CalendarSettings,
    coupleProfile: CoupleProfile?,
    onDismiss: () -> Unit,
    onUpdateNickname: (String, String) -> Unit,
    onUpdateBackground: (String) -> Unit,
    onToggleHeartbeat: (Boolean) -> Unit,
    onUpdateAnniversaryDate: (java.time.LocalDate) -> Unit = {},
    onSelectBackgroundFromGallery: () -> Unit = {},
    onUpdateReminderHours: (Int) -> Unit = {}
) {
    var user1Nickname by remember { mutableStateOf(coupleProfile?.user1?.nickname ?: "") }
    var user2Nickname by remember { mutableStateOf(coupleProfile?.user2?.nickname ?: "") }
    var showHeartbeat by remember { mutableStateOf(settings.showHeartbeatAnimation) }
    var useDefaultBackground by remember { mutableStateOf(settings.useDefaultBackground) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var reminderHours by remember { mutableStateOf(settings.reminderHoursBefore) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 650.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = BackgroundWhite
            )
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(PastelPink, SoftPink)
                            )
                        )
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Cài đặt",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }
                
                // Settings content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Profile Settings Section
                    item {
                        SettingsSection(title = "Thông tin cá nhân") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // User 1 Nickname
                                coupleProfile?.user1?.let { user1 ->
                                    NicknameField(
                                        label = "${user1.name}",
                                        value = user1Nickname,
                                        onValueChange = { 
                                            user1Nickname = it
                                        },
                                        onSave = {
                                            onUpdateNickname(user1.id, user1Nickname)
                                        }
                                    )
                                }
                                
                                // User 2 Nickname
                                coupleProfile?.user2?.let { user2 ->
                                    NicknameField(
                                        label = "${user2.name}",
                                        value = user2Nickname,
                                        onValueChange = { 
                                            user2Nickname = it
                                        },
                                        onSave = {
                                            onUpdateNickname(user2.id, user2Nickname)
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Appearance Settings
                    item {
                        SettingsSection(title = "Giao diện") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                // Background setting
                                SettingItem(
                                    icon = Icons.Default.Image,
                                    title = "Hình nền mặc định",
                                    subtitle = if (useDefaultBackground) "Đang sử dụng" else "Tắt",
                                    trailing = {
                                        Switch(
                                            checked = useDefaultBackground,
                                            onCheckedChange = { 
                                                useDefaultBackground = it
                                                if (it) {
                                                    onUpdateBackground("")
                                                }
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AccentPink
                                            )
                                        )
                                    }
                                )
                                
                                // Custom background button (only show when not using default)
                                if (!useDefaultBackground) {
                                    Button(
                                        onClick = { 
                                            onSelectBackgroundFromGallery()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PastelPink.copy(alpha = 0.3f),
                                            contentColor = AccentPink
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.select_background_gallery))
                                    }
                                }
                                
                                Divider(color = PastelPink.copy(alpha = 0.3f))
                                
                                // Heartbeat animation
                                SettingItem(
                                    icon = Icons.Default.Favorite,
                                    title = "Hiệu ứng trái tim đập",
                                    subtitle = if (showHeartbeat) "Bật" else "Tắt",
                                    trailing = {
                                        Switch(
                                            checked = showHeartbeat,
                                            onCheckedChange = { 
                                                showHeartbeat = it
                                                onToggleHeartbeat(it)
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = AccentPink
                                            )
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    // Notification Settings
                    item {
                        SettingsSection(title = "Thông báo") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingItem(
                                    icon = Icons.Default.Notifications,
                                    title = "Nhắc nhở sự kiện",
                                    subtitle = "Nhận thông báo trước ${reminderHours}h",
                                    onClick = { 
                                        showReminderPicker = true
                                    },
                                    trailing = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Chỉnh sửa",
                                            tint = AccentPink,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                    
                    // About Section
                    item {
                        SettingsSection(title = "Thông tin") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SettingItem(
                                    icon = Icons.Default.Info,
                                    title = "Về chức năng",
                                    subtitle = "Calendar - Đếm ngày yêu",
                                    onClick = { }
                                )
                                
                                coupleProfile?.let { profile ->
                                    val formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")
                                    SettingItem(
                                        icon = Icons.Default.DateRange,
                                        title = "Ngày bắt đầu yêu nhau",
                                        subtitle = profile.relationshipStartDate.toLocalDate().format(formatter),
                                        onClick = { showDatePicker = true },
                                        trailing = {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Chỉnh sửa",
                                                tint = AccentPink,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Date Picker Dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = coupleProfile?.relationshipStartDate?.let {
                    it.toLocalDate().toEpochDay() * 24 * 60 * 60 * 1000
                } ?: System.currentTimeMillis()
            )
            
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val selectedDate = java.time.LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                                onUpdateAnniversaryDate(selectedDate)
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text(stringResource(R.string.confirm), color = AccentPink)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            ) {
                DatePicker(
                    state = datePickerState,
                    colors = DatePickerDefaults.colors(
                        selectedDayContainerColor = AccentPink,
                        todayContentColor = AccentPink,
                        todayDateBorderColor = AccentPink
                    )
                )
            }
        }
        
        // Reminder Hours Picker Dialog
        if (showReminderPicker) {
            val reminderOptions = listOf(1, 2, 6, 12, 24, 48, 72)
            
            AlertDialog(
                onDismissRequest = { showReminderPicker = false },
                title = {
                    Text(
                        text = "Chọn thời gian nhắc nhở",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                text = {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Nhận thông báo trước sự kiện:",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                        reminderOptions.forEach { hours ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        reminderHours = hours
                                        onUpdateReminderHours(hours)
                                        showReminderPicker = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = reminderHours == hours,
                                    onClick = {
                                        reminderHours = hours
                                        onUpdateReminderHours(hours)
                                        showReminderPicker = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AccentPink
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = when (hours) {
                                        1 -> "1 giờ trước"
                                        2 -> "2 giờ trước"
                                        6 -> "6 giờ trước"
                                        12 -> "12 giờ trước"
                                        24 -> "1 ngày trước"
                                        48 -> "2 ngày trước"
                                        72 -> "3 ngày trước"
                                        else -> "$hours giờ trước"
                                    },
                                    fontSize = 15.sp,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showReminderPicker = false }) {
                        Text(stringResource(R.string.cancel), color = TextSecondary)
                    }
                },
                containerColor = BackgroundWhite,
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = AccentPink,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = PastelPink.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick)
                else Modifier
            )
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentPink,
                modifier = Modifier.size(24.dp)
            )
            
            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
        
        if (trailing != null) {
            trailing()
        } else if (onClick != null) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextLight
            )
        }
    }
}

@Composable
private fun NicknameField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = isEditing,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentPink,
                    focusedLabelColor = AccentPink,
                    disabledBorderColor = PastelPink.copy(alpha = 0.5f),
                    disabledTextColor = TextPrimary
                ),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.nickname)) }
            )
            
            if (isEditing) {
                IconButton(
                    onClick = {
                        onSave()
                        isEditing = false
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .background(AccentPink, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save",
                        tint = Color.White
                    )
                }
            } else {
                IconButton(
                    onClick = { isEditing = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(PastelPink.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = AccentPink
                    )
                }
            }
        }
    }
}

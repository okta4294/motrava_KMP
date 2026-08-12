package com.myapp.motrava.presentation.service

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myapp.motrava.data.remote.dto.ServiceReminderData
import com.myapp.motrava.data.remote.dto.VehicleData
import com.myapp.motrava.presentation.theme.*
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(
    viewModel: ServiceReminderViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<ServiceReminderData?>(null) }
    var reminderToAddDistance by remember { mutableStateOf<ServiceReminderData?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (uiState.selectedVehicle != null) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = AccentYellow,
                    contentColor = MaterialTheme.colorScheme.background,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Vehicle Selector
            if (uiState.vehicles.isNotEmpty()) {
                item {
                    VehicleDropdown(
                        vehicles = uiState.vehicles,
                        selectedVehicle = uiState.selectedVehicle,
                        onVehicleSelected = { viewModel.selectVehicle(it) }
                    )
                }
            } else if (!uiState.isLoading) {
                item {
                    Text("No vehicles found. Please add a vehicle first.", color = MaterialTheme.colorScheme.error)
                }
            }

            // Next Service Due Card (gradient)
            if (uiState.reminders.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(ActivePink, AccentPeach)
                                    ),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    "Next Service Due",
                                    color = Color.White.copy(alpha = 0.9f),
                                    style = MaterialTheme.typography.labelLarge,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    "${uiState.reminders.size} Reminders Active",
                                    color = Color.White,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "for ${uiState.selectedVehicle?.vehicleName ?: "â€”"}",
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // Error Message
            uiState.error?.let { error ->
                item {
                    Text(text = error, color = MaterialTheme.colorScheme.error)
                }
            }

            // Loading Indicator
            if (uiState.isLoading) {
                item {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = GradientPurple)
                    }
                }
            }

            // Section header
            if (uiState.reminders.isNotEmpty()) {
                item {
                    Text(
                        "Upcoming Maintenance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Reminders List
            items(uiState.reminders, key = { it.id }) { reminder ->
                ReminderItem(
                    reminder = reminder,
                    onDelete = { viewModel.deleteReminder(reminder.id) },
                    onEdit = { reminderToEdit = reminder },
                    onAddDistance = { reminderToAddDistance = reminder }
                )
            }
            
            if (!uiState.isLoading && uiState.reminders.isEmpty() && uiState.selectedVehicle != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            "No service reminders found for this vehicle.",
                            modifier = Modifier.padding(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddReminderDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { name, interval ->
                    viewModel.addReminder(name, interval)
                    showAddDialog = false
                }
            )
        }
        
        reminderToEdit?.let { reminder ->
            UpdateReminderDialog(
                reminder = reminder,
                onDismiss = { reminderToEdit = null },
                onUpdate = { name, interval ->
                    viewModel.updateReminder(reminder.id, name, interval)
                    reminderToEdit = null
                }
            )
        }
        
        reminderToAddDistance?.let { reminder ->
            AddManualDistanceDialog(
                onDismiss = { reminderToAddDistance = null },
                onAdd = { distance, note ->
                    viewModel.addManualDistance(reminder.id, distance, note)
                    reminderToAddDistance = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDropdown(
    vehicles: List<VehicleData>,
    selectedVehicle: VehicleData?,
    onVehicleSelected: (VehicleData) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedVehicle?.vehicleName ?: "Select Vehicle",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            label = { Text("Vehicle") },
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentPeach,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedLabelColor = AccentPeach
            )
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            vehicles.forEach { vehicle ->
                DropdownMenuItem(
                    text = { Text(vehicle.vehicleName) },
                    onClick = {
                        onVehicleSelected(vehicle)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ReminderItem(
    reminder: ServiceReminderData,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddDistance: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left accent line
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Max)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
                    .background(GradientPurple)
            )
            
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reminder.serviceName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = GradientPurple
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Interval: ${reminder.intervalKm} km",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last Service: ${reminder.lastServiceKm} km",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row {
                    IconButton(onClick = onAddDistance) {
                        Icon(Icons.Default.Speed, contentDescription = "Add Distance", tint = AccentPeach)
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GradientPurple)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var serviceName by remember { mutableStateOf("") }
    var intervalStr by remember { mutableStateOf("") }

    val inputShape = RoundedCornerShape(12.dp)
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GradientPink,
        unfocusedBorderColor = GradientPurple,
        focusedLabelColor = GradientPink,
        unfocusedLabelColor = GradientPink.copy(alpha = 0.7f),
        cursorColor = GradientPink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Service Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Service Name (e.g. Oil Change)") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = intervalStr,
                    onValueChange = { intervalStr = it },
                    label = { Text("Interval (km)") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val interval = intervalStr.toIntOrNull()
                    if (serviceName.isNotBlank() && interval != null) {
                        onAdd(serviceName, interval)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GradientPurple)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateReminderDialog(
    reminder: ServiceReminderData,
    onDismiss: () -> Unit,
    onUpdate: (String, Int) -> Unit
) {
    var serviceName by remember { mutableStateOf(reminder.serviceName) }
    var intervalStr by remember { mutableStateOf(reminder.intervalKm.toString()) }

    val inputShape = RoundedCornerShape(12.dp)
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GradientPink,
        unfocusedBorderColor = GradientPurple,
        focusedLabelColor = GradientPink,
        unfocusedLabelColor = GradientPink.copy(alpha = 0.7f),
        cursorColor = GradientPink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Update Service Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = serviceName,
                    onValueChange = { serviceName = it },
                    label = { Text("Service Name (e.g. Oil Change)") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = intervalStr,
                    onValueChange = { intervalStr = it },
                    label = { Text("Interval (km)") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val interval = intervalStr.toIntOrNull()
                    if (serviceName.isNotBlank() && interval != null) {
                        onUpdate(serviceName, interval)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GradientPurple)
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddManualDistanceDialog(
    onDismiss: () -> Unit,
    onAdd: (Double, String) -> Unit
) {
    var distanceStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val inputShape = RoundedCornerShape(12.dp)
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GradientPink,
        unfocusedBorderColor = GradientPurple,
        focusedLabelColor = GradientPink,
        unfocusedLabelColor = GradientPink.copy(alpha = 0.7f),
        cursorColor = GradientPink
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Add Manual Distance", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = distanceStr,
                    onValueChange = { distanceStr = it },
                    label = { Text("Current Odometer (km)") },
                    placeholder = { Text("e.g. 1400") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    )
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = inputShape,
                    colors = inputColors
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val distance = distanceStr.toDoubleOrNull()
                    if (distance != null && distance > 0) {
                        onAdd(distance, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GradientPurple)
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}


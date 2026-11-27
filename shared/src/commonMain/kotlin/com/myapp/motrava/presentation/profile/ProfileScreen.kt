package com.myapp.motrava.presentation.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import org.koin.compose.viewmodel.koinViewModel
import com.myapp.motrava.presentation.theme.*
import com.myapp.motrava.presentation.vehicle.VehicleViewModel

@Composable
fun ProfileScreen(
    onNavigateToAddVehicle: () -> Unit = {},
    onNavigateToEditVehicle: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
    vehicleViewModel: VehicleViewModel = koinViewModel()
) {
    val profileState by viewModel.userState.collectAsState()
    val vehiclesState by vehicleViewModel.vehiclesState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out", fontWeight = FontWeight.SemiBold) },
            text = { Text("Are you sure you want to log out? Any active trip tracking on this device will be stopped.") },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    LaunchedEffect(Unit) {
        vehicleViewModel.fetchVehicles()
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profile Header (centered)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val user = (profileState as? ProfileState.Success)?.user
                
                // Minimalist avatar with hairline border
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape
                        )
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (!user?.profilePic.isNullOrEmpty()) {
                                coil3.compose.AsyncImage(
                                    model = user?.profilePic,
                                    contentDescription = "Profile Picture",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Avatar",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(14.dp))
                
                when (profileState) {
                    is ProfileState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    }
                    is ProfileState.Success -> {
                        val user = (profileState as ProfileState.Success).user
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = user.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ProfileState.Error -> {
                        Text(
                            text = "Failed to load",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Vehicle Info Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vehicles",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(onClick = onNavigateToAddVehicle) {
                            Text("Add Vehicle", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    when (vehiclesState) {
                        is VehicleViewModel.VehiclesState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.padding(16.dp).size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        }
                        is VehicleViewModel.VehiclesState.Success -> {
                            val vehicles = (vehiclesState as VehicleViewModel.VehiclesState.Success).vehicles
                            val distances = (vehiclesState as VehicleViewModel.VehiclesState.Success).distances
                            if (vehicles.isEmpty()) {
                                Text(
                                    text = "No vehicles added yet.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                vehicles.forEachIndexed { index, vehicle ->
                                    val distance = distances[vehicle.id] ?: 0.0
                                    var showDeleteDialog by remember { mutableStateOf(false) }

                                    if (showDeleteDialog) {
                                        AlertDialog(
                                            onDismissRequest = { showDeleteDialog = false },
                                            title = { Text("Delete Vehicle") },
                                            text = { Text("Are you sure you want to delete \"${vehicle.vehicleName}\"?") },
                                            confirmButton = {
                                                TextButton(onClick = {
                                                    vehicleViewModel.deleteVehicle(vehicle.id)
                                                    showDeleteDialog = false
                                                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                                            },
                                            dismissButton = {
                                                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                                            }
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = when (vehicle.vehicleType.uppercase()) {
                                                        "MOTORCYCLE" -> Icons.Default.TwoWheeler
                                                        "CAR" -> Icons.Default.DirectionsCar
                                                        else -> Icons.Default.DirectionsCar
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                        
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 14.dp)
                                        ) {
                                            Text(
                                                vehicle.vehicleName,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                "${vehicle.brand} ${vehicle.model} • ${vehicle.plateNumber}\n%.1f km".format(distance),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        
                                        IconButton(onClick = { onNavigateToEditVehicle(vehicle.id) }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit Vehicle",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        IconButton(onClick = { showDeleteDialog = true }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete Vehicle",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                    }
                                    if (index < vehicles.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outlineVariant
                                        )
                                    }
                                }
                            }
                        }
                        is VehicleViewModel.VehiclesState.Error -> {
                            Text(text = "Failed to load vehicles", color = MaterialTheme.colorScheme.error)
                        }
                        else -> {}
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = { showLogoutDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "Log Out",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Motrava v2.2",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

    }
}

@Preview(showBackground = true)
@Composable
fun ProfilePreview() {
    MotravaTheme {
        ProfileScreen()
    }
}


package com.myapp.motrava.presentation.vehicle

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import org.koin.compose.viewmodel.koinViewModel
import com.myapp.motrava.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    vehicleId: String? = null,
    onVehicleAdded: () -> Unit,
    viewModel: VehicleViewModel = koinViewModel()
) {
    var vehicleName by remember { mutableStateOf("") }
    var plateNumber by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var avgBbm by remember { mutableStateOf("") }
    var initialKm by remember { mutableStateOf("") }
    var prefilled by remember { mutableStateOf(false) }
    
    // Dropdown for vehicle type
    val vehicleTypes = listOf("CAR", "MOTORCYCLE")
    var expanded by remember { mutableStateOf(false) }
    var selectedType by remember { mutableStateOf(vehicleTypes[0]) }

    val createState by viewModel.createVehicleState.collectAsState()

    val vehiclesState by viewModel.vehiclesState.collectAsState()

    LaunchedEffect(vehicleId) {
        if (vehicleId != null) {
            viewModel.fetchVehicles()
        }
    }

    LaunchedEffect(vehicleId, vehiclesState) {
        if (vehicleId != null && !prefilled && vehiclesState is VehicleViewModel.VehiclesState.Success) {
            val vehicle = (vehiclesState as VehicleViewModel.VehiclesState.Success).vehicles.find { it.id == vehicleId }
            if (vehicle != null) {
                vehicleName = vehicle.vehicleName
                plateNumber = vehicle.plateNumber
                brand = vehicle.brand
                model = vehicle.model
                color = vehicle.color
                year = vehicle.year.toString()
                avgBbm = vehicle.avgBbm?.toString() ?: ""
                initialKm = vehicle.initialKm?.toString() ?: ""
                selectedType = vehicle.vehicleType
                prefilled = true
            }
        }
    }

    LaunchedEffect(createState) {
        if (createState is VehicleViewModel.CreateVehicleState.Success) {
            viewModel.resetCreateState()
            onVehicleAdded()
        }
    }

    // Input field styling
    val inputShape = RoundedCornerShape(12.dp)
    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = GradientPink,
        unfocusedBorderColor = GradientPurple,
        focusedLabelColor = GradientPink,
        unfocusedLabelColor = GradientPink.copy(alpha = 0.7f),
        cursorColor = GradientPink
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (vehicleId != null) "Edit Vehicle" else "Add Vehicle",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onVehicleAdded) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = GradientPink
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            
            OutlinedTextField(
                value = vehicleName,
                onValueChange = { vehicleName = it },
                label = { Text("Vehicle Name") },
                placeholder = { Text("e.g., My Daily Car") },
                modifier = Modifier.fillMaxWidth(),
                shape = inputShape,
                colors = inputColors
            )

            OutlinedTextField(
                value = plateNumber,
                onValueChange = { plateNumber = it },
                label = { Text("Plate Number") },
                modifier = Modifier.fillMaxWidth(),
                shape = inputShape,
                colors = inputColors
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Brand") },
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model") },
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Color") },
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = avgBbm,
                    onValueChange = { avgBbm = it },
                    label = { Text("Avg BBM (km/L)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
                OutlinedTextField(
                    value = initialKm,
                    onValueChange = { initialKm = it },
                    label = { Text("Initial KM") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    shape = inputShape,
                    colors = inputColors
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vehicle Type") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = inputShape,
                    colors = inputColors
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    vehicleTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedType = type
                                expanded = false
                            }
                        )
                    }
                }
            }

            if (createState is VehicleViewModel.CreateVehicleState.Error) {
                Text(
                    text = (createState as VehicleViewModel.CreateVehicleState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val yearInt = year.toIntOrNull() ?: 2023
                    val avgBbmDouble = avgBbm.replace(",", ".").toDoubleOrNull()
                    val initialKmDouble = initialKm.replace(",", ".").toDoubleOrNull()
                    if (vehicleId != null) {
                        viewModel.updateVehicle(
                            id = vehicleId,
                            name = vehicleName,
                            plate = plateNumber,
                            brand = brand,
                            model = model,
                            type = selectedType,
                            color = color,
                            year = yearInt,
                            avgBbm = avgBbmDouble,
                            initialKm = initialKmDouble
                        )
                    } else {
                        viewModel.createVehicle(
                            name = vehicleName,
                            plate = plateNumber,
                            brand = brand,
                            model = model,
                            type = selectedType,
                            color = color,
                            year = yearInt,
                            avgBbm = avgBbmDouble,
                            initialKm = initialKmDouble
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPeach,
                    contentColor = Color.White
                ),
                enabled = vehicleName.isNotBlank() && plateNumber.isNotBlank() && 
                          createState !is VehicleViewModel.CreateVehicleState.Loading
            ) {
                if (createState is VehicleViewModel.CreateVehicleState.Loading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Text(
                        "Save Vehicle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}


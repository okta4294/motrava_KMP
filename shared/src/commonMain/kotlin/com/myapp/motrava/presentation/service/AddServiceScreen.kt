package com.myapp.motrava.presentation.service

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.myapp.motrava.presentation.theme.MotravaTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServiceScreen(onServiceSaved: () -> Unit) {
    var serviceType by remember { mutableStateOf("") }
    var brandName by remember { mutableStateOf("") } // e.g. Motul
    var currentOdo by remember { mutableStateOf("") }
    var nextServiceInterval by remember { mutableStateOf("") } // e.g. 4000

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Log Service Activity") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(text = "Service Details", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = serviceType,
                onValueChange = { serviceType = it },
                label = { Text("Service Type") },
                placeholder = { Text("e.g. Oil Change, Engine Tune-up") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = brandName,
                onValueChange = { brandName = it },
                label = { Text("Parts/Oil Brand (Optional)") },
                placeholder = { Text("e.g. Motul 5100") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            Text(text = "Maintenance Schedule", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = currentOdo,
                onValueChange = { currentOdo = it },
                label = { Text("Current Odometer (km)") },
                leadingIcon = { Icon(Icons.Default.Speed, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = nextServiceInterval,
                onValueChange = { nextServiceInterval = it },
                label = { Text("Next Service Interval (km)") },
                placeholder = { Text("e.g. 4000") },
                leadingIcon = { Icon(Icons.Default.EventRepeat, contentDescription = null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = {
                    if (currentOdo.isNotEmpty() && nextServiceInterval.isNotEmpty()) {
                        val total = (currentOdo.toIntOrNull() ?: 0) + (nextServiceInterval.toIntOrNull() ?: 0)
                        Text("Next service at approx. $total km")
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onServiceSaved,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Save Service Log")
            }
        }
    }
}

@Preview
@Composable
fun AddServicePreview() {
    MotravaTheme { AddServiceScreen {} }
}


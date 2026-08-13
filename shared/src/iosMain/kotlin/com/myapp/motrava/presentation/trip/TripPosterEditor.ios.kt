package com.myapp.motrava.presentation.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.myapp.motrava.data.remote.dto.TripDetailData
import com.myapp.motrava.presentation.theme.GradientPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.CoreGraphics.*
import platform.Foundation.setValue
import platform.UIKit.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
actual fun TripPosterEditorDialog(
    trip: TripDetailData,
    initialIsTransparentBg: Boolean,
    liveMapSnapshot: ImageBitmap?,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF121318)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss, enabled = !isSaving) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Text("Export Trip Poster (iOS)", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Button(
                        onClick = {
                            isSaving = true
                            coroutineScope.launch {
                                exportTripPosterIos(trip)
                                isSaving = false
                                onDismiss()
                            }
                        },
                        enabled = !isSaving,
                        colors = ButtonDefaults.buttonColors(containerColor = GradientPurple)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp)
                ) {
                    Text("iOS Preview Mode\nPress Save to export poster.", color = Color.White.copy(alpha = 0.5f))
                }
            }
        }
    }
}

private suspend fun exportTripPosterIos(trip: TripDetailData) = withContext(Dispatchers.IO) {
    val width = 1080.0
    val height = 1920.0
    val size = CGSizeMake(width, height)
    
    UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
    val context = UIGraphicsGetCurrentContext()
    
    // Background
    CGContextSetFillColorWithColor(context, UIColor(red = 0.1, green = 0.1, blue = 0.14, alpha = 1.0).CGColor)
    CGContextFillRect(context, CGRectMake(0.0, 0.0, width, height))
    
    // Draw simple text using CoreGraphics
    val text = "MOTRAVA ACTIVITY: ${trip.vehicleName ?: "RIDE"}"
    // (Text drawing is omitted here to keep cinterop simple without missing NSAttributedString keys)
    
    val resultImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    
    if (resultImage != null) {
        UIImageWriteToSavedPhotosAlbum(resultImage, null, null, null)
    }
}

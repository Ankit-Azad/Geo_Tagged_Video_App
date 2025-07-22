package com.example.myapplication01

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoRecordingScreen(
    viewModel: VideoLocationViewModel,
    onStartRecording: (Long) -> Unit,
    onStopRecording: () -> Unit,
    onSetupCamera: (PreviewView) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Observe ViewModel state
    val isRecording by viewModel.isRecording.observeAsState(false)
    val recordingDuration by viewModel.recordingDuration.observeAsState(0L)
    val locationData by viewModel.locationData.observeAsState(emptyList())
    val selectedInterval by viewModel.selectedLocationInterval.observeAsState(1000L)
    val shouldAutoStart by viewModel.shouldAutoStartRecording.observeAsState(false)
    
    // Auto-start recording if requested from RecordingStartScreen
    LaunchedEffect(shouldAutoStart) {
        if (shouldAutoStart && !isRecording) {
            onStartRecording(selectedInterval)
            viewModel.recordingStarted()
        }
    }
    
    // Get safe location data count
    val locationPointsCount = locationData.size
    
    // Dropdown state
    var expanded by remember { mutableStateOf(false) }
    val intervalOptions = viewModel.locationIntervals.keys.toList()
    val selectedIntervalText = intervalOptions.find { 
        viewModel.locationIntervals[it] == selectedInterval 
    } ?: intervalOptions.first()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    onSetupCamera(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // Status Overlay (Top)
                 StatusOverlay(
             viewModel = viewModel,
             isRecording = isRecording,
             recordingTime = viewModel.getFormattedRecordingTime(),
             locationPointsCount = locationPointsCount,
             modifier = Modifier
                 .fillMaxWidth()
                 .align(Alignment.TopCenter)
         )
        
        // Controls (Bottom)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // Location Interval Selector (only show when not recording)
            if (!isRecording) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Location Update Interval:",
                            color = Color.White,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = it }
                        ) {
                            OutlinedTextField(
                                value = selectedIntervalText,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Interval", color = Color.White.copy(alpha = 0.7f)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )
                            
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                intervalOptions.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            viewModel.setLocationInterval(viewModel.locationIntervals[option] ?: 1000L)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Record Button
            FloatingActionButton(
                onClick = {
                    if (isRecording) {
                        onStopRecording()
                    } else {
                        onStartRecording(selectedInterval)
                    }
                },
                modifier = Modifier.size(80.dp),
                containerColor = if (isRecording) Color.Red else Color.Green,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                    contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            }
            
            // Info Text
            Text(
                text = if (isRecording) 
                    "Tap to stop recording" 
                else 
                    "Tap to start recording with location tracking",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
    }
}

@Composable
fun StatusOverlay(
    viewModel: VideoLocationViewModel,
    isRecording: Boolean,
    recordingTime: String,
    locationPointsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Recording Status
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(Color.Red, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                Text(
                    text = if (isRecording) "Recording..." else "Ready to Record",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Location Status
            Text(
                text = viewModel.getFormattedLocation(),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            // Recording Time (only when recording)
            if (isRecording) {
                Text(
                    text = recordingTime,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Text(
                    text = "Location Points: $locationPointsCount",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
package com.example.myapplication01

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Stop
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
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication01.ui.theme.MyApplication01Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.*
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    
    companion object {
        private const val TAG = "VideoLocationTracker"
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        setContent {
            MyApplication01Theme {
                val viewModel: VideoLocationViewModel = viewModel()
                val permissionsState = rememberMultiplePermissionsState(REQUIRED_PERMISSIONS.toList())
                
                LaunchedEffect(permissionsState.allPermissionsGranted) {
                    if (permissionsState.allPermissionsGranted) {
                        setupLocationTracking(viewModel)
                    }
                }
                
                VideoLocationTrackerApp(
                    viewModel = viewModel,
                    permissionsState = permissionsState,
                    onStartRecording = { interval -> startRecording(viewModel, interval) },
                    onStopRecording = { stopRecording(viewModel) },
                    onSetupCamera = { previewView -> setupCamera(previewView) }
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationTracking(viewModel: VideoLocationViewModel) {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    viewModel.updateLocation(location)
                    
                    if (viewModel.isRecording.value == true) {
                        viewModel.addLocationFrame(location)
                    }
                }
            }
        }
        
        startLocationUpdates(viewModel)
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(viewModel: VideoLocationViewModel) {
        val interval = viewModel.selectedLocationInterval.value ?: 1000L
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, interval)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(interval / 2)
            .setMaxUpdateDelayMillis(interval * 2)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    private fun setupCamera(previewView: androidx.camera.view.PreviewView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD))
                .build()
            
            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, videoCapture)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("MissingPermission")
    private fun startRecording(viewModel: VideoLocationViewModel, interval: Long) {
        val videoCapture = this.videoCapture ?: return

        val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VideoLocationTracker")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED
                ) {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewModel.startRecording(name, interval)
                        
                        // Restart location updates with new interval
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                        startLocationUpdates(viewModel)
                        
                        Log.d(TAG, "Recording started with location interval: ${interval}ms")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            saveLocationDataToFile(viewModel, name)
                            val locationCount = (viewModel.locationData.value ?: emptyList()).size
                            val msg = "Video saved successfully with $locationCount location points"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture failed: ${recordEvent.error}")
                        }
                        viewModel.stopRecording()
                    }
                }
            }
    }

    private fun stopRecording(viewModel: VideoLocationViewModel) {
        recording?.stop()
        recording = null
    }

    private fun saveLocationDataToFile(viewModel: VideoLocationViewModel, videoName: String) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            
            val locationDataList = viewModel.locationData.value ?: emptyList()
            val videoMetadata = VideoMetadata(
                videoName = videoName,
                recordingStartTime = viewModel.recordingStartTime.value ?: 0L,
                recordingDuration = System.currentTimeMillis() - (viewModel.recordingStartTime.value ?: 0L),
                locationUpdateInterval = viewModel.selectedLocationInterval.value ?: 1000L,
                totalLocationPoints = locationDataList.size,
                locationData = locationDataList
            )
            
            val jsonString = gson.toJson(videoMetadata)
            
            val fileName = "${videoName}_location_data.json"
            
            // Save to the same location as videos: Movies/VideoLocationTracker/
            val moviesDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // For Android 10+ (API 29+), use MediaStore
                saveJsonToMediaStore(fileName, jsonString)
                return
            } else {
                // For older versions, use direct file access
                val moviesFolder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "VideoLocationTracker")
                if (!moviesFolder.exists()) {
                    moviesFolder.mkdirs()
                }
                File(moviesFolder, fileName)
            }
            
            FileWriter(moviesDir).use { writer ->
                writer.write(jsonString)
            }
            
            Log.d(TAG, "Location data saved to: ${moviesDir.absolutePath}")
            Toast.makeText(this, "JSON saved to Movies/VideoLocationTracker/", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location data", e)
            Toast.makeText(this, "Error saving location data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    @SuppressLint("InlinedApi")
    private fun saveJsonToMediaStore(fileName: String, jsonContent: String) {
        try {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/VideoLocationTracker/")
            }
            
            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(jsonContent.toByteArray())
                }
                Log.d(TAG, "JSON saved to MediaStore: Movies/VideoLocationTracker/$fileName")
                Toast.makeText(this, "JSON saved to Movies/VideoLocationTracker/", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving JSON to MediaStore", e)
            // Fallback to app's external files directory
            val file = File(getExternalFilesDir(null), fileName)
            FileWriter(file).use { writer ->
                writer.write(jsonContent)
            }
            Log.d(TAG, "JSON saved to fallback location: ${file.absolutePath}")
            Toast.makeText(this, "JSON saved to app files directory", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (::fusedLocationClient.isInitialized && ::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun VideoLocationTrackerApp(
    viewModel: VideoLocationViewModel,
    permissionsState: MultiplePermissionsState,
    onStartRecording: (Long) -> Unit,
    onStopRecording: () -> Unit,
    onSetupCamera: (androidx.camera.view.PreviewView) -> Unit
) {
    val context = LocalContext.current
    
    when {
        permissionsState.allPermissionsGranted -> {
            VideoRecordingScreen(
                viewModel = viewModel,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSetupCamera = onSetupCamera
            )
        }
        permissionsState.shouldShowRationale -> {
            PermissionRationaleScreen(
                onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
            )
        }
    }
}
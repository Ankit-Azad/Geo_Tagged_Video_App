package com.example.myapplication01

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.location.Location
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplication01.ui.theme.MyApplication01Theme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.android.gms.location.*
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch
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
                    onSetupCamera = { previewView -> setupCamera(previewView) },
                    onUpload = { uploadFiles(viewModel) },
                    onDecline = { viewModel.declineUpload() }
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
                            val jsonFileName = saveLocationDataToFile(viewModel, name)
                            val locationCount = (viewModel.locationData.value ?: emptyList()).size
                            val msg = "Video saved successfully with $locationCount location points"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                            
                            // Show upload/decline screen
                            if (jsonFileName != null) {
                                viewModel.showUploadDeclineScreen(name, jsonFileName)
                            }
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

    private fun saveLocationDataToFile(viewModel: VideoLocationViewModel, videoName: String): String? {
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
            
            // Save to Downloads folder - this is always visible to users
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val appFolder = File(downloadsDir, "MyApplication01_LocationData")
                
                // Create directory if it doesn't exist
                if (!appFolder.exists()) {
                    val created = appFolder.mkdirs()
                    Log.d(TAG, "Directory created: $created, Path: ${appFolder.absolutePath}")
                }
                
                val jsonFile = File(appFolder, fileName)
                
                // Write the JSON file
                jsonFile.writeText(jsonString)
                
                // Notify the media scanner to make the file visible immediately
                MediaScannerConnection.scanFile(
                    this,
                    arrayOf(jsonFile.absolutePath),
                    arrayOf("text/plain"),
                    null
                )
                
                // Show success message with exact path
                val message = "✅ JSON saved to Downloads/MyApplication01_LocationData/"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, "JSON file saved successfully to: ${jsonFile.absolutePath}")
                
                // Also log the file size to confirm it was written
                Log.d(TAG, "File size: ${jsonFile.length()} bytes")
                return fileName
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save to Downloads folder", e)
                
                // Fallback: Save to app's files directory and show clear instructions
                val fallbackFile = File(getExternalFilesDir(null), fileName)
                fallbackFile.writeText(jsonString)
                
                val message = "⚠️ JSON saved to hidden app folder. Check Android/data/com.example.myapplication01/files/ using a file manager like Files by Google"
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                Log.d(TAG, "JSON saved to fallback location: ${fallbackFile.absolutePath}")
                return fileName
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location data", e)
            Toast.makeText(this, "❌ Error saving JSON: ${e.message}", Toast.LENGTH_LONG).show()
            return null
        }
    }
    
    private fun uploadFiles(viewModel: VideoLocationViewModel) {
        viewModel.startUpload()
        
        val videoName = viewModel.lastRecordedVideoName.value
        val jsonName = viewModel.lastRecordedJsonName.value
        
        if (videoName.isNotEmpty() && jsonName.isNotEmpty()) {
            lifecycleScope.launch {
                val uploadService = UploadService(this@MainActivity)
                
                val result = uploadService.uploadVideoAndJson(
                    videoFileName = videoName,
                    jsonFileName = jsonName,
                    onProgress = { progress ->
                        viewModel.updateUploadProgress(progress)
                    }
                )
                
                when (result) {
                    is UploadResult.Success -> {
                        Toast.makeText(this@MainActivity, "✅ Upload successful!", Toast.LENGTH_LONG).show()
                        Log.d(TAG, "Upload completed: ${result.message}")
                        viewModel.uploadCompleted()
                    }
                    is UploadResult.Error -> {
                        Toast.makeText(this@MainActivity, "❌ Upload failed: ${result.error}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Upload failed: ${result.error}")
                        viewModel.uploadFailed()
                    }
                }
            }
        } else {
            Toast.makeText(this, "❌ No files to upload", Toast.LENGTH_SHORT).show()
            viewModel.uploadFailed()
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
    onSetupCamera: (androidx.camera.view.PreviewView) -> Unit,
    onUpload: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val showUploadScreen by viewModel.showUploadScreen.observeAsState(false)
    val isUploading by viewModel.isUploading.observeAsState(false)
    val uploadProgress by viewModel.uploadProgress.observeAsState(0f)
    
    when {
        !permissionsState.allPermissionsGranted -> {
            if (permissionsState.shouldShowRationale) {
                PermissionRationaleScreen(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            } else {
                PermissionRequestScreen(
                    onRequestPermissions = { permissionsState.launchMultiplePermissionRequest() }
                )
            }
        }
        showUploadScreen -> {
            UploadDeclineScreen(
                videoFileName = viewModel.lastRecordedVideoName.value ?: "",
                jsonFileName = viewModel.lastRecordedJsonName.value ?: "",
                recordingDuration = viewModel.getFormattedRecordingTime(),
                locationPointsCount = viewModel.locationData.value?.size ?: 0,
                isUploading = isUploading,
                uploadProgress = uploadProgress,
                onUpload = onUpload,
                onDecline = onDecline
            )
        }
        else -> {
            VideoRecordingScreen(
                viewModel = viewModel,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSetupCamera = onSetupCamera
            )
        }
    }
}
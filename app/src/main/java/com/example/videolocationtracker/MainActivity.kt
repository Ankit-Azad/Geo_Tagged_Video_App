package com.example.videolocationtracker

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.VideoCapture
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import com.example.videolocationtracker.databinding.ActivityMainBinding
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private lateinit var cameraExecutor: ExecutorService
    
    // Location tracking
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    
    // Recording data
    private var isRecording = false
    private var recordingStartTime = 0L
    private var frameCount = 0L
    private val locationData = mutableListOf<LocationFrame>()
    private var selectedLocationInterval = 1000L // Default 1 second
    
    // Timer for UI updates
    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null
    
    // Location update intervals (in milliseconds)
    private val locationIntervals = mapOf(
        "Every 0.5 seconds (2 FPS)" to 500L,
        "Every 1 second (1 FPS)" to 1000L,
        "Every 2 seconds (0.5 FPS)" to 2000L,
        "Every 5 seconds (0.2 FPS)" to 5000L
    )
    
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

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
                setupLocationTracking()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupLocationIntervalSpinner()
        
        if (allPermissionsGranted()) {
            startCamera()
            setupLocationTracking()
        } else {
            requestPermissions()
        }

        binding.btnRecord.setOnClickListener { toggleRecording() }
        
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupLocationIntervalSpinner() {
        val intervals = locationIntervals.keys.toList()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, intervals)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerLocationInterval.adapter = adapter
        
        binding.spinnerLocationInterval.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedKey = intervals[position]
                selectedLocationInterval = locationIntervals[selectedKey] ?: 1000L
                Log.d(TAG, "Location interval changed to: ${selectedLocationInterval}ms")
            }
            
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationTracking() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = location
                    updateLocationStatus(location)
                    
                    if (isRecording) {
                        addLocationFrame(location)
                    }
                }
            }
        }
        
        startLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, selectedLocationInterval)
            .setWaitForAccurateLocation(false)
            .setMinUpdateIntervalMillis(selectedLocationInterval / 2)
            .setMaxUpdateDelayMillis(selectedLocationInterval * 2)
            .build()

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun updateLocationStatus(location: Location) {
        val locationText = "Location: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)} (±${location.accuracy.toInt()}m)"
        binding.tvLocationStatus.text = locationText
    }

    private fun addLocationFrame(location: Location) {
        val currentTime = System.currentTimeMillis()
        val relativeTime = currentTime - recordingStartTime
        
        val locationFrame = LocationFrame(
            frameNumber = frameCount,
            timestamp = currentTime,
            relativeTimeMs = relativeTime,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            bearing = if (location.hasBearing()) location.bearing else null,
            speed = if (location.hasSpeed()) location.speed else null
        )
        
        locationData.add(locationFrame)
        frameCount++
        
        // Update UI
        binding.tvFrameCount.text = "Location Points: ${locationData.size}"
        
        Log.d(TAG, "Added location frame: $frameCount at ${relativeTime}ms")
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
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

    private fun toggleRecording() {
        val videoCapture = this.videoCapture ?: return

        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val videoCapture = videoCapture ?: return

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
                        isRecording = true
                        recordingStartTime = System.currentTimeMillis()
                        frameCount = 0
                        locationData.clear()
                        
                        updateRecordingUI(true)
                        startTimer()
                        
                        // Start location updates with selected interval
                        stopLocationUpdates()
                        startLocationUpdates()
                        
                        Log.d(TAG, "Recording started with location interval: ${selectedLocationInterval}ms")
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            saveLocationDataToFile(name)
                            val msg = "Video saved successfully with ${locationData.size} location points"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture failed: ${recordEvent.error}")
                        }
                        isRecording = false
                        updateRecordingUI(false)
                        stopTimer()
                    }
                }
            }
    }

    private fun stopRecording() {
        recording?.stop()
        recording = null
    }

    private fun updateRecordingUI(recording: Boolean) {
        if (recording) {
            binding.btnRecord.text = "STOP"
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            binding.tvRecordingStatus.text = "Recording..."
            binding.tvRecordingTime.visibility = View.VISIBLE
            binding.tvFrameCount.visibility = View.VISIBLE
            binding.spinnerLocationInterval.isEnabled = false
        } else {
            binding.btnRecord.text = "START"
            binding.btnRecord.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.tvRecordingStatus.text = "Ready to Record"
            binding.tvRecordingTime.visibility = View.GONE
            binding.tvFrameCount.visibility = View.GONE
            binding.spinnerLocationInterval.isEnabled = true
        }
    }

    private fun startTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    val seconds = (elapsed / 1000) % 60
                    val minutes = (elapsed / (1000 * 60)) % 60
                    binding.tvRecordingTime.text = String.format("%02d:%02d", minutes, seconds)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    private fun stopTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    private fun saveLocationDataToFile(videoName: String) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            
            val videoMetadata = VideoMetadata(
                videoName = videoName,
                recordingStartTime = recordingStartTime,
                recordingDuration = System.currentTimeMillis() - recordingStartTime,
                locationUpdateInterval = selectedLocationInterval,
                totalLocationPoints = locationData.size,
                locationData = locationData
            )
            
            val jsonString = gson.toJson(videoMetadata)
            
            val fileName = "${videoName}_location_data.json"
            val file = File(getExternalFilesDir(null), fileName)
            
            FileWriter(file).use { writer ->
                writer.write(jsonString)
            }
            
            Log.d(TAG, "Location data saved to: ${file.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location data", e)
            Toast.makeText(this, "Error saving location data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        stopLocationUpdates()
        stopTimer()
    }
}
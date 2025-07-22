package com.example.myapplication01

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class VideoLocationViewModel : ViewModel() {
    
    // Recording state
    private val _isRecording = MutableLiveData(false)
    val isRecording: LiveData<Boolean> = _isRecording
    
    private val _recordingStartTime = MutableLiveData(0L)
    val recordingStartTime: LiveData<Long> = _recordingStartTime
    
    private val _recordingDuration = MutableLiveData(0L)
    val recordingDuration: LiveData<Long> = _recordingDuration
    
    private val _videoName = MutableLiveData("")
    val videoName: LiveData<String> = _videoName
    
    // Location state
    private val _currentLocation = MutableLiveData<Location?>()
    val currentLocation: LiveData<Location?> = _currentLocation
    
    private val _locationData = MutableLiveData<List<LocationFrame>>(emptyList())
    val locationData: LiveData<List<LocationFrame>> = _locationData
    
    private val _frameCount = MutableLiveData<Long>(0L)
    val frameCount: LiveData<Long> = _frameCount
    
    // Settings
    private val _selectedLocationInterval = MutableLiveData<Long>(1000L) // Default 1 second
    val selectedLocationInterval: LiveData<Long> = _selectedLocationInterval
    
    // Location intervals map
    val locationIntervals = mapOf(
        "Every 0.5 seconds (2 FPS)" to 500L,
        "Every 1 second (1 FPS)" to 1000L,
        "Every 2 seconds (0.5 FPS)" to 2000L,
        "Every 5 seconds (0.2 FPS)" to 5000L
    )
    
    fun updateLocation(location: Location) {
        _currentLocation.value = location
    }
    
    fun setLocationInterval(interval: Long) {
        _selectedLocationInterval.value = interval
    }
    
    fun startRecording(videoName: String, interval: Long) {
        _isRecording.value = true
        _recordingStartTime.value = System.currentTimeMillis()
        _videoName.value = videoName
        _selectedLocationInterval.value = interval
        _locationData.value = emptyList()
        _frameCount.value = 0L
        
        // Start timer
        startRecordingTimer()
    }
    
    fun stopRecording() {
        _isRecording.value = false
    }
    
    fun addLocationFrame(location: Location) {
        val currentTime = System.currentTimeMillis()
        val startTime = _recordingStartTime.value ?: currentTime
        val relativeTime = currentTime - startTime
        val currentFrameCount = _frameCount.value ?: 0L
        
        val locationFrame = LocationFrame(
            frameNumber = currentFrameCount,
            timestamp = currentTime,
            relativeTimeMs = relativeTime,
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            accuracy = location.accuracy,
            bearing = if (location.hasBearing()) location.bearing else null,
            speed = if (location.hasSpeed()) location.speed else null
        )
        
        val currentList = _locationData.value ?: emptyList()
        _locationData.value = currentList + locationFrame
        _frameCount.value = currentFrameCount + 1
    }
    
    private fun startRecordingTimer() {
        viewModelScope.launch {
            while (_isRecording.value == true) {
                val startTime = _recordingStartTime.value ?: System.currentTimeMillis()
                _recordingDuration.value = System.currentTimeMillis() - startTime
                delay(1000) // Update every second
            }
        }
    }
    
    fun getFormattedRecordingTime(): String {
        val duration = _recordingDuration.value ?: 0L
        val seconds = (duration / 1000) % 60
        val minutes = (duration / (1000 * 60)) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    fun getFormattedLocation(): String {
        return _currentLocation.value?.let { location ->
            "Location: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)} (±${location.accuracy.toInt()}m)"
        } ?: "Location: Not Available"
    }
    
    // Upload/Decline state
    private val _showUploadScreen = MutableLiveData(false)
    val showUploadScreen: LiveData<Boolean> = _showUploadScreen
    
    private val _isUploading = MutableLiveData(false)
    val isUploading: LiveData<Boolean> = _isUploading
    
    private val _uploadProgress = MutableLiveData(0f)
    val uploadProgress: LiveData<Float> = _uploadProgress
    
    private val _lastRecordedVideoName = MutableLiveData("")
    val lastRecordedVideoName: LiveData<String> = _lastRecordedVideoName
    
    private val _lastRecordedJsonName = MutableLiveData("")
    val lastRecordedJsonName: LiveData<String> = _lastRecordedJsonName
    
    fun showUploadDeclineScreen(videoName: String, jsonName: String) {
        _lastRecordedVideoName.value = videoName
        _lastRecordedJsonName.value = jsonName
        _showUploadScreen.value = true
    }
    
    fun startUpload() {
        _isUploading.value = true
        _uploadProgress.value = 0f
    }
    
    fun updateUploadProgress(progress: Float) {
        _uploadProgress.value = progress
    }
    
    fun uploadCompleted() {
        _isUploading.value = false
        _uploadProgress.value = 1f
        resetForNewRecording()
    }
    
    fun uploadFailed() {
        _isUploading.value = false
        _uploadProgress.value = 0f
    }
    
    fun declineUpload() {
        resetForNewRecording()
    }
    
    private fun resetForNewRecording() {
        _showUploadScreen.value = false
        _isRecording.value = false
        _recordingStartTime.value = 0L
        _recordingDuration.value = 0L
        _videoName.value = ""
        _locationData.value = emptyList()
        _frameCount.value = 0L
        _lastRecordedVideoName.value = ""
        _lastRecordedJsonName.value = ""
        _uploadProgress.value = 0f
    }
}
package com.example.myapplication01

import com.google.gson.annotations.SerializedName

/**
 * Represents a single location point captured during video recording
 */
data class LocationFrame(
    @SerializedName("frame_number")
    val frameNumber: Long,
    
    @SerializedName("timestamp")
    val timestamp: Long, // Unix timestamp in milliseconds
    
    @SerializedName("relative_time_ms")
    val relativeTimeMs: Long, // Time since recording started in milliseconds
    
    @SerializedName("latitude")
    val latitude: Double,
    
    @SerializedName("longitude")
    val longitude: Double,
    
    @SerializedName("altitude")
    val altitude: Double, // in meters
    
    @SerializedName("accuracy")
    val accuracy: Float, // in meters
    
    @SerializedName("bearing")
    val bearing: Float? = null, // in degrees (0-360), null if not available
    
    @SerializedName("speed")
    val speed: Float? = null // in m/s, null if not available
)

/**
 * Contains metadata about the video recording and all location data
 */
data class VideoMetadata(
    @SerializedName("video_name")
    val videoName: String,
    
    @SerializedName("recording_start_time")
    val recordingStartTime: Long, // Unix timestamp when recording started
    
    @SerializedName("recording_duration_ms")
    val recordingDuration: Long, // Total recording duration in milliseconds
    
    @SerializedName("location_update_interval_ms")
    val locationUpdateInterval: Long, // Interval between location updates in milliseconds
    
    @SerializedName("total_location_points")
    val totalLocationPoints: Int,
    
    @SerializedName("location_data")
    val locationData: List<LocationFrame>
)
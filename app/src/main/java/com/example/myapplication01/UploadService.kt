package com.example.myapplication01

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.Source
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class UploadService(private val context: Context) {
    
    companion object {
        private const val TAG = "UploadService"
        // Change this to your Django server URL
        private const val BASE_URL = "http://192.168.1.100:8000" // Replace with your local IP
        private const val UPLOAD_ENDPOINT = "/api/upload-video/"
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS) // 5 minutes for large video uploads
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    suspend fun uploadVideoAndJson(
        videoFileName: String,
        jsonFileName: String,
        onProgress: (Float) -> Unit
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting upload for video: $videoFileName, json: $jsonFileName")
            
            // Find video file (in Movies/VideoLocationTracker)
            val videoFile = findVideoFile(videoFileName)
            if (videoFile == null) {
                Log.e(TAG, "Video file not found: $videoFileName")
                return@withContext UploadResult.Error("Video file not found")
            }
            
            // Find JSON file (in Downloads/MyApplication01_LocationData)
            val jsonFile = findJsonFile(jsonFileName)
            if (jsonFile == null) {
                Log.e(TAG, "JSON file not found: $jsonFileName")
                return@withContext UploadResult.Error("JSON file not found")
            }
            
            Log.d(TAG, "Video file: ${videoFile.absolutePath}, size: ${videoFile.length()}")
            Log.d(TAG, "JSON file: ${jsonFile.absolutePath}, size: ${jsonFile.length()}")
            
            // Create multipart request
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "video",
                    videoFile.name,
                    ProgressRequestBody(
                        videoFile.asRequestBody("video/mp4".toMediaType()),
                        onProgress
                    )
                )
                .addFormDataPart(
                    "metadata",
                    jsonFile.name,
                    jsonFile.asRequestBody("application/json".toMediaType())
                )
                .build()
            
            val request = Request.Builder()
                .url("$BASE_URL$UPLOAD_ENDPOINT")
                .post(requestBody)
                .build()
            
            Log.d(TAG, "Sending request to: $BASE_URL$UPLOAD_ENDPOINT")
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                Log.d(TAG, "Upload successful. Response: $responseBody")
                UploadResult.Success(responseBody ?: "Upload completed successfully")
            } else {
                val errorBody = response.body?.string()
                Log.e(TAG, "Upload failed. Code: ${response.code}, Error: $errorBody")
                UploadResult.Error("Upload failed: ${response.code} - $errorBody")
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "Network error during upload", e)
            UploadResult.Error("Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error during upload", e)
            UploadResult.Error("Unexpected error: ${e.message}")
        }
    }
    
    private fun findVideoFile(fileName: String): File? {
        // Look in Movies/VideoLocationTracker folder
        val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
        val videoDir = File(moviesDir, "VideoLocationTracker")
        val videoFile = File(videoDir, "$fileName.mp4")
        
        return if (videoFile.exists()) {
            Log.d(TAG, "Found video file: ${videoFile.absolutePath}")
            videoFile
        } else {
            Log.w(TAG, "Video file not found at: ${videoFile.absolutePath}")
            null
        }
    }
    
    private fun findJsonFile(fileName: String): File? {
        // Look in Downloads/MyApplication01_LocationData folder
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appDir = File(downloadsDir, "MyApplication01_LocationData")
        val jsonFile = File(appDir, fileName)
        
        return if (jsonFile.exists()) {
            Log.d(TAG, "Found JSON file: ${jsonFile.absolutePath}")
            jsonFile
        } else {
            Log.w(TAG, "JSON file not found at: ${jsonFile.absolutePath}")
            null
        }
    }
}

// Custom RequestBody to track upload progress
class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val onProgress: (Float) -> Unit
) : RequestBody() {
    
    override fun contentType(): MediaType? = requestBody.contentType()
    
    override fun contentLength(): Long = requestBody.contentLength()
    
    override fun writeTo(sink: BufferedSink) {
        val bufferedSink = sink.buffer
        val source = requestBody.source()
        
        val totalSize = contentLength()
        var totalBytesRead = 0L
        
        var read: Long
        while (source.read(bufferedSink.buffer, 8192).also { read = it } != -1L) {
            totalBytesRead += read
            bufferedSink.flush()
            
            val progress = if (totalSize > 0) {
                totalBytesRead.toFloat() / totalSize.toFloat()
            } else {
                0f
            }
            onProgress(progress)
        }
    }
    
    private fun RequestBody.source(): Source {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer
    }
}

// Result sealed class for upload operations
sealed class UploadResult {
    data class Success(val message: String) : UploadResult()
    data class Error(val error: String) : UploadResult()
}
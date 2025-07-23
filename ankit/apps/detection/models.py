from django.db import models
from django.contrib.auth.models import User
import json

class VideoUpload(models.Model):
    video_name = models.CharField(max_length=255)
    video_file = models.FileField(upload_to='uploads/videos/')
    metadata_file = models.FileField(upload_to='uploads/metadata/')
    uploaded_at = models.DateTimeField(auto_now_add=True)
    uploaded_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True)
    
    # Video metadata from JSON
    recording_start_time = models.BigIntegerField(null=True, blank=True)
    recording_duration_ms = models.BigIntegerField(null=True, blank=True)
    location_update_interval_ms = models.BigIntegerField(null=True, blank=True)
    total_location_points = models.IntegerField(default=0)
    
    processing_status = models.CharField(
        max_length=20,
        choices=[
            ('pending', 'Pending'),
            ('processing', 'Processing'),
            ('completed', 'Completed'),
            ('failed', 'Failed')
        ],
        default='pending'
    )
    
    def __str__(self):
        return f"Video: {self.video_name} uploaded at {self.uploaded_at}"

class LocationFrame(models.Model):
    video_upload = models.ForeignKey(VideoUpload, on_delete=models.CASCADE, related_name='location_frames')
    frame_number = models.BigIntegerField()
    timestamp = models.BigIntegerField()  # Unix timestamp in milliseconds
    relative_time_ms = models.BigIntegerField()  # Time since recording started
    latitude = models.FloatField()
    longitude = models.FloatField()
    altitude = models.FloatField(null=True, blank=True)
    accuracy = models.FloatField(null=True, blank=True)
    bearing = models.FloatField(null=True, blank=True)
    speed = models.FloatField(null=True, blank=True)
    
    class Meta:
        ordering = ['frame_number']
    
    def __str__(self):
        return f"Frame {self.frame_number} - ({self.latitude}, {self.longitude})"

class GarbageDetection(models.Model):
    video_upload = models.ForeignKey(VideoUpload, on_delete=models.CASCADE, related_name='detections')
    location_frame = models.ForeignKey(LocationFrame, on_delete=models.CASCADE, null=True, blank=True)
    
    # Detection details
    frame_number = models.BigIntegerField()
    detected_at = models.DateTimeField()
    image_base64 = models.TextField()  # Base64 encoded detection image
    
    # Location data (copied from LocationFrame for easier queries)
    latitude = models.FloatField()
    longitude = models.FloatField()
    
    # Status management
    status = models.CharField(
        max_length=10,
        choices=[('pending', 'Pending'), ('cleaned', 'Cleaned')],
        default='pending'
    )
    
    # Metadata
    confidence_score = models.FloatField(default=0.0)
    detection_count = models.IntegerField(default=1)  # Number of objects detected in frame
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    class Meta:
        ordering = ['-detected_at']
    
    def __str__(self):
        return f"Detection #{self.id} - Frame {self.frame_number} ({self.status})"

class LocationCluster(models.Model):
    """Groups nearby detections for map display"""
    center_latitude = models.FloatField()
    center_longitude = models.FloatField()
    detection_count = models.IntegerField(default=0)
    pending_count = models.IntegerField(default=0)
    cleaned_count = models.IntegerField(default=0)
    
    # Clustering parameters
    radius_meters = models.FloatField(default=50.0)  # Cluster radius
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    def __str__(self):
        return f"Cluster at ({self.center_latitude}, {self.center_longitude}) - {self.detection_count} detections"
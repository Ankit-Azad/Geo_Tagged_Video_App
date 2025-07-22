from django.db import models
from django.contrib.auth import get_user_model
from django.contrib.gis.db import models as gis_models
import json

User = get_user_model()

class VideoUpload(models.Model):
    STATUS_CHOICES = [
        ('processing', 'Processing'),
        ('completed', 'Completed'),
        ('failed', 'Failed'),
    ]
    
    video_file = models.FileField(upload_to='uploads/videos/%Y/%m/%d/')
    metadata_file = models.FileField(upload_to='uploads/metadata/%Y/%m/%d/')
    upload_timestamp = models.DateTimeField(auto_now_add=True)
    processing_status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='processing')
    total_frames = models.IntegerField(default=0)
    processed_frames = models.IntegerField(default=0)
    total_detections = models.IntegerField(default=0)
    
    # Metadata from JSON
    recording_duration_ms = models.BigIntegerField(null=True, blank=True)
    location_update_interval_ms = models.IntegerField(null=True, blank=True)
    recording_start_time = models.BigIntegerField(null=True, blank=True)
    
    def __str__(self):
        return f"Video Upload {self.id} - {self.upload_timestamp}"
    
    @property
    def processing_progress(self):
        if self.total_frames == 0:
            return 0
        return (self.processed_frames / self.total_frames) * 100

class LocationData(models.Model):
    video_upload = models.ForeignKey(VideoUpload, on_delete=models.CASCADE, related_name='location_data')
    frame_number = models.IntegerField()
    timestamp = models.BigIntegerField()  # Unix timestamp
    relative_time_ms = models.BigIntegerField()  # Time from recording start
    latitude = models.FloatField()
    longitude = models.FloatField()
    accuracy = models.FloatField()
    location = gis_models.PointField(geography=True, null=True, blank=True)
    
    class Meta:
        ordering = ['frame_number']
        unique_together = ['video_upload', 'frame_number']
    
    def save(self, *args, **kwargs):
        if self.latitude and self.longitude:
            from django.contrib.gis.geos import Point
            self.location = Point(self.longitude, self.latitude)
        super().save(*args, **kwargs)
    
    def __str__(self):
        return f"Location Frame {self.frame_number} - ({self.latitude}, {self.longitude})"

class GarbageDetection(models.Model):
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('cleaned', 'Cleaned'),
    ]
    
    video_upload = models.ForeignKey(VideoUpload, on_delete=models.CASCADE, related_name='detections')
    frame_number = models.IntegerField()
    timestamp_ms = models.BigIntegerField()  # Time from video start
    
    # Detection details
    garbage_type = models.CharField(max_length=100)
    confidence = models.FloatField()
    bbox_x = models.FloatField()  # Bounding box coordinates
    bbox_y = models.FloatField()
    bbox_width = models.FloatField()
    bbox_height = models.FloatField()
    
    # Location data (copied from LocationData for this frame)
    latitude = models.FloatField()
    longitude = models.FloatField()
    location_accuracy = models.FloatField()
    location = gis_models.PointField(geography=True, null=True, blank=True)
    
    # Status management
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    status_updated_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True)
    status_updated_at = models.DateTimeField(null=True, blank=True)
    
    # Processed image with bounding box
    processed_image = models.ImageField(upload_to='processed/%Y/%m/%d/', null=True, blank=True)
    
    created_at = models.DateTimeField(auto_now_add=True)
    
    class Meta:
        ordering = ['-created_at']
        indexes = [
            models.Index(fields=['status']),
            models.Index(fields=['latitude', 'longitude']),
            models.Index(fields=['garbage_type']),
        ]
    
    def save(self, *args, **kwargs):
        if self.latitude and self.longitude:
            from django.contrib.gis.geos import Point
            self.location = Point(self.longitude, self.latitude)
        super().save(*args, **kwargs)
    
    def __str__(self):
        return f"{self.garbage_type} at Frame {self.frame_number} - {self.status}"

class LocationCluster(models.Model):
    """Groups detections by similar locations"""
    center_latitude = models.FloatField()
    center_longitude = models.FloatField()
    center_location = gis_models.PointField(geography=True)
    detection_count = models.IntegerField(default=0)
    pending_count = models.IntegerField(default=0)
    cleaned_count = models.IntegerField(default=0)
    
    # Cluster parameters
    radius_meters = models.FloatField(default=50.0)  # 50 meter radius
    
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    class Meta:
        ordering = ['-detection_count']
    
    def save(self, *args, **kwargs):
        from django.contrib.gis.geos import Point
        self.center_location = Point(self.center_longitude, self.center_latitude)
        super().save(*args, **kwargs)
    
    def __str__(self):
        return f"Cluster at ({self.center_latitude:.4f}, {self.center_longitude:.4f}) - {self.detection_count} detections"
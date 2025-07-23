from django.contrib import admin
from .models import VideoUpload, LocationFrame, GarbageDetection, LocationCluster

@admin.register(VideoUpload)
class VideoUploadAdmin(admin.ModelAdmin):
    list_display = ['video_name', 'uploaded_at', 'processing_status', 'total_location_points']
    list_filter = ['processing_status', 'uploaded_at']
    search_fields = ['video_name']
    readonly_fields = ['uploaded_at']

@admin.register(LocationFrame)
class LocationFrameAdmin(admin.ModelAdmin):
    list_display = ['video_upload', 'frame_number', 'latitude', 'longitude', 'timestamp']
    list_filter = ['video_upload']
    search_fields = ['video_upload__video_name']

@admin.register(GarbageDetection)
class GarbageDetectionAdmin(admin.ModelAdmin):
    list_display = ['id', 'video_upload', 'frame_number', 'latitude', 'longitude', 'status', 'detected_at']
    list_filter = ['status', 'detected_at', 'video_upload']
    search_fields = ['video_upload__video_name']
    actions = ['mark_as_cleaned', 'mark_as_pending']
    
    def mark_as_cleaned(self, request, queryset):
        queryset.update(status='cleaned')
        self.message_user(request, f'{queryset.count()} detections marked as cleaned.')
    mark_as_cleaned.short_description = "Mark selected detections as cleaned"
    
    def mark_as_pending(self, request, queryset):
        queryset.update(status='pending')
        self.message_user(request, f'{queryset.count()} detections marked as pending.')
    mark_as_pending.short_description = "Mark selected detections as pending"

@admin.register(LocationCluster)
class LocationClusterAdmin(admin.ModelAdmin):
    list_display = ['id', 'center_latitude', 'center_longitude', 'detection_count', 'pending_count', 'cleaned_count']
    list_filter = ['created_at']
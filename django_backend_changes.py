# ==========================================
# DJANGO BACKEND CHANGES
# ==========================================

# 1. views.py - Add this view to handle uploads
# ==========================================

from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_http_methods
import json
import os
from django.conf import settings
from django.core.files.storage import default_storage
from django.core.files.base import ContentFile
import logging

logger = logging.getLogger(__name__)

@csrf_exempt
@require_http_methods(["POST"])
def upload_video_with_metadata(request):
    """
    Handle video and JSON metadata uploads from Android app
    """
    try:
        # Get uploaded files
        video_file = request.FILES.get('video')
        metadata_file = request.FILES.get('metadata')
        
        if not video_file or not metadata_file:
            return JsonResponse({
                'error': 'Both video and metadata files are required',
                'status': 'error'
            }, status=400)
        
        logger.info(f"Received video: {video_file.name}, size: {video_file.size}")
        logger.info(f"Received metadata: {metadata_file.name}, size: {metadata_file.size}")
        
        # Read and parse JSON metadata
        try:
            metadata_content = metadata_file.read().decode('utf-8')
            metadata_json = json.loads(metadata_content)
            logger.info(f"Parsed metadata: {len(metadata_json.get('location_data', []))} location points")
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            return JsonResponse({
                'error': f'Invalid JSON metadata: {str(e)}',
                'status': 'error'
            }, status=400)
        
        # Save video file
        video_path = os.path.join('uploads/videos', video_file.name)
        video_saved_path = default_storage.save(video_path, ContentFile(video_file.read()))
        
        # Save metadata file
        metadata_path = os.path.join('uploads/metadata', metadata_file.name)
        metadata_saved_path = default_storage.save(metadata_path, ContentFile(metadata_content.encode('utf-8')))
        
        # Process the video for garbage detection
        try:
            detection_results = process_video_for_garbage_detection(
                video_path=video_saved_path,
                metadata=metadata_json
            )
            
            return JsonResponse({
                'status': 'success',
                'message': 'Video and metadata uploaded successfully',
                'video_path': video_saved_path,
                'metadata_path': metadata_saved_path,
                'detection_results': detection_results,
                'total_location_points': len(metadata_json.get('location_data', [])),
                'recording_duration': metadata_json.get('recording_duration_ms', 0)
            })
            
        except Exception as e:
            logger.error(f"Error processing video: {str(e)}")
            return JsonResponse({
                'status': 'error',
                'message': f'Error processing video: {str(e)}',
                'video_path': video_saved_path,
                'metadata_path': metadata_saved_path
            }, status=500)
            
    except Exception as e:
        logger.error(f"Upload error: {str(e)}")
        return JsonResponse({
            'error': f'Upload failed: {str(e)}',
            'status': 'error'
        }, status=500)


def process_video_for_garbage_detection(video_path, metadata):
    """
    Process video with garbage detection model and map to location data
    
    Args:
        video_path: Path to uploaded video file
        metadata: JSON metadata with location data
    
    Returns:
        Dictionary with detection results and location mapping
    """
    try:
        # Get location data from metadata
        location_data = metadata.get('location_data', [])
        recording_start_time = metadata.get('recording_start_time', 0)
        location_interval = metadata.get('location_update_interval_ms', 1000)
        
        # Your existing garbage detection code here
        # Replace dummy data with actual metadata
        
        # Example: Process video and get detection results
        detection_results = run_garbage_detection_model(video_path)
        
        # Map detections to location data
        mapped_results = []
        
        for detection in detection_results:
            # Get frame timestamp (in milliseconds from start)
            frame_timestamp = detection.get('timestamp_ms', 0)
            
            # Find corresponding location data
            corresponding_location = find_location_for_timestamp(
                location_data, 
                frame_timestamp, 
                location_interval
            )
            
            if corresponding_location:
                mapped_results.append({
                    'detection': detection,
                    'location': {
                        'latitude': corresponding_location['latitude'],
                        'longitude': corresponding_location['longitude'],
                        'accuracy': corresponding_location['accuracy'],
                        'timestamp': corresponding_location['timestamp'],
                        'frame_number': corresponding_location['frame_number']
                    },
                    'garbage_confidence': detection.get('confidence', 0),
                    'garbage_type': detection.get('class_name', 'unknown')
                })
        
        return {
            'total_detections': len(detection_results),
            'mapped_detections': len(mapped_results),
            'location_mapped_results': mapped_results,
            'processing_status': 'completed'
        }
        
    except Exception as e:
        logger.error(f"Error in garbage detection processing: {str(e)}")
        raise


def find_location_for_timestamp(location_data, frame_timestamp, interval_ms):
    """
    Find the location data corresponding to a video frame timestamp
    
    Args:
        location_data: List of location frames from JSON
        frame_timestamp: Timestamp of video frame (ms from recording start)
        interval_ms: Location update interval in milliseconds
    
    Returns:
        Corresponding location data or None
    """
    if not location_data:
        return None
    
    # Find closest location point
    closest_location = None
    min_time_diff = float('inf')
    
    for location in location_data:
        location_time = location.get('relative_time_ms', 0)
        time_diff = abs(frame_timestamp - location_time)
        
        if time_diff < min_time_diff:
            min_time_diff = time_diff
            closest_location = location
    
    # Only return if within reasonable time window (2x the interval)
    if min_time_diff <= (interval_ms * 2):
        return closest_location
    
    return None


def run_garbage_detection_model(video_path):
    """
    Run your existing garbage detection model
    Replace this with your actual model code
    
    Returns:
        List of detection results with timestamps
    """
    # REPLACE THIS WITH YOUR ACTUAL GARBAGE DETECTION CODE
    
    # Example structure - replace with your model output
    dummy_detections = [
        {
            'timestamp_ms': 1000,  # 1 second into video
            'confidence': 0.85,
            'class_name': 'plastic_bottle',
            'bbox': [100, 150, 200, 300],
            'frame_number': 30
        },
        {
            'timestamp_ms': 5000,  # 5 seconds into video
            'confidence': 0.92,
            'class_name': 'food_waste',
            'bbox': [50, 75, 150, 200],
            'frame_number': 150
        }
    ]
    
    return dummy_detections


# ==========================================
# 2. urls.py - Add this URL pattern
# ==========================================

from django.urls import path
from . import views

urlpatterns = [
    # ... your existing URL patterns ...
    path('api/upload-video/', views.upload_video_with_metadata, name='upload_video_metadata'),
]


# ==========================================
# 3. settings.py - Add these settings
# ==========================================

import os

# File upload settings
FILE_UPLOAD_MAX_MEMORY_SIZE = 100 * 1024 * 1024  # 100MB
DATA_UPLOAD_MAX_MEMORY_SIZE = 100 * 1024 * 1024  # 100MB
MEDIA_ROOT = os.path.join(BASE_DIR, 'media')
MEDIA_URL = '/media/'

# Create upload directories
UPLOAD_DIRS = [
    os.path.join(MEDIA_ROOT, 'uploads', 'videos'),
    os.path.join(MEDIA_ROOT, 'uploads', 'metadata'),
]

for directory in UPLOAD_DIRS:
    os.makedirs(directory, exist_ok=True)

# CORS settings (if using django-cors-headers)
CORS_ALLOWED_ORIGINS = [
    "http://localhost:3000",
    "http://127.0.0.1:3000",
    # Add your local IP for Android testing
    "http://192.168.1.100:8000",
]

CORS_ALLOW_ALL_ORIGINS = True  # Only for development

# ==========================================
# 4. requirements.txt - Add these if not present
# ==========================================

"""
django>=4.2.0
django-cors-headers>=4.0.0
Pillow>=9.0.0
# Your existing ML/CV dependencies for garbage detection
opencv-python>=4.8.0
numpy>=1.24.0
torch>=2.0.0  # or tensorflow
"""


# ==========================================
# 5. Example: Integration with your existing model
# ==========================================

def integrate_with_your_garbage_model(video_path, metadata):
    """
    Example of how to integrate with your existing garbage detection model
    """
    import cv2
    
    # Open video file
    cap = cv2.VideoCapture(video_path)
    fps = cap.get(cv2.CAP_PROP_FPS)
    
    detections = []
    frame_count = 0
    
    while True:
        ret, frame = cap.read()
        if not ret:
            break
        
        # Calculate timestamp for this frame
        timestamp_ms = (frame_count / fps) * 1000
        
        # Run your garbage detection model on this frame
        # detection_result = your_model.detect(frame)
        
        # If garbage detected, add to results
        # if detection_result.has_garbage:
        #     detections.append({
        #         'timestamp_ms': timestamp_ms,
        #         'frame_number': frame_count,
        #         'confidence': detection_result.confidence,
        #         'class_name': detection_result.class_name,
        #         'bbox': detection_result.bbox
        #     })
        
        frame_count += 1
    
    cap.release()
    return detections


# ==========================================
# 6. Database Models (optional)
# ==========================================

from django.db import models

class VideoUpload(models.Model):
    video_file = models.FileField(upload_to='uploads/videos/')
    metadata_file = models.FileField(upload_to='uploads/metadata/')
    upload_timestamp = models.DateTimeField(auto_now_add=True)
    processing_status = models.CharField(max_length=50, default='pending')
    total_detections = models.IntegerField(default=0)
    total_location_points = models.IntegerField(default=0)
    
    def __str__(self):
        return f"Video Upload {self.id} - {self.upload_timestamp}"

class GarbageDetection(models.Model):
    video_upload = models.ForeignKey(VideoUpload, on_delete=models.CASCADE)
    timestamp_ms = models.IntegerField()
    frame_number = models.IntegerField()
    garbage_type = models.CharField(max_length=100)
    confidence = models.FloatField()
    latitude = models.FloatField()
    longitude = models.FloatField()
    location_accuracy = models.FloatField()
    
    def __str__(self):
        return f"{self.garbage_type} at {self.latitude}, {self.longitude}"
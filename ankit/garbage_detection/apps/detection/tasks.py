from celery import shared_task
import logging
import os
from django.core.files.base import ContentFile
from django.utils import timezone
from .models import VideoUpload, LocationData, GarbageDetection, LocationCluster
from .yolo_service import YOLOGarbageDetector
from .clustering_service import LocationClusteringService

logger = logging.getLogger(__name__)

@shared_task
def process_video_task(video_upload_id):
    """
    Background task to process uploaded video with YOLO detection
    """
    try:
        video_upload = VideoUpload.objects.get(id=video_upload_id)
        video_upload.processing_status = 'processing'
        video_upload.save()
        
        logger.info(f"Starting video processing for upload {video_upload_id}")
        
        # Initialize YOLO detector
        detector = YOLOGarbageDetector()
        
        # Get video file path
        video_path = video_upload.video_file.path
        
        # Process video with YOLO
        detections, total_frames = detector.detect_garbage_in_video(video_path)
        
        # Update video upload info
        video_upload.total_frames = total_frames
        video_upload.processed_frames = total_frames
        video_upload.total_detections = len(detections)
        video_upload.save()
        
        # Get location data for this video
        location_data = LocationData.objects.filter(video_upload=video_upload)
        location_dict = {loc.frame_number: loc for loc in location_data}
        
        # Save detections to database
        detection_objects = []
        for detection in detections:
            frame_number = detection['frame_number']
            
            # Find corresponding location data
            location = find_location_for_frame(location_dict, frame_number)
            
            if location:
                # Create processed image with bounding box
                processed_image = detector.draw_detection_on_frame(
                    video_path, frame_number, detection
                )
                
                # Save processed image
                image_filename = f"detection_{video_upload_id}_{frame_number}.jpg"
                image_path = f"processed/{timezone.now().strftime('%Y/%m/%d')}/{image_filename}"
                
                if processed_image:
                    from django.core.files.uploadedfile import InMemoryUploadedFile
                    import io
                    
                    # Convert PIL image to Django file
                    img_io = io.BytesIO()
                    processed_image.save(img_io, format='JPEG', quality=95)
                    img_file = ContentFile(img_io.getvalue(), name=image_filename)
                
                # Create detection object
                garbage_detection = GarbageDetection(
                    video_upload=video_upload,
                    frame_number=frame_number,
                    timestamp_ms=detection['timestamp_ms'],
                    garbage_type=detection['garbage_type'],
                    confidence=detection['confidence'],
                    bbox_x=detection['bbox_x'],
                    bbox_y=detection['bbox_y'],
                    bbox_width=detection['bbox_width'],
                    bbox_height=detection['bbox_height'],
                    latitude=location.latitude,
                    longitude=location.longitude,
                    location_accuracy=location.accuracy,
                    status='pending'
                )
                
                if processed_image:
                    garbage_detection.processed_image = img_file
                
                detection_objects.append(garbage_detection)
        
        # Bulk create detections
        GarbageDetection.objects.bulk_create(detection_objects)
        
        # Create location clusters
        clustering_service = LocationClusteringService()
        clustering_service.create_clusters_for_video(video_upload)
        
        # Mark processing as completed
        video_upload.processing_status = 'completed'
        video_upload.save()
        
        logger.info(f"Video processing completed for upload {video_upload_id}. "
                   f"Found {len(detection_objects)} detections")
        
    except Exception as e:
        logger.error(f"Error processing video {video_upload_id}: {str(e)}")
        try:
            video_upload = VideoUpload.objects.get(id=video_upload_id)
            video_upload.processing_status = 'failed'
            video_upload.save()
        except:
            pass

def find_location_for_frame(location_dict, frame_number):
    """
    Find the location data corresponding to a video frame number
    Uses the same logic as Android app - location per interval
    """
    # First try exact match
    if frame_number in location_dict:
        return location_dict[frame_number]
    
    # Find closest location within reasonable range
    min_distance = float('inf')
    closest_location = None
    
    for loc_frame_num, location in location_dict.items():
        distance = abs(frame_number - loc_frame_num)
        if distance < min_distance:
            min_distance = distance
            closest_location = location
    
    # Only return if within 30 frames (1 second at 30fps)
    if min_distance <= 30:
        return closest_location
    
    return None
from celery import shared_task
from django.utils import timezone
from .models import VideoUpload, LocationFrame, GarbageDetection, LocationCluster
from .yolo_service import garbage_detector
import json
import logging
from datetime import datetime
import cv2

logger = logging.getLogger(__name__)

@shared_task
def process_uploaded_video(video_upload_id):
    """
    Background task to process uploaded video and JSON metadata
    """
    try:
        video_upload = VideoUpload.objects.get(id=video_upload_id)
        video_upload.processing_status = 'processing'
        video_upload.save()
        
        logger.info(f"Starting processing for video upload {video_upload_id}")
        
        # Step 1: Parse JSON metadata and create LocationFrame objects
        location_frames = parse_and_save_location_data(video_upload)
        
        # Step 2: Process video with YOLO and create GarbageDetection objects
        detections_created = process_video_with_yolo(video_upload, location_frames)
        
        # Step 3: Create location clusters for map view
        create_location_clusters(video_upload)
        
        # Mark as completed
        video_upload.processing_status = 'completed'
        video_upload.save()
        
        logger.info(f"Processing completed for video {video_upload_id}. Created {detections_created} detections.")
        
        return {
            'status': 'success',
            'video_upload_id': video_upload_id,
            'detections_created': detections_created,
            'location_frames_created': len(location_frames)
        }
        
    except Exception as e:
        logger.error(f"Error processing video upload {video_upload_id}: {e}")
        
        # Mark as failed
        try:
            video_upload = VideoUpload.objects.get(id=video_upload_id)
            video_upload.processing_status = 'failed'
            video_upload.save()
        except:
            pass
            
        return {
            'status': 'error',
            'error': str(e)
        }

def parse_and_save_location_data(video_upload):
    """Parse JSON metadata and create LocationFrame objects"""
    try:
        with open(video_upload.metadata_file.path, 'r') as f:
            metadata = json.load(f)
        
        # Update video upload with metadata
        video_upload.recording_start_time = metadata.get('recording_start_time', 0)
        video_upload.recording_duration_ms = metadata.get('recording_duration_ms', 0)
        video_upload.location_update_interval_ms = metadata.get('location_update_interval_ms', 1000)
        video_upload.total_location_points = metadata.get('total_location_points', 0)
        video_upload.save()
        
        # Create LocationFrame objects
        location_frames = []
        for location_data in metadata.get('location_data', []):
            location_frame = LocationFrame.objects.create(
                video_upload=video_upload,
                frame_number=location_data.get('frame_number', 0),
                timestamp=location_data.get('timestamp', 0),
                relative_time_ms=location_data.get('relative_time_ms', 0),
                latitude=location_data.get('latitude', 0.0),
                longitude=location_data.get('longitude', 0.0),
                altitude=location_data.get('altitude'),
                accuracy=location_data.get('accuracy'),
                bearing=location_data.get('bearing'),
                speed=location_data.get('speed')
            )
            location_frames.append(location_frame)
        
        logger.info(f"Created {len(location_frames)} location frames for video {video_upload.id}")
        return location_frames
        
    except Exception as e:
        logger.error(f"Error parsing location data: {e}")
        return []

def process_video_with_yolo(video_upload, location_frames):
    """Process video with YOLO and create GarbageDetection objects"""
    try:
        # Get video FPS
        cap = cv2.VideoCapture(video_upload.video_file.path)
        video_fps = cap.get(cv2.CAP_PROP_FPS) or 30
        cap.release()
        
        # Run YOLO detection
        detections_data = garbage_detector.detect_garbage_in_video(
            video_upload.video_file.path, 
            fps=video_fps
        )
        
        detections_created = 0
        
        for detection_data in detections_data:
            frame_number = detection_data['frame_number']
            
            # Find corresponding location frame
            location_frame = garbage_detector.get_location_for_frame(
                frame_number=frame_number,
                location_frames=location_frames,
                video_fps=video_fps,
                location_interval_ms=video_upload.location_update_interval_ms or 1000
            )
            
            if location_frame:
                # Create detection with location data
                detection = GarbageDetection.objects.create(
                    video_upload=video_upload,
                    location_frame=location_frame,
                    frame_number=frame_number,
                    detected_at=timezone.now(),
                    image_base64=detection_data['image_base64'],
                    latitude=location_frame.latitude,
                    longitude=location_frame.longitude,
                    confidence_score=max(detection_data.get('confidence_scores', [0.5])),
                    detection_count=detection_data.get('detection_count', 1)
                )
                detections_created += 1
                logger.debug(f"Created detection for frame {frame_number}")
            else:
                logger.warning(f"No location data found for frame {frame_number}")
        
        return detections_created
        
    except Exception as e:
        logger.error(f"Error processing video with YOLO: {e}")
        return 0

def create_location_clusters(video_upload):
    """Create location clusters for map display"""
    try:
        detections = GarbageDetection.objects.filter(video_upload=video_upload)
        
        # Simple clustering: group detections within 50 meters
        clusters = []
        processed_detections = set()
        
        for detection in detections:
            if detection.id in processed_detections:
                continue
                
            # Find nearby detections
            nearby_detections = []
            for other_detection in detections:
                if other_detection.id in processed_detections:
                    continue
                    
                # Calculate distance (simplified - use proper geospatial calculation in production)
                lat_diff = abs(detection.latitude - other_detection.latitude)
                lng_diff = abs(detection.longitude - other_detection.longitude)
                
                # Rough approximation: 0.0005 degrees ≈ 50 meters
                if lat_diff < 0.0005 and lng_diff < 0.0005:
                    nearby_detections.append(other_detection)
                    processed_detections.add(other_detection.id)
            
            if nearby_detections:
                # Calculate cluster center
                center_lat = sum(d.latitude for d in nearby_detections) / len(nearby_detections)
                center_lng = sum(d.longitude for d in nearby_detections) / len(nearby_detections)
                
                pending_count = sum(1 for d in nearby_detections if d.status == 'pending')
                cleaned_count = sum(1 for d in nearby_detections if d.status == 'cleaned')
                
                cluster = LocationCluster.objects.create(
                    center_latitude=center_lat,
                    center_longitude=center_lng,
                    detection_count=len(nearby_detections),
                    pending_count=pending_count,
                    cleaned_count=cleaned_count
                )
                clusters.append(cluster)
        
        logger.info(f"Created {len(clusters)} location clusters for video {video_upload.id}")
        return clusters
        
    except Exception as e:
        logger.error(f"Error creating location clusters: {e}")
        return []
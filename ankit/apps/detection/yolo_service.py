import cv2
import base64
import numpy as np
from ultralytics import YOLO
from pathlib import Path
from django.conf import settings
import os
import logging

logger = logging.getLogger(__name__)

class YOLOGarbageDetector:
    def __init__(self):
        self.model = None
        self.model_path = getattr(settings, 'YOLO_MODEL_PATH', None)
        self.confidence_threshold = getattr(settings, 'CONFIDENCE_THRESHOLD', 0.5)
        
    def load_model(self):
        """Load YOLO model lazily"""
        if self.model is None:
            try:
                if self.model_path and os.path.exists(self.model_path):
                    logger.info(f"Loading YOLO model from: {self.model_path}")
                    self.model = YOLO(str(self.model_path))
                else:
                    # Fallback to YOLOv8 pretrained model
                    logger.warning("Custom model not found, using YOLOv8n pretrained model")
                    self.model = YOLO('yolov8n.pt')
            except Exception as e:
                logger.error(f"Error loading YOLO model: {e}")
                # Use a dummy model for development
                self.model = None
        return self.model is not None
    
    def detect_garbage_in_video(self, video_path, fps=30):
        """
        Process video and detect garbage in frames
        Returns list of detections with frame numbers and base64 images
        """
        if not self.load_model():
            logger.error("Could not load YOLO model")
            return []
        
        detections = []
        
        try:
            cap = cv2.VideoCapture(str(video_path))
            if not cap.isOpened():
                logger.error(f"Could not open video file: {video_path}")
                return []
            
            # Get video properties
            total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
            video_fps = cap.get(cv2.CAP_PROP_FPS) or fps
            
            logger.info(f"Processing video: {total_frames} frames at {video_fps} FPS")
            
            frame_number = 0
            
            while True:
                ret, frame = cap.read()
                if not ret:
                    break
                
                # Run YOLO detection
                try:
                    results = self.model(frame, conf=self.confidence_threshold)
                    
                    # Check if any objects were detected
                    if len(results[0].boxes) > 0:
                        # Draw bounding boxes on frame
                        annotated_frame = results[0].plot()
                        
                        # Convert to base64
                        _, buffer = cv2.imencode('.jpg', annotated_frame)
                        image_base64 = base64.b64encode(buffer).decode('utf-8')
                        
                        detection_data = {
                            'frame_number': frame_number,
                            'image_base64': image_base64,
                            'confidence_scores': [float(box.conf) for box in results[0].boxes],
                            'detection_count': len(results[0].boxes),
                            'timestamp_ms': (frame_number / video_fps) * 1000  # Convert to milliseconds
                        }
                        
                        detections.append(detection_data)
                        logger.debug(f"Detection found in frame {frame_number}")
                        
                        # Skip ahead to avoid processing consecutive similar frames
                        # Skip next 29 frames (1 second at 30fps)
                        for _ in range(29):
                            ret = cap.read()[0]
                            if not ret:
                                break
                            frame_number += 1
                
                except Exception as e:
                    logger.error(f"Error processing frame {frame_number}: {e}")
                
                frame_number += 1
                
                # Progress logging
                if frame_number % 100 == 0:
                    progress = (frame_number / total_frames) * 100
                    logger.info(f"Processing progress: {progress:.1f}% ({frame_number}/{total_frames})")
            
            cap.release()
            logger.info(f"Video processing complete. Found {len(detections)} detections.")
            
        except Exception as e:
            logger.error(f"Error processing video: {e}")
            
        return detections
    
    def get_location_for_frame(self, frame_number, location_frames, video_fps, location_interval_ms):
        """
        Map video frame to corresponding location data
        
        Args:
            frame_number: Video frame number
            location_frames: List of LocationFrame objects
            video_fps: Video frames per second
            location_interval_ms: Location update interval in milliseconds
        
        Returns:
            Corresponding LocationFrame object or None
        """
        if not location_frames:
            return None
        
        # Calculate time in milliseconds for this frame
        frame_time_ms = (frame_number / video_fps) * 1000
        
        # Find the closest location frame
        closest_location = None
        min_time_diff = float('inf')
        
        for location_frame in location_frames:
            time_diff = abs(location_frame.relative_time_ms - frame_time_ms)
            if time_diff < min_time_diff:
                min_time_diff = time_diff
                closest_location = location_frame
        
        # Only return if within reasonable time window (2x location interval)
        if min_time_diff <= (location_interval_ms * 2):
            return closest_location
        
        return None

# Global detector instance
garbage_detector = YOLOGarbageDetector()
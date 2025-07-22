import cv2
import numpy as np
from ultralytics import YOLO
from django.conf import settings
import os
from PIL import Image, ImageDraw, ImageFont
import logging

logger = logging.getLogger(__name__)

class YOLOGarbageDetector:
    def __init__(self):
        self.model_path = settings.YOLO_MODEL_PATH
        self.confidence_threshold = settings.CONFIDENCE_THRESHOLD
        self.model = None
        self.load_model()
    
    def load_model(self):
        """Load YOLO model"""
        try:
            if os.path.exists(self.model_path):
                self.model = YOLO(self.model_path)
                logger.info(f"YOLO model loaded from {self.model_path}")
            else:
                # Load a pretrained model for demo (you can replace with your trained model)
                self.model = YOLO('yolov8n.pt')
                logger.warning(f"Custom model not found at {self.model_path}, using pretrained YOLOv8n")
        except Exception as e:
            logger.error(f"Error loading YOLO model: {e}")
            self.model = None
    
    def detect_garbage_in_video(self, video_path):
        """
        Process video and detect garbage in each frame
        Returns list of detections with frame numbers
        """
        if not self.model:
            raise Exception("YOLO model not loaded")
        
        detections = []
        cap = cv2.VideoCapture(video_path)
        
        if not cap.isOpened():
            raise Exception(f"Could not open video file: {video_path}")
        
        fps = cap.get(cv2.CAP_PROP_FPS)
        total_frames = int(cap.get(cv2.CAP_PROP_FRAME_COUNT))
        frame_number = 0
        
        logger.info(f"Processing video: {total_frames} frames at {fps} FPS")
        
        while True:
            ret, frame = cap.read()
            if not ret:
                break
            
            # Calculate timestamp for this frame
            timestamp_ms = (frame_number / fps) * 1000
            
            # Run YOLO detection on frame
            results = self.model(frame, conf=self.confidence_threshold)
            
            # Process detections
            for result in results:
                if result.boxes is not None:
                    for box in result.boxes:
                        # Get detection details
                        conf = float(box.conf[0])
                        cls = int(box.cls[0])
                        class_name = self.model.names[cls]
                        
                        # Filter for garbage-related classes (customize as needed)
                        garbage_classes = [
                            'bottle', 'cup', 'plastic', 'can', 'trash', 'garbage',
                            'paper', 'cardboard', 'bag', 'food-waste'
                        ]
                        
                        # For demo, we'll consider some common classes as garbage
                        if any(gc in class_name.lower() for gc in ['bottle', 'cup', 'cell phone']):
                            # Get bounding box coordinates
                            x1, y1, x2, y2 = box.xyxy[0].tolist()
                            
                            detection = {
                                'frame_number': frame_number,
                                'timestamp_ms': timestamp_ms,
                                'garbage_type': class_name,
                                'confidence': conf,
                                'bbox_x': x1,
                                'bbox_y': y1,
                                'bbox_width': x2 - x1,
                                'bbox_height': y2 - y1,
                                'bbox_coordinates': [x1, y1, x2, y2]
                            }
                            detections.append(detection)
            
            frame_number += 1
            
            # Log progress every 100 frames
            if frame_number % 100 == 0:
                logger.info(f"Processed {frame_number}/{total_frames} frames")
        
        cap.release()
        logger.info(f"Video processing complete. Found {len(detections)} garbage detections")
        return detections, total_frames
    
    def draw_detection_on_frame(self, video_path, frame_number, detection):
        """
        Extract a specific frame and draw bounding box on it
        Returns PIL Image with bounding box drawn
        """
        cap = cv2.VideoCapture(video_path)
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_number)
        
        ret, frame = cap.read()
        cap.release()
        
        if not ret:
            return None
        
        # Convert BGR to RGB
        frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        image = Image.fromarray(frame_rgb)
        
        # Draw bounding box
        draw = ImageDraw.Draw(image)
        
        # Get bounding box coordinates
        x1 = detection['bbox_x']
        y1 = detection['bbox_y']
        x2 = x1 + detection['bbox_width']
        y2 = y1 + detection['bbox_height']
        
        # Draw rectangle
        draw.rectangle([x1, y1, x2, y2], outline='red', width=3)
        
        # Draw label
        label = f"{detection['garbage_type']}: {detection['confidence']:.2f}"
        
        try:
            font = ImageFont.truetype("arial.ttf", 16)
        except:
            font = ImageFont.load_default()
        
        # Get text size for background
        bbox = draw.textbbox((0, 0), label, font=font)
        text_width = bbox[2] - bbox[0]
        text_height = bbox[3] - bbox[1]
        
        # Draw background for text
        draw.rectangle(
            [x1, y1 - text_height - 4, x1 + text_width + 4, y1],
            fill='red'
        )
        
        # Draw text
        draw.text((x1 + 2, y1 - text_height - 2), label, fill='white', font=font)
        
        return image
    
    def extract_frame(self, video_path, frame_number):
        """Extract a specific frame from video"""
        cap = cv2.VideoCapture(video_path)
        cap.set(cv2.CAP_PROP_POS_FRAMES, frame_number)
        
        ret, frame = cap.read()
        cap.release()
        
        if ret:
            # Convert BGR to RGB
            frame_rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
            return Image.fromarray(frame_rgb)
        return None
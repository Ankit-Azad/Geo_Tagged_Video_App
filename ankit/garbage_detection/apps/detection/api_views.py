from rest_framework.decorators import api_view
from rest_framework.response import Response
from rest_framework import status
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
import json
import logging
from .models import VideoUpload, LocationData
from .tasks import process_video_task

logger = logging.getLogger(__name__)

@csrf_exempt
@api_view(['POST'])
def upload_video_with_metadata(request):
    """
    Handle video and JSON metadata uploads from Android app
    """
    try:
        # Get uploaded files
        video_file = request.FILES.get('video')
        metadata_file = request.FILES.get('metadata')
        
        if not video_file or not metadata_file:
            return Response({
                'error': 'Both video and metadata files are required',
                'status': 'error'
            }, status=status.HTTP_400_BAD_REQUEST)
        
        logger.info(f"Received video: {video_file.name}, size: {video_file.size}")
        logger.info(f"Received metadata: {metadata_file.name}, size: {metadata_file.size}")
        
        # Read and parse JSON metadata
        try:
            metadata_content = metadata_file.read().decode('utf-8')
            metadata_json = json.loads(metadata_content)
            logger.info(f"Parsed metadata: {len(metadata_json.get('location_data', []))} location points")
        except (UnicodeDecodeError, json.JSONDecodeError) as e:
            return Response({
                'error': f'Invalid JSON metadata: {str(e)}',
                'status': 'error'
            }, status=status.HTTP_400_BAD_REQUEST)
        
        # Create VideoUpload instance
        video_upload = VideoUpload.objects.create(
            video_file=video_file,
            metadata_file=metadata_file,
            recording_duration_ms=metadata_json.get('recording_duration_ms', 0),
            location_update_interval_ms=metadata_json.get('location_update_interval_ms', 1000),
            recording_start_time=metadata_json.get('recording_start_time', 0)
        )
        
        # Save location data
        location_data_list = metadata_json.get('location_data', [])
        for location_item in location_data_list:
            LocationData.objects.create(
                video_upload=video_upload,
                frame_number=location_item.get('frame_number', 0),
                timestamp=location_item.get('timestamp', 0),
                relative_time_ms=location_item.get('relative_time_ms', 0),
                latitude=location_item.get('latitude', 0.0),
                longitude=location_item.get('longitude', 0.0),
                accuracy=location_item.get('accuracy', 0.0)
            )
        
        # Start background processing with Celery
        process_video_task.delay(video_upload.id)
        
        return Response({
            'status': 'success',
            'message': 'Video and metadata uploaded successfully',
            'upload_id': video_upload.id,
            'total_location_points': len(location_data_list),
            'recording_duration': metadata_json.get('recording_duration_ms', 0)
        }, status=status.HTTP_201_CREATED)
        
    except Exception as e:
        logger.error(f"Upload error: {str(e)}")
        return Response({
            'error': f'Upload failed: {str(e)}',
            'status': 'error'
        }, status=status.HTTP_500_INTERNAL_SERVER_ERROR)

@api_view(['GET'])
def upload_status(request, upload_id):
    """
    Check the processing status of an uploaded video
    """
    try:
        video_upload = VideoUpload.objects.get(id=upload_id)
        return Response({
            'upload_id': upload_id,
            'status': video_upload.processing_status,
            'progress': video_upload.processing_progress,
            'total_frames': video_upload.total_frames,
            'processed_frames': video_upload.processed_frames,
            'total_detections': video_upload.total_detections
        })
    except VideoUpload.DoesNotExist:
        return Response({
            'error': 'Upload not found'
        }, status=status.HTTP_404_NOT_FOUND)
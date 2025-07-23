from django.shortcuts import render, get_object_or_404
from django.contrib.auth.decorators import login_required, user_passes_test
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_POST
from rest_framework.decorators import api_view
from rest_framework.response import Response
from .models import VideoUpload, GarbageDetection, LocationCluster
from .tasks import process_uploaded_video
import json
import logging

logger = logging.getLogger(__name__)

@login_required
def detection_list(request):
    """Display all garbage detections with real data"""
    detections = GarbageDetection.objects.all().order_by('-detected_at')
    
    # Check user type for supervisor privileges
    is_supervisor = request.user.user_type == 'supervisor' if hasattr(request.user, 'user_type') else request.user.is_superuser
    
    context = {
        'detections': detections,
        'is_supervisor': is_supervisor,
        'total_detections': detections.count(),
        'pending_count': detections.filter(status='pending').count(),
        'cleaned_count': detections.filter(status='cleaned').count(),
    }
    
    return render(request, 'detection/detection_list.html', context)

@login_required
def video_uploads(request):
    """Display uploaded videos and their processing status"""
    uploads = VideoUpload.objects.all().order_by('-uploaded_at')
    
    context = {
        'uploads': uploads,
    }
    
    return render(request, 'detection/video_uploads.html', context)

@csrf_exempt
@api_view(['POST'])
def upload_video_api(request):
    """API endpoint for Android app uploads"""
    try:
        video_file = request.FILES.get('video')
        metadata_file = request.FILES.get('metadata')
        
        if not video_file or not metadata_file:
            logger.error("Missing files in upload request")
            return Response({
                'error': 'Both video and metadata files are required',
                'status': 'error'
            }, status=400)
        
        # Extract video name from metadata or use filename
        video_name = video_file.name.replace('.mp4', '')
        
        # Create VideoUpload record
        video_upload = VideoUpload.objects.create(
            video_name=video_name,
            video_file=video_file,
            metadata_file=metadata_file,
            processing_status='pending'
        )
        
        # Trigger background processing
        try:
            process_uploaded_video.delay(video_upload.id)
            logger.info(f"Started background processing for upload {video_upload.id}")
        except Exception as e:
            logger.error(f"Failed to start background processing: {e}")
            # Process synchronously as fallback
            process_uploaded_video(video_upload.id)
        
        return Response({
            'status': 'success',
            'message': 'Video uploaded successfully and processing started',
            'upload_id': video_upload.id,
            'processing_status': video_upload.processing_status
        })
        
    except Exception as e:
        logger.error(f"Error in upload_video_api: {e}")
        return Response({
            'error': str(e),
            'status': 'error'
        }, status=500)

@api_view(['GET'])
def upload_status_api(request, upload_id):
    """Check upload processing status"""
    try:
        video_upload = VideoUpload.objects.get(id=upload_id)
        
        # Get detection count
        detection_count = GarbageDetection.objects.filter(video_upload=video_upload).count()
        
        return Response({
            'upload_id': upload_id,
            'status': video_upload.processing_status,
            'video_name': video_upload.video_name,
            'uploaded_at': video_upload.uploaded_at.isoformat(),
            'detection_count': detection_count,
            'total_location_points': video_upload.total_location_points,
        })
        
    except VideoUpload.DoesNotExist:
        return Response({
            'error': 'Upload not found',
            'status': 'error'
        }, status=404)
    except Exception as e:
        logger.error(f"Error in upload_status_api: {e}")
        return Response({
            'error': str(e),
            'status': 'error'
        }, status=500)

@login_required
@user_passes_test(lambda u: getattr(u, 'user_type', None) == 'supervisor' or u.is_superuser)
@require_POST
def update_status(request):
    """Update detection status (supervisor only)"""
    try:
        detection_id = request.POST.get('id')
        detection = get_object_or_404(GarbageDetection, id=detection_id)
        
        # Toggle status
        detection.status = 'cleaned' if detection.status == 'pending' else 'pending'
        detection.save()
        
        logger.info(f"Detection {detection_id} status updated to {detection.status}")
        
        return JsonResponse({
            'status': detection.status,
            'detection_id': detection_id
        })
        
    except Exception as e:
        logger.error(f"Error updating detection status: {e}")
        return JsonResponse({
            'error': str(e)
        }, status=400)

@login_required
def map_view(request):
    """Display garbage detections on map with clustering"""
    # Get all detections for map display
    detections = GarbageDetection.objects.filter(status='pending').select_related('location_frame')
    
    # Get location clusters
    clusters = LocationCluster.objects.all()
    
    context = {
        'detections': detections,
        'clusters': clusters,
        'total_detections': GarbageDetection.objects.count(),
        'pending_count': GarbageDetection.objects.filter(status='pending').count(),
        'cleaned_count': GarbageDetection.objects.filter(status='cleaned').count(),
    }
    
    return render(request, 'maps/map_view.html', context)
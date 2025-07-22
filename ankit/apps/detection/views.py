from django.shortcuts import render
from django.contrib.auth.decorators import login_required
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from rest_framework.decorators import api_view
from rest_framework.response import Response
import json

@login_required
def detection_list(request):
    return render(request, 'detection/detection_list.html')

@login_required
def video_uploads(request):
    return render(request, 'detection/video_uploads.html')

@csrf_exempt
@api_view(['POST'])
def upload_video_api(request):
    """API endpoint for Android app uploads"""
    try:
        video_file = request.FILES.get('video')
        metadata_file = request.FILES.get('metadata')
        
        if not video_file or not metadata_file:
            return Response({
                'error': 'Both video and metadata files are required',
                'status': 'error'
            }, status=400)
        
        # Basic file handling (extend with YOLO processing)
        return Response({
            'status': 'success',
            'message': 'Files received successfully',
            'upload_id': 1  # Placeholder
        })
        
    except Exception as e:
        return Response({
            'error': str(e),
            'status': 'error'
        }, status=500)

@api_view(['GET'])
def upload_status_api(request, upload_id):
    """Check upload processing status"""
    return Response({
        'upload_id': upload_id,
        'status': 'completed',
        'progress': 100
    })
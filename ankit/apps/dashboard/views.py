from django.shortcuts import render
from django.contrib.auth.decorators import login_required
from apps.detection.models import GarbageDetection, VideoUpload

@login_required
def dashboard_view(request):
    # Get real detection statistics
    total_detections = GarbageDetection.objects.count()
    pending_count = GarbageDetection.objects.filter(status='pending').count()
    cleaned_count = GarbageDetection.objects.filter(status='cleaned').count()
    
    # Get recent uploads
    recent_uploads = VideoUpload.objects.order_by('-uploaded_at')[:5]
    
    # Get recent detections for display
    recent_detections = GarbageDetection.objects.order_by('-detected_at')[:10]
    
    # Check user type
    is_supervisor = getattr(request.user, 'user_type', None) == 'supervisor' or request.user.is_superuser
    
    context = {
        'user': request.user,
        'is_supervisor': is_supervisor,
        'total_detections': total_detections,
        'pending_count': pending_count,
        'cleaned_count': cleaned_count,
        'recent_uploads': recent_uploads,
        'recent_detections': recent_detections,
        'upload_count': VideoUpload.objects.count(),
    }
    return render(request, 'dashboard/dashboard.html', context)
from django.shortcuts import render
from django.contrib.auth.decorators import login_required

@login_required
def dashboard_view(request):
    context = {
        'user': request.user,
        'total_detections': 0,  # Will be populated with real data
        'pending_count': 0,
        'cleaned_count': 0,
    }
    return render(request, 'dashboard/dashboard.html', context)
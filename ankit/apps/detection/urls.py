from django.urls import path
from . import views

app_name = 'detection'

urlpatterns = [
    # Web views
    path('', views.detection_list, name='detection_list'),
    path('uploads/', views.video_uploads, name='video_uploads'),
    
    # API endpoints  
    path('upload-video/', views.upload_video_api, name='upload_video_api'),
    path('upload-status/<int:upload_id>/', views.upload_status_api, name='upload_status_api'),
]
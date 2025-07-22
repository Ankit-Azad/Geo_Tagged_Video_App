# Garbage Detection System

A comprehensive Django-based web application for garbage detection using YOLO models with GPS location tracking.

## Features

### 🎯 Core Functionality
- **Video Upload API**: Receives video and JSON metadata from Android app
- **YOLO Detection**: Processes videos using trained garbage detection models
- **GPS Integration**: Maps detections to precise geographic locations
- **Real-time Dashboard**: Shows detection results with timestamps and locations
- **Status Management**: Supervisors can mark locations as cleaned/pending
- **Interactive Maps**: Displays garbage markers with clustering for nearby detections

### 👥 User Management
- **Supervisor Dashboard**: Full access to all features and status updates
- **Worker Dashboard**: View-only access to current detection status
- **Role-based Authentication**: Different permissions for different user types

### 🗺️ Map Features
- **Location Clustering**: Groups nearby detections into single markers
- **Status Visualization**: Different markers for pending vs cleaned locations
- **Interactive Interface**: Click markers to view detection details

## Installation

### Prerequisites
- Python 3.8+
- PostgreSQL with PostGIS extension
- Redis server
- Node.js (optional, for additional frontend features)

### 1. Clone Repository
```bash
git clone <repository-url>
cd ankit
```

### 2. Create Virtual Environment
```bash
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate
```

### 3. Install Dependencies
```bash
pip install -r requirements.txt
```

### 4. Database Setup
```bash
# Create PostgreSQL database
sudo -u postgres psql
CREATE DATABASE garbage_detection;
CREATE USER postgres WITH PASSWORD 'your-password';
GRANT ALL PRIVILEGES ON DATABASE garbage_detection TO postgres;
\q

# Enable PostGIS extension
sudo -u postgres psql -d garbage_detection
CREATE EXTENSION postgis;
\q
```

### 5. Environment Configuration
```bash
cp .env.example .env
# Edit .env with your database and Redis settings
```

### 6. Django Setup
```bash
cd garbage_detection
python manage.py makemigrations
python manage.py migrate
python manage.py createsuperuser
python manage.py collectstatic
```

### 7. Create User Groups
```bash
python manage.py shell
```
```python
from django.contrib.auth import get_user_model
User = get_user_model()

# Create supervisor user
supervisor = User.objects.create_user(
    username='supervisor',
    password='supervisor123',
    user_type='supervisor'
)

# Create worker user
worker = User.objects.create_user(
    username='worker', 
    password='worker123',
    user_type='worker'
)
```

## Running the Application

### 1. Start Redis Server
```bash
redis-server
```

### 2. Start Celery Worker (for background processing)
```bash
cd garbage_detection
celery -A garbage_detection worker -l info
```

### 3. Start Django Development Server
```bash
cd garbage_detection
python manage.py runserver 0.0.0.0:8000
```

### 4. Access the Application
- Web Interface: http://localhost:8000
- API Endpoint: http://localhost:8000/api/upload-video/
- Admin Panel: http://localhost:8000/admin

## API Usage

### Upload Video with Metadata
```http
POST /api/upload-video/
Content-Type: multipart/form-data

video: <video-file.mp4>
metadata: <metadata-file.json>
```

### Check Upload Status
```http
GET /api/upload-status/{upload_id}/
```

## Project Structure

```
ankit/
├── garbage_detection/           # Main Django project
│   ├── garbage_detection/      # Project settings
│   ├── apps/                   # Django applications
│   │   ├── accounts/          # User management
│   │   ├── dashboard/         # Main dashboard
│   │   ├── detection/         # YOLO detection logic
│   │   └── maps/             # Map visualization
│   ├── templates/            # HTML templates
│   ├── static/              # Static files (CSS, JS, images)
│   └── media/               # User uploaded files
├── requirements.txt         # Python dependencies
└── README.md               # This file
```

## User Roles

### Supervisor
- View all detections and their status
- Update status (pending ↔ cleaned)
- Access video upload management
- View detailed analytics

### Worker
- View current status of detections
- Access map view
- Read-only dashboard access

## Technologies Used

- **Backend**: Django 4.2, Django REST Framework
- **Database**: PostgreSQL with PostGIS
- **Task Queue**: Celery with Redis
- **AI/ML**: YOLOv8 (Ultralytics)
- **Maps**: Leaflet.js
- **Frontend**: Bootstrap 5, Font Awesome
- **Image Processing**: OpenCV, PIL

## YOLO Model Integration

The system supports custom trained YOLO models for garbage detection:

1. Place your trained model file at: `garbage_detection/models/yolo_garbage_detection.pt`
2. Update model classes in `apps/detection/yolo_service.py`
3. Adjust confidence threshold in settings if needed

## Map Clustering Logic

The system automatically clusters nearby detections:
- Default clustering radius: 50 meters
- Markers show combined status of clustered detections
- Click markers to see individual detection details

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

## License

This project is licensed under the MIT License.

## Support

For support or questions, please contact the development team.
# 🚀 Windows Setup Guide - Garbage Detection Django Server

## 📋 Prerequisites

1. **Python 3.8+** installed on Windows
   - Download from: https://www.python.org/downloads/
   - ✅ Make sure to check "Add Python to PATH" during installation

2. **Git** (optional, for cloning)
   - Download from: https://git-scm.com/download/win

## 🛠️ Quick Setup (5 minutes)

### Step 1: Open Command Prompt
- Press `Win + R`, type `cmd`, press Enter
- Or search "Command Prompt" in Start menu

### Step 2: Navigate to Project Directory
```cmd
cd path\to\ankit
```

### Step 3: Run Automated Setup
```cmd
setup_and_run.bat
```
This will:
- Create virtual environment
- Install all dependencies
- Setup database
- Create admin user
- Collect static files

### Step 4: Start the Server
```cmd
run_server.bat
```

## 🔧 Manual Setup (if needed)

### 1. Create Virtual Environment
```cmd
python -m venv venv
```

### 2. Activate Virtual Environment
```cmd
venv\Scripts\activate.bat
```

### 3. Install Dependencies
```cmd
pip install -r requirements.txt
```

### 4. Database Setup
```cmd
python manage.py makemigrations accounts
python manage.py makemigrations dashboard  
python manage.py makemigrations detection
python manage.py makemigrations maps
python manage.py makemigrations
python manage.py migrate
```

### 5. Create Admin User
```cmd
python manage.py createsuperuser
```
Follow prompts to create username/password

### 6. Create Test Users
```cmd
python manage.py shell
```
Then run:
```python
from apps.accounts.models import User

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

exit()
```

### 7. Collect Static Files
```cmd
python manage.py collectstatic --noinput
```

### 8. Start Development Server
```cmd
python manage.py runserver 0.0.0.0:8000
```

## 🌐 Access the Application

- **Web Interface**: http://localhost:8000
- **Admin Panel**: http://localhost:8000/admin
- **API Endpoint**: http://localhost:8000/api/upload-video/

## 👥 Test Users

| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| supervisor | supervisor123 | Supervisor | Full access, can toggle status |
| worker | worker123 | Worker | Read-only access |

## 📱 Android App Integration

Update your Android app's UploadService.kt:
```kotlin
// Replace with your computer's IP address
private const val BASE_URL = "http://YOUR_COMPUTER_IP:8000"
```

To find your IP address:
```cmd
ipconfig
```
Look for "IPv4 Address" under your network adapter.

## 🔍 Troubleshooting

### Issue: "Python not found"
**Solution**: Install Python and add to PATH
```cmd
where python
```

### Issue: "Permission denied"
**Solution**: Run Command Prompt as Administrator

### Issue: "Port already in use"
**Solution**: Use different port
```cmd
python manage.py runserver 0.0.0.0:8001
```

### Issue: "Module not found"
**Solution**: Make sure virtual environment is activated
```cmd
venv\Scripts\activate.bat
pip install -r requirements.txt
```

## 📂 Project Structure

```
ankit/
├── manage.py              # Django management script
├── garbage_detection/     # Main project settings
├── apps/                  # Django applications
│   ├── accounts/         # User management
│   ├── dashboard/        # Main dashboard
│   ├── detection/        # YOLO detection & API
│   └── maps/            # Map visualization
├── templates/           # HTML templates
├── static/             # CSS, JS, images
├── media/              # Uploaded files
├── requirements.txt    # Python dependencies
├── setup_and_run.bat  # Automated setup script
└── run_server.bat     # Server start script
```

## 🚀 Production Deployment

For production, consider:
1. **PostgreSQL**: Replace SQLite with PostgreSQL
2. **Redis + Celery**: Add background processing
3. **HTTPS**: Use SSL certificates
4. **Static Files**: Use cloud storage or CDN

## 📞 Support

If you encounter issues:
1. Check Python version: `python --version`
2. Verify virtual environment is active
3. Check error logs in terminal
4. Ensure all dependencies are installed

---

## 🎉 Success!

Once setup is complete, your Django server will:
- ✅ Accept video uploads from Android app
- ✅ Process videos with YOLO (when configured)
- ✅ Provide web dashboard for supervisors/workers
- ✅ Show detection results with location data
- ✅ Allow status management (pending/cleaned)

**Your Android app can now upload videos to this server!** 🎯

## 🔄 How the Integration Works

### Android App → Django Backend Flow:

1. **📱 Android Records Video**: Your app records MP4 video with GPS location data every 0.5-5 seconds
2. **📄 JSON Metadata Created**: Location data saved as JSON file with frame mapping
3. **⬆️ Upload to Django**: Both video and JSON uploaded to `/api/upload-video/`
4. **🤖 YOLO Processing**: Django processes video frames for garbage detection
5. **📍 Location Mapping**: Detected frames mapped to GPS coordinates from JSON
6. **📊 Dashboard Update**: Real-time dashboard shows detections with locations
7. **🗺️ Map Display**: Interactive map shows clustered garbage markers

### Frame-to-Location Mapping:

- **Video FPS**: 30 fps (30 frames per second)
- **Location Interval**: 0.5s (location updated every 500ms)
- **Mapping**: Frames 0-14 use location point 0, frames 15-29 use location point 1, etc.
- **Smart Detection**: YOLO skips similar frames to avoid duplicates

### User Roles:

- **👷 Workers**: View dashboard and map (read-only)
- **👨‍💼 Supervisors**: Can toggle detection status (pending ↔ cleaned)

## 🔧 Advanced Setup (Optional)

### Enable Background Processing with Celery:

```cmd
# Install Redis (for Windows)
# Download from: https://github.com/microsoftarchive/redis/releases

# Install Celery
pip install celery redis

# Start Redis server (in separate terminal)
redis-server

# Start Celery worker (in separate terminal)
celery -A garbage_detection worker --loglevel=info
```

### Switch to PostgreSQL:

```cmd
# Install PostgreSQL
pip install psycopg2-binary

# Update settings.py
DATABASES = {
    'default': {
        'ENGINE': 'django.db.backends.postgresql',
        'NAME': 'garbage_detection',
        'USER': 'postgres',
        'PASSWORD': 'your_password',
        'HOST': 'localhost',
        'PORT': '5432',
    }
}
```

## 📱 Android App Configuration

Update your `UploadService.kt`:

```kotlin
companion object {
    private const val BASE_URL = "http://YOUR_COMPUTER_IP:8000"
    private const val UPLOAD_ENDPOINT = "/api/upload-video/"
}
```

Replace `YOUR_COMPUTER_IP` with your actual IP address from `ipconfig`.

## 🎯 Testing the Integration

1. **Start Django Server**: `python manage.py runserver 0.0.0.0:8000`
2. **Record Video**: Use your Android app to record a video with location
3. **Upload**: Tap the upload button in your app
4. **Check Dashboard**: Visit `http://localhost:8000` to see processing status
5. **View Results**: Check detections at `http://localhost:8000/detection/`
6. **See Map**: View locations at `http://localhost:8000/maps/`

## 🐛 Troubleshooting Integration

### Android App Issues:
- **Upload fails**: Check server IP and port
- **No response**: Ensure Django server is running
- **Permission denied**: Check file permissions

### Django Issues:
- **No detections**: Check YOLO model path in settings
- **Processing stuck**: Check Celery worker status
- **Location mapping wrong**: Verify JSON format

### Common Solutions:
```cmd
# Check server logs
python manage.py runserver --verbosity=2

# Test API directly
curl -X POST http://localhost:8000/api/upload-video/ -F "video=@test.mp4" -F "metadata=@test.json"

# Reset database if needed
python manage.py flush
python manage.py migrate
```

**Your Android app is now fully integrated with the Django backend for automated garbage detection!** 🎉
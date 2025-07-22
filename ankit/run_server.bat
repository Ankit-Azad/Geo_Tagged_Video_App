@echo off
echo ========================================
echo   Starting Garbage Detection Server
echo ========================================

echo.
echo Activating virtual environment...
call venv\Scripts\activate.bat

echo.
echo Starting Django development server...
echo Server will be available at: http://localhost:8000
echo API endpoint: http://localhost:8000/api/upload-video/
echo.
echo Press Ctrl+C to stop the server
echo.

python manage.py runserver 0.0.0.0:8000
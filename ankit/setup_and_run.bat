@echo off
echo ========================================
echo    Garbage Detection Django Setup
echo ========================================

echo.
echo 1. Creating virtual environment...
python -m venv venv

echo.
echo 2. Activating virtual environment...
call venv\Scripts\activate.bat

echo.
echo 3. Installing dependencies...
pip install -r requirements.txt

echo.
echo 4. Making migrations...
python manage.py makemigrations accounts
python manage.py makemigrations dashboard
python manage.py makemigrations detection
python manage.py makemigrations maps
python manage.py makemigrations

echo.
echo 5. Applying migrations...
python manage.py migrate

echo.
echo 6. Creating superuser (follow prompts)...
python manage.py createsuperuser

echo.
echo 7. Collecting static files...
python manage.py collectstatic --noinput

echo.
echo ========================================
echo       Setup Complete!
echo ========================================
echo.
echo To run the server, use: python manage.py runserver 0.0.0.0:8000
echo Then visit: http://localhost:8000
echo.
pause
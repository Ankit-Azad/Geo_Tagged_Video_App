from django.contrib.auth.models import AbstractUser
from django.db import models

class User(AbstractUser):
    USER_TYPES = (
        ('supervisor', 'Supervisor'),
        ('worker', 'Worker'),
    )
    
    user_type = models.CharField(max_length=20, choices=USER_TYPES, default='worker')
    phone_number = models.CharField(max_length=15, blank=True)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)
    
    def __str__(self):
        return f"{self.username} ({self.get_user_type_display()})"
    
    def is_supervisor(self):
        return self.user_type == 'supervisor'
    
    def is_worker(self):
        return self.user_type == 'worker'
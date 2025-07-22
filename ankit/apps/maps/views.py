from django.shortcuts import render
from django.contrib.auth.decorators import login_required

@login_required
def map_view(request):
    return render(request, 'maps/map_view.html')
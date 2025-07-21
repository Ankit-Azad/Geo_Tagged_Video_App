# Video Location Tracker - Android App

An Android application built with Kotlin that records video while simultaneously tracking GPS location data and saving it to JSON format. This app is perfect for applications requiring synchronized video and location data, such as field work documentation, travel vlogs, or security applications.

## Features

- **Video Recording**: High-quality video recording using Android's CameraX API
- **Real-time Location Tracking**: GPS location tracking during video recording
- **Configurable Location Update Intervals**: Choose from multiple refresh rates (0.5s to 5s)
- **JSON Data Export**: Location data saved in structured JSON format
- **Real-time UI Updates**: Live display of location accuracy, recording time, and frame count
- **Permission Management**: Automatic handling of camera and location permissions

## Technical Specifications

### Location Update Intervals

The app offers several location update intervals to balance accuracy with battery usage:

- **Every 0.5 seconds (2 FPS)**: High precision tracking (500ms interval)
- **Every 1 second (1 FPS)**: Standard tracking (1000ms interval) - Default
- **Every 2 seconds (0.5 FPS)**: Battery-efficient tracking (2000ms interval)
- **Every 5 seconds (0.2 FPS)**: Ultra battery-efficient tracking (5000ms interval)

### Minimum Requirements

- **Android Version**: API 24 (Android 7.0) or higher
- **Hardware**: Camera with video recording capability
- **Permissions**: Camera, Audio Recording, Fine Location, Coarse Location
- **Storage**: External storage for video and JSON files

## Project Structure

```
app/
├── src/main/
│   ├── java/com/example/videolocationtracker/
│   │   ├── MainActivity.kt              # Main activity with video recording logic
│   │   └── LocationFrame.kt             # Data classes for location data
│   ├── res/
│   │   ├── layout/
│   │   │   └── activity_main.xml        # Main UI layout
│   │   ├── values/
│   │   │   ├── colors.xml               # App colors
│   │   │   ├── strings.xml              # String resources
│   │   │   └── themes.xml               # App themes
│   │   ├── drawable/
│   │   │   └── ic_videocam.xml          # Video camera icon
│   │   └── xml/
│   │       ├── file_paths.xml           # File provider paths
│   │       ├── backup_rules.xml         # Backup configuration
│   │       └── data_extraction_rules.xml # Data extraction rules
│   └── AndroidManifest.xml              # App permissions and configuration
├── build.gradle.kts                     # App-level dependencies
└── proguard-rules.pro                   # ProGuard configuration
```

## Key Dependencies

```kotlin
// Camera and Video
implementation("androidx.camera:camera-core:1.3.1")
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-video:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Location Services
implementation("com.google.android.gms:play-services-location:21.0.1")

// JSON handling
implementation("com.google.code.gson:gson:2.10.1")
```

## JSON Output Format

The app generates a JSON file for each recording with the following structure:

```json
{
  "video_name": "2024-01-15-14-30-25-123",
  "recording_start_time": 1705320625000,
  "recording_duration_ms": 30000,
  "location_update_interval_ms": 1000,
  "total_location_points": 30,
  "location_data": [
    {
      "frame_number": 0,
      "timestamp": 1705320625000,
      "relative_time_ms": 0,
      "latitude": 37.7749,
      "longitude": -122.4194,
      "altitude": 45.0,
      "accuracy": 5.0,
      "bearing": 180.5,
      "speed": 2.5
    }
  ]
}
```

### JSON Field Descriptions

- **video_name**: Timestamp-based filename of the recorded video
- **recording_start_time**: Unix timestamp when recording started
- **recording_duration_ms**: Total recording duration in milliseconds
- **location_update_interval_ms**: Configured interval between location updates
- **total_location_points**: Total number of location points captured
- **location_data**: Array of location frames with:
  - **frame_number**: Sequential frame number starting from 0
  - **timestamp**: Unix timestamp of this location reading
  - **relative_time_ms**: Time since recording started
  - **latitude/longitude**: GPS coordinates in decimal degrees
  - **altitude**: Elevation in meters above sea level
  - **accuracy**: GPS accuracy radius in meters
  - **bearing**: Direction of travel in degrees (0-360, null if unavailable)
  - **speed**: Movement speed in meters per second (null if unavailable)

## Installation & Setup

1. **Clone the repository** or create a new Android Studio project
2. **Copy all the provided files** into your project structure
3. **Open in Android Studio** (Arctic Fox or newer recommended)
4. **Sync the project** to download dependencies
5. **Build and run** on a physical device (camera required)

### Important Notes

- **Physical Device Required**: Camera and GPS functionality requires a real Android device
- **Permissions**: The app will request camera, microphone, and location permissions on first launch
- **Storage Location**: JSON files are saved to the app's external files directory
- **Video Storage**: Videos are saved to the device's Movies folder under "VideoLocationTracker"

## Usage Instructions

1. **Launch the app** and grant required permissions
2. **Select location update interval** from the dropdown menu
3. **Tap START** to begin recording video and tracking location
4. **Monitor real-time status** showing GPS coordinates and accuracy
5. **Tap STOP** to end recording
6. **Check device storage** for the generated video and JSON files

## Location Accuracy & Battery Optimization

### Accuracy Considerations

- **GPS Accuracy**: Typical accuracy ranges from 3-5 meters in open areas
- **Indoor Performance**: Location accuracy may be significantly reduced indoors
- **Update Intervals**: More frequent updates provide smoother tracking but consume more battery
- **Movement Detection**: The app includes speed and bearing data when available

### Battery Usage

| Interval | Battery Impact | Use Case |
|----------|---------------|----------|
| 0.5s (2 FPS) | High | Precise tracking for sports, detailed mapping |
| 1s (1 FPS) | Medium | General purpose recording |
| 2s (0.5 FPS) | Low | Long duration recordings |
| 5s (0.2 FPS) | Very Low | Extended recordings, basic tracking |

## Troubleshooting

### Common Issues

1. **Permissions Denied**: Go to Settings > Apps > Video Location Tracker > Permissions
2. **Location Not Found**: Ensure GPS is enabled and you're in an area with good signal
3. **Video Quality Issues**: Adjust video quality in the camera configuration
4. **Storage Full**: Check device storage space for video and JSON files

### Performance Tips

- Use lower location update intervals for longer recordings
- Close other GPS-using apps to improve location accuracy
- Ensure device has sufficient storage space
- Keep the device charged during long recordings

## Future Enhancements

Potential features for future versions:
- Export to GPX format for mapping applications
- Integration with cloud storage services
- Real-time map overlay showing recording path
- Custom video quality settings
- Background recording capability
- Integration with external sensors (heart rate, etc.)

## Contributing

Feel free to submit issues, fork the repository, and create pull requests for any improvements.

## License

This project is open source and available under the MIT License.

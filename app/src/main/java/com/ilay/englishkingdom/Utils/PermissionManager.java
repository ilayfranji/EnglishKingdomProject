package com.ilay.englishkingdom.Utils;

import android.app.Activity;
import android.content.pm.PackageManager; // Used to check if permission is granted
import androidx.core.app.ActivityCompat; // Used to request permissions
import androidx.core.content.ContextCompat; // Used to check permission status

public class PermissionManager {
    // This class handles ALL permission requests in the app in one place
    // Instead of repeating permission code in every Activity, we just call this class
    // Think of it as a helper that knows how to ask the user for permissions politely

    // ==================== REQUEST CODES ====================
    // Each permission request needs a unique number so we know which request the user responded to
    // When Android tells us the user's answer, it passes back this number
    // We use constants (final) so these numbers never change accidentally

    public static final int CAMERA_PERMISSION_CODE = 101; // ID for camera permission requests
    public static final int GALLERY_PERMISSION_CODE = 102; // ID for gallery permission requests

    // ==================== CHECK CAMERA PERMISSION ====================

    public static boolean hasCameraPermission(Activity activity) {
        // Returns true if camera permission is already granted, false if not
        // ContextCompat.checkSelfPermission checks the current status of a permission
        // PERMISSION_GRANTED means the user already allowed it
        return ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // ==================== CHECK GALLERY PERMISSION ====================

    public static boolean hasGalleryPermission(Activity activity) {
        // Gallery permission is different depending on Android version
        // Android 13+ (API 33+) uses READ_MEDIA_IMAGES (more specific permission)
        // Android 12 and below uses READ_EXTERNAL_STORAGE (broader permission)
        // Build.VERSION.SDK_INT gives us the Android version number of the current device
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - check READ_MEDIA_IMAGES
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 and below - check READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ==================== REQUEST CAMERA PERMISSION ====================

    public static void requestCameraPermission(Activity activity) {
        // Asks the user to allow camera access
        // Android shows a system popup: "Allow English Kingdom to take pictures and video?"
        // The user's answer (Allow/Deny) comes back in onRequestPermissionsResult()
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE); // Pass our ID so we know it's for camera
    }

    // ==================== REQUEST GALLERY PERMISSION ====================

    public static void requestGalleryPermission(Activity activity) {
        // Asks the user to allow gallery/storage access
        // Same as camera - Android shows a system popup and result comes back in onRequestPermissionsResult()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ - request READ_MEDIA_IMAGES
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},
                    GALLERY_PERMISSION_CODE);
        } else {
            // Android 12 and below - request READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    GALLERY_PERMISSION_CODE);
        }
    }
}
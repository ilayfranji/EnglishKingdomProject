package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;

import com.ilay.englishkingdom.Utils.PermissionManager;

public class ImagePickerHelper {
    // This class has ONE job: show the camera/gallery picker and return the picked image
    // It is used by AddCategoryDialog, EditCategoryDialog, and RegisterActivity
    // so we write the camera/gallery/permission logic ONCE here instead of 3 times

    // ==================== CALLBACK INTERFACE ====================

    public interface OnImagePickedListener {
        // This is a "callback" - like a phone number we call when the photo is ready
        // The class that creates ImagePickerHelper must implement this method
        // When a photo is picked, we call listener.onImagePicked() to pass the photo back
        void onImagePicked(Uri uri, boolean fromGallery);
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to show dialogs and toasts
    private final OnImagePickedListener listener; // Who to notify when photo is picked
    private final ActivityResultLauncher<String> galleryLauncher; // Opens the gallery app
    private final ActivityResultLauncher<Uri> cameraLauncher; // Opens the camera app

    private Uri pendingCameraUri = null; // The temp file URI created before launching camera
    // We save it here because the camera needs a file to save into BEFORE it opens
    // and we need to remember it for when the camera returns the result

    private boolean waitingForCamera = false; // true = we asked for camera permission, waiting for user response
    private boolean waitingForGallery = false; // true = we asked for gallery permission, waiting for user response
    // We need these flags because after requesting permission, Android calls onRequestPermissionsResult()
    // and we need to know WHAT we were trying to do before the permission request

    // ==================== CONSTRUCTOR ====================

    public ImagePickerHelper(Activity activity, OnImagePickedListener listener,
                             ActivityResultLauncher<String> galleryLauncher,
                             ActivityResultLauncher<Uri> cameraLauncher) {
        // Constructor saves all the things we need to do our job
        // The launchers are passed in from the Activity because they MUST be created
        // in the Activity using registerForActivityResult() - Android requires this
        // We can't create them here inside a helper class
        this.activity = activity;
        this.listener = listener;
        this.galleryLauncher = galleryLauncher;
        this.cameraLauncher = cameraLauncher;
    }

    // ==================== SHOW PICKER ====================

    public void show() {
        // Shows a popup with two choices: Camera or Gallery
        // Called whenever admin taps "Add Image" or "Change Image"
        new AlertDialog.Builder(activity)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"📷 Camera", "🖼️ Gallery"}, (dialog, which) -> {
                    if (which == 0) { // Index 0 = Camera was tapped
                        if (PermissionManager.hasCameraPermission(activity)) {
                            // Permission already granted - launch camera immediately
                            launchCamera();
                        } else {
                            // Permission not granted yet - ask the user
                            // We set waitingForCamera = true so when permission result comes back
                            // we know we should launch the camera
                            waitingForCamera = true;
                            PermissionManager.requestCameraPermission(activity);
                        }
                    } else { // Index 1 = Gallery was tapped
                        if (PermissionManager.hasGalleryPermission(activity)) {
                            // Permission already granted - launch gallery immediately
                            launchGallery();
                        } else {
                            // Permission not granted yet - ask the user
                            waitingForGallery = true;
                            PermissionManager.requestGalleryPermission(activity);
                        }
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close, do nothing
                .show();
    }

    // ==================== PERMISSION RESULT ====================

    public void onPermissionResult(int requestCode, int[] grantResults) {
        // The Activity's onRequestPermissionsResult() calls this method
        // because ImagePickerHelper is the one who requested the permission
        // and it's the one who knows what to do after getting the result

        // Check if the user tapped Allow (PERMISSION_GRANTED) or Deny
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == PermissionManager.CAMERA_PERMISSION_CODE) {
            // This is the response for our camera permission request
            if (granted && waitingForCamera) {
                // User allowed camera AND we were waiting to launch it - launch now
                launchCamera();
            } else if (!granted) {
                // User denied camera permission - show a message
                Toast.makeText(activity, "Camera permission denied.", Toast.LENGTH_SHORT).show();
            }
            waitingForCamera = false; // Reset flag - we're no longer waiting

        } else if (requestCode == PermissionManager.GALLERY_PERMISSION_CODE) {
            // This is the response for our gallery permission request
            if (granted && waitingForGallery) {
                // User allowed gallery AND we were waiting to launch it - launch now
                launchGallery();
            } else if (!granted) {
                Toast.makeText(activity, "Gallery permission denied.", Toast.LENGTH_SHORT).show();
            }
            waitingForGallery = false; // Reset flag
        }
    }

    // ==================== LAUNCHER RESULT CALLBACKS ====================

    public void onGalleryResult(Uri uri) {
        // The Activity's galleryLauncher calls this after user picks an image
        // We pass the result to whoever is listening (Add dialog, Edit dialog, Register)
        if (uri != null) listener.onImagePicked(uri, true); // true = came from gallery
    }

    public void onCameraResult(boolean success) {
        // The Activity's cameraLauncher calls this after user takes a photo
        // success = true means photo was taken, false means user cancelled
        if (success && pendingCameraUri != null) {
            listener.onImagePicked(pendingCameraUri, false); // false = came from camera
        } else {
            pendingCameraUri = null; // User cancelled - clear the temp URI
        }
    }

    // ==================== GETTERS ====================

    public Uri getPendingCameraUri() {
        // LearnActivity needs this to save/restore the URI when Android kills the activity
        // (e.g. when camera opens Android might kill the app to free memory)
        return pendingCameraUri;
    }

    public void setPendingCameraUri(Uri uri) {
        // LearnActivity calls this to restore the URI after Android recreates the activity
        pendingCameraUri = uri;
    }

    // ==================== PRIVATE HELPERS ====================

    private void launchCamera() {
        // Creates a temp file first, then opens the camera pointing to that file
        // The camera needs to know WHERE to save the photo before it opens
        pendingCameraUri = createTempUri();
        cameraLauncher.launch(pendingCameraUri);
    }

    private void launchGallery() {
        // Opens the gallery - "image/*" means accept any image type (jpg, png, etc.)
        galleryLauncher.launch("image/*");
    }

    private Uri createTempUri() {
        // Creates a temporary empty file in our app's private cache folder
        // System.currentTimeMillis() makes the filename unique every time
        java.io.File photo = new java.io.File(activity.getCacheDir(),
                "temp_photo_" + System.currentTimeMillis() + ".jpg");
        // FileProvider converts the file path into a secure URI the camera app can write to
        // Without FileProvider the camera would be blocked from saving (Android security rule)
        return androidx.core.content.FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".provider", photo);
    }
}
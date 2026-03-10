package com.ilay.englishkingdom; // The package where this file lives - in the main package, not in Activities

import android.app.Application; // Application is a base class provided by Android that runs before any Activity starts

import com.cloudinary.android.MediaManager; // MediaManager is Cloudinary's main class - we use it to upload images

import java.util.HashMap; // HashMap is a data structure that stores key-value pairs - like a dictionary
import java.util.Map; // Map is the interface that HashMap implements

// AppInitializer extends Application - this means Android will run this class FIRST before any screen opens
// Its job is to initialize and set up everything the app needs before any screen opens
public class AppInitializer extends Application {

    @Override
    public void onCreate() { // onCreate runs automatically when the app first starts - before any Activity
        super.onCreate(); // Always call the parent's onCreate first - this is required

        initCloudinary(); // Call our method to set up Cloudinary
    }

    private void initCloudinary() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "djbp6p30q"); // Our Cloudinary cloud name - identifies our account
        config.put("api_key", "124468166385282"); // API key is required by the SDK - it is NOT a secret
        config.put("api_secret", "Oea9YawwemULIaW8SNZD936pL6U"); // Required by SDK for initialization
        // Even though we include the secret here, our uploads are still unsigned
        // because we use an unsigned upload_preset in our upload calls
        // This means no paid features are used and we stay on the free tier
        config.put("secure", true); // Use HTTPS for all image URLs

        MediaManager.init(this, config);
    }
}
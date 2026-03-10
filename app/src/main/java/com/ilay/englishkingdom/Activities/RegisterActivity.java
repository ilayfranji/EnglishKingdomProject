package com.ilay.englishkingdom.Activities;

import android.Manifest; // Contains all permission constants
import android.content.pm.PackageManager; // Used to check permission status
import android.net.Uri; // Uri represents the address of a file - used for the profile picture
import android.os.Bundle; // Used when creating the activity
import android.util.Patterns; // Used to validate email format
import android.widget.Button; // Used for buttons
import android.widget.EditText; // Used for text input fields
import android.widget.ImageView; // Used to display the profile picture
import android.widget.TextView; // Used for clickable texts
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Modern way to handle camera/gallery results
import androidx.activity.result.contract.ActivityResultContracts; // Contains contracts for camera and gallery
import androidx.appcompat.app.AlertDialog; // Used to show popup windows
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.core.app.ActivityCompat; // Used to request permissions
import androidx.core.content.ContextCompat; // Used to check if permission is granted

import com.bumptech.glide.Glide; // Used to load and display images
import com.cloudinary.android.MediaManager; // Cloudinary's main class for uploading images
import com.cloudinary.android.callback.ErrorInfo; // Represents an error from Cloudinary
import com.cloudinary.android.callback.UploadCallback; // Callback for when upload finishes
import com.google.firebase.auth.FirebaseAuth; // Firebase Authentication
import com.google.firebase.auth.FirebaseAuthUserCollisionException; // Thrown when email is already registered
import com.google.firebase.auth.FirebaseUser; // Represents the currently logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Firestore database
import com.ilay.englishkingdom.Models.UserRole; // Our UserRole enum
import com.ilay.englishkingdom.R; // Used to reference our XML resources

import java.util.HashMap; // Used to create a map of key/value pairs to save in Firestore
import java.util.Map; // Used with HashMap

public class RegisterActivity extends AppCompatActivity {

    // Declare all UI elements
    private EditText etFirstName; // Input field for first name
    private EditText etLastName; // Input field for last name
    private EditText etEmail; // Input field for email
    private EditText etPassword; // Input field for password
    private EditText etConfirmPassword; // Input field for confirm password
    private Button btnRegister; // Register button
    private TextView tvLogin; // Already have account clickable text
    private ImageView imgProfilePicture; // The circular profile picture

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private FirebaseFirestore db; // Our connection to Firestore database

    private Uri selectedImageUri = null; // Stores the URI of the selected profile picture - null means no image selected

    // ActivityResultLauncher for gallery - opens gallery and waits for user to pick an image
    private ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), // Contract for picking content from gallery
            uri -> { // This runs when the user picks an image
                if (uri != null) { // If user picked an image and didn't cancel
                    selectedImageUri = uri; // Save the image URI
                    // Load the selected image into the profile picture ImageView using Glide
                    // circleCrop() makes the image circular to match our circular avatar design
                    Glide.with(this).load(uri).circleCrop().into(imgProfilePicture);
                }
            });

    // ActivityResultLauncher for camera - opens camera and waits for user to take a photo
    private ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), // Contract for taking a picture
            success -> { // This runs when the user takes a photo
                if (success && selectedImageUri != null) { // If photo was taken successfully
                    // Load the taken photo into the profile picture ImageView
                    Glide.with(this).load(selectedImageUri).circleCrop().into(imgProfilePicture);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Connect each Java variable to its XML view
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        imgProfilePicture = findViewById(R.id.imgProfilePicture); // Connect profile picture ImageView

        // Profile picture click - clicking the avatar opens the camera/gallery picker
        imgProfilePicture.setOnClickListener(v -> showImagePickerDialog());

        // Register button click
        btnRegister.setOnClickListener(v -> registerUser());

        // Already have account text click - go back to LoginActivity
        tvLogin.setOnClickListener(v -> finish());
    }

    private void showImagePickerDialog() { // Shows a popup to choose between camera and gallery
        new AlertDialog.Builder(this)
                .setTitle("Choose Image Source") // Popup title
                .setItems(new String[]{"📷 Camera", "🖼️ Gallery"}, (dialog, which) -> {
                    // "which" is the index of the option the user selected - 0 = Camera, 1 = Gallery
                    if (which == 0) { // Camera selected
                        // Before launching the camera we must check if the app has camera permission
                        // ContextCompat.checkSelfPermission checks if a specific permission is granted
                        // Manifest.permission.CAMERA is the camera permission constant
                        // PackageManager.PERMISSION_GRANTED means the permission is already granted
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            // Permission is already granted - we can launch the camera directly
                            selectedImageUri = createTempImageUri(); // Create a temporary file for the photo
                            cameraLauncher.launch(selectedImageUri); // Launch the camera
                        } else {
                            // Permission is NOT granted - we need to ask the user for it
                            // requestPermissions shows the system permission popup to the user
                            // new String[]{Manifest.permission.CAMERA} is the list of permissions we are requesting
                            // 100 is a request code - just a number we choose to identify this specific request
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA},
                                    100);
                        }
                    } else { // Gallery selected - no permission needed for gallery
                        galleryLauncher.launch("image/*"); // Launch gallery - "image/*" means any image type
                    }
                })
                .setNegativeButton("Cancel", null) // Cancel button - closes the popup
                .show(); // Show the popup
    }

    // This method runs automatically after the user responds to the permission popup
    // requestCode is the number we passed in requestPermissions - helps us know which permission this is
    // grantResults is the list of results - whether each permission was granted or denied
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults); // Always call parent first
        if (requestCode == 100) { // Check if this is our camera permission request (code 100)
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // grantResults.length > 0 makes sure the array is not empty
                // grantResults[0] == PERMISSION_GRANTED means the user pressed "Allow"
                selectedImageUri = createTempImageUri(); // Create a temporary file for the photo
                cameraLauncher.launch(selectedImageUri); // Launch the camera now that we have permission
            } else {
                // User pressed "Deny" - we cannot use the camera
                Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Uri createTempImageUri() { // Creates a temporary file URI for the camera photo
        // Create a temporary file in the app's internal cache folder
        // getCacheDir() returns the internal cache folder which is always accessible
        java.io.File photoFile = new java.io.File(getCacheDir(), "temp_photo_" + System.currentTimeMillis() + ".jpg");
        // FileProvider converts the file path into a URI that the camera app can use safely
        return androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
    }

    private void registerUser() { // This method handles the registration logic
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false; // Tracks if any field has an error

        // Validate First Name
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required");
            hasError = true;
        } else if (!firstName.matches("[a-zA-Z]+")) { // Only letters allowed
            etFirstName.setError("First name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(firstName.charAt(0))) {
            // charAt(0) gets the first character - isUpperCase() checks if it is a capital letter
            etFirstName.setError("First name must start with a capital letter");
            hasError = true;
        }

        // Validate Last Name
        if (lastName.isEmpty()) {
            etLastName.setError("Last name is required");
            hasError = true;
        } else if (!lastName.matches("[a-zA-Z]+")) { // Only letters allowed
            etLastName.setError("Last name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(lastName.charAt(0))) {
            // Same check as first name - first character must be capital
            etLastName.setError("Last name must start with a capital letter");
            hasError = true;
        }

        // Validate Email
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            hasError = true;
        }

        // Validate Password
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            hasError = true;
        } else if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        // Validate Confirm Password
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) return; // If any field has an error stop here - all errors show at once

        // All validation passed - check if user selected a profile picture
        if (selectedImageUri != null) { // If user selected a profile picture
            uploadProfilePictureAndRegister(firstName, lastName, email, password); // Upload image first then register
        } else { // No profile picture selected - register without one
            createFirebaseAccount(firstName, lastName, email, password, ""); // Pass empty string for profilePicture
        }
    }

    private void uploadProfilePictureAndRegister(String firstName, String lastName, String email, String password) {
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(selectedImageUri) // The image URI to upload
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned upload preset
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Upload started - nothing to do here
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Called while uploading - nothing to do here
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Upload succeeded - get the secure HTTPS image URL from Cloudinary
                        String profilePictureUrl = (String) resultData.get("secure_url");
                        createFirebaseAccount(firstName, lastName, email, password, profilePictureUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        // Upload failed - show exact error and register without picture
                        Toast.makeText(RegisterActivity.this, "Error: " + error.getDescription(), Toast.LENGTH_LONG).show();
                        createFirebaseAccount(firstName, lastName, email, password, "");
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Upload rescheduled - nothing to do here
                    }
                })
                .dispatch(); // Start the upload
    }

    private void createFirebaseAccount(String firstName, String lastName, String email, String password, String profilePictureUrl) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) { // Account created successfully
                        FirebaseUser user = mAuth.getCurrentUser();

                        user.sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    if (!emailTask.isSuccessful()) {
                                        Toast.makeText(this, "Could not send verification email", Toast.LENGTH_LONG).show();
                                    }
                                });

                        saveUserToFirestore(user.getUid(), firstName, lastName, email, profilePictureUrl);

                    } else { // Registration failed
                        Exception exception = task.getException();

                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            etEmail.setError("This email is already registered, please login instead");
                            etEmail.requestFocus();
                        } else if (exception != null && exception.getMessage() != null && exception.getMessage().contains("network")) {
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void saveUserToFirestore(String userId, String firstName, String lastName, String email, String profilePictureUrl) {
        Map<String, Object> user = new HashMap<>();
        user.put("firstName", firstName); // Save first name
        user.put("lastName", lastName); // Save last name
        user.put("email", email); // Save email
        user.put("role", UserRole.USER.name()); // Save role as USER by default
        user.put("profilePicture", profilePictureUrl); // Save profile picture URL - empty string if no picture
        user.put("createdAt", System.currentTimeMillis()); // Save current time in milliseconds

        db.collection("users")
                .document(userId) // Use Firebase Auth UID as document ID
                .set(user) // Save all the data
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account created! Please verify your email 📧", Toast.LENGTH_LONG).show();
                        finish(); // Close RegisterActivity and go back to LoginActivity
                    } else {
                        Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
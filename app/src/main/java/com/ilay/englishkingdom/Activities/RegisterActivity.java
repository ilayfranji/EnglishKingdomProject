package com.ilay.englishkingdom.Activities;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.Models.User;
import com.ilay.englishkingdom.Models.UserRole;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private EditText etFirstName; // First name input field
    private EditText etLastName; // Last name input field
    private EditText etEmail; // Email input field
    private EditText etPassword; // Password input field
    private EditText etConfirmPassword; // Confirm password input field
    private Button btnRegister; // Register button
    private TextView tvLogin; // "Already have an account? Login" link
    private ImageView imgProfilePicture; // The circular profile picture avatar

    // ==================== FIREBASE ====================

    private FirebaseAuth mAuth; // Our connection to Firebase Authentication
    private FirebaseFirestore db; // Our connection to Firestore database

    // ==================== IMAGE HANDLING ====================

    // ImagePickerHelper handles ALL camera/gallery/permission logic
    // Same helper we use in LearnActivity - we write the logic once and reuse it
    private ImagePickerHelper imagePicker;

    private Uri selectedImageUri = null; // The profile picture the user picked - null means no image

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These MUST live here in the Activity - Android does not allow creating
    // them inside helper classes. We pass them into ImagePickerHelper.

    // Gallery launcher - opens gallery, result comes back here, forwarded to ImagePickerHelper
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // Forward result to ImagePickerHelper

    // Camera launcher - opens camera, result comes back here, forwarded to ImagePickerHelper
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> imagePicker.onCameraResult(success)); // Forward result to ImagePickerHelper

    // ==================== LIFECYCLE ====================

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
        imgProfilePicture = findViewById(R.id.imgProfilePicture);

        // Create ImagePickerHelper - when a photo is picked, this callback runs
        // We update selectedImageUri and load the image into the avatar circle
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    // This runs after the user picks or takes a photo
                    selectedImageUri = uri; // Save URI so we can upload it during registration
                    // circleCrop() makes the image circular to match our round avatar design
                    Glide.with(this).load(uri).circleCrop().into(imgProfilePicture);
                },
                galleryLauncher,
                cameraLauncher);

        // Clicking the avatar opens different options based on whether a photo is already selected
        imgProfilePicture.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                // Photo already selected - show Change/Delete options
                showPhotoOptionsDialog();
            } else {
                // No photo yet - show camera/gallery picker directly
                imagePicker.show();
            }
        });

        btnRegister.setOnClickListener(v -> registerUser()); // Validate and register when tapped
        tvLogin.setOnClickListener(v -> finish()); // Close RegisterActivity and go back to Login
    }

    // ==================== PERMISSION RESULT ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward the permission result to ImagePickerHelper
        // ImagePickerHelper requested the permission so it knows what to do with the result
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== PHOTO OPTIONS ====================

    private void showPhotoOptionsDialog() {
        // Shows when user taps on an already selected profile photo
        // Change = open picker again, Delete = remove photo and reset to default avatar
        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(new String[]{"🔄 Change Photo", "🗑️ Delete Photo"}, (dialog, which) -> {
                    if (which == 0) { // Change Photo tapped
                        imagePicker.show(); // Open camera/gallery picker again
                    } else { // Delete Photo tapped
                        selectedImageUri = null; // Clear the selected image URI
                        // Reset avatar back to the default grey placeholder icon
                        imgProfilePicture.setImageResource(R.drawable.ic_default_avatar);
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close, keep current photo
                .show();
    }

    // ==================== REGISTER USER ====================

    private void registerUser() {
        // Read and trim all input fields
        // trim() removes any accidental spaces at the start or end
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false; // We use this flag to show ALL errors at once instead of one by one

        // ---- Validate First Name ----
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required");
            hasError = true;
        } else if (!firstName.matches("[a-zA-Z]+")) { // Only letters - no numbers or symbols
            etFirstName.setError("First name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(firstName.charAt(0))) {
            // charAt(0) gets the first character - isUpperCase checks if it is a capital letter
            etFirstName.setError("First name must start with a capital letter");
            hasError = true;
        }

        // ---- Validate Last Name ----
        if (lastName.isEmpty()) {
            etLastName.setError("Last name is required");
            hasError = true;
        } else if (!lastName.matches("[a-zA-Z]+")) {
            etLastName.setError("Last name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(lastName.charAt(0))) {
            etLastName.setError("Last name must start with a capital letter");
            hasError = true;
        }

        // ---- Validate Email ----
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            // Patterns.EMAIL_ADDRESS is a built-in Android pattern for validating email format
            etEmail.setError("Please enter a valid email");
            hasError = true;
        }

        // ---- Validate Password ----
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            hasError = true;
        } else if (password.length() < 6) {
            // Firebase requires passwords to be at least 6 characters
            etPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        // ---- Validate Confirm Password ----
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            // .equals() compares the actual text content not the object reference
            etConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) return; // Stop here - don't register if there are any errors

        // All validation passed - check if profile picture was selected
        if (selectedImageUri != null) {
            // Profile picture selected - upload it first then register
            uploadProfilePictureAndRegister(firstName, lastName, email, password);
        } else {
            // No profile picture - register directly with empty string
            createFirebaseAccount(firstName, lastName, email, password, "");
        }
    }

    // ==================== UPLOAD PROFILE PICTURE ====================

    private void uploadProfilePictureAndRegister(String firstName, String lastName,
                                                 String email, String password) {
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // Nothing to do when upload starts
                    @Override public void onProgress(String id, long bytes, long total) {} // Could add progress bar
                    @Override public void onReschedule(String id, ErrorInfo e) {} // Nothing to do if rescheduled

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload finished - get the HTTPS Cloudinary URL
                        String profilePictureUrl = (String) result.get("secure_url");
                        // Now create the Firebase account with the image URL
                        createFirebaseAccount(firstName, lastName, email, password, profilePictureUrl);
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - still register but without a profile picture
                        Toast.makeText(RegisterActivity.this, "Image upload failed, registering without photo.", Toast.LENGTH_LONG).show();
                        createFirebaseAccount(firstName, lastName, email, password, "");
                    }
                }).dispatch(); // dispatch() actually starts the upload
    }

    // ==================== CREATE FIREBASE ACCOUNT ====================

    private void createFirebaseAccount(String firstName, String lastName,
                                       String email, String password, String profilePictureUrl) {
        // Creates the Firebase Authentication account with email and password
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) { // Account created successfully
                        FirebaseUser user = mAuth.getCurrentUser(); // Get the newly created user

                        // Send a verification email - user must verify before they can log in
                        user.sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    if (!emailTask.isSuccessful()) {
                                        Toast.makeText(this, "Could not send verification email", Toast.LENGTH_LONG).show();
                                    }
                                });

                        saveUserToFirestore(user.getUid(), firstName, lastName, email, profilePictureUrl);

                    } else { // Account creation failed - check what went wrong
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // Email already registered - show error on the email field
                            etEmail.setError("This email is already registered, please login instead");
                            etEmail.requestFocus(); // Move cursor to email field so user sees the error
                        } else if (exception != null && exception.getMessage() != null
                                && exception.getMessage().contains("network")) {
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ==================== SAVE USER TO FIRESTORE ====================

    private void saveUserToFirestore(String userId, String firstName, String lastName,
                                     String email, String profilePictureUrl) {
        // Create a User object - cleaner than using a raw HashMap
        // The User class fields map directly to Firestore document fields
        User user = new User(
                userId,                     // idFS - same as Firebase Auth UID
                firstName,                  // firstName
                lastName,                   // lastName
                email,                      // email
                UserRole.USER.name(),       // role - always USER when self registering
                profilePictureUrl,          // profilePicture - Cloudinary URL or empty string
                System.currentTimeMillis()  // createdAt - current time in milliseconds
        );

        // Use the Firebase Auth UID as the document ID
        // This makes it easy to find user data later using just the UID
        db.collection("users").document(userId).set(user)
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
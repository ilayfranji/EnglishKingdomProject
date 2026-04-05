package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to navigate to HomeActivity when continuing as guest
import android.content.pm.PackageManager; // Used for permission checking
import android.net.Uri; // Used to store the selected profile picture URI
import android.os.Bundle; // Used when creating the activity
import android.util.Patterns; // Used to validate email format
import android.widget.Button; // Used for the register and guest buttons
import android.widget.EditText; // Used for the text input fields
import android.widget.ImageView; // Used for the profile picture
import android.widget.TextView; // Used for the login link
import android.widget.Toast; // Used to show short messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery and camera
import androidx.activity.result.contract.ActivityResultContracts; // Provides gallery and camera contracts
import androidx.appcompat.app.AlertDialog; // Used for the photo options and guest warning dialogs
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.bumptech.glide.Glide; // Used to load and show the selected profile picture
import com.cloudinary.android.MediaManager; // Used to upload the profile picture to Cloudinary
import com.cloudinary.android.callback.ErrorInfo; // Used to get the error details if upload fails
import com.cloudinary.android.callback.UploadCallback; // Used to listen for upload result
import com.google.firebase.auth.FirebaseAuth; // Used to create the Firebase account
import com.google.firebase.auth.FirebaseAuthUserCollisionException; // Used to detect duplicate email
import com.google.firebase.auth.FirebaseUser; // Used to get the newly created user
import com.google.firebase.firestore.FirebaseFirestore; // Used to save user data to Firestore
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery/permissions
import com.ilay.englishkingdom.Models.User; // Our User data model
import com.ilay.englishkingdom.Models.UserRole; // Used to set the user's role to USER
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.Map; // Used to read the Cloudinary upload result

public class RegisterActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private EditText etFirstName; // First name input field
    private EditText etLastName; // Last name input field
    private EditText etEmail; // Email input field
    private EditText etPassword; // Password input field
    private EditText etConfirmPassword; // Confirm password input field
    private Button btnRegister; // Register button
    private Button btnGuest; // Continue as guest button - same warning as login screen
    private TextView tvLogin; // "Already have an account? Login" link
    private ImageView imgProfilePicture; // Profile picture circle - tapping opens picker

    // ==================== FIREBASE ====================

    private FirebaseAuth mAuth; // Used to create the Firebase auth account
    private FirebaseFirestore db; // Used to save user data to Firestore

    // ==================== IMAGE HANDLING ====================

    private ImagePickerHelper imagePicker; // Handles all camera/gallery/permission logic
    private Uri selectedImageUri = null; // The URI of the picked image - null means no image yet

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These must live here in the Activity - Android doesn't allow them in helper classes
    // We pass them into ImagePickerHelper so it can launch gallery and camera

    // Gallery launcher - opens gallery and waits for user to pick an image
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // Forward result to ImagePickerHelper

    // Camera launcher - opens camera and waits for user to take a photo
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

        // Connect each variable to its XML view
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        btnGuest = findViewById(R.id.btnGuest);
        tvLogin = findViewById(R.id.tvLogin);
        imgProfilePicture = findViewById(R.id.imgProfilePicture);

        // Set up ImagePickerHelper - when a photo is picked this callback runs
        // We save the URI and load the image into the profile picture circle
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    selectedImageUri = uri; // Save so we can upload it during registration
                    Glide.with(this).load(uri).circleCrop().into(imgProfilePicture); // Show it
                },
                galleryLauncher,
                cameraLauncher);

        // Tapping the profile picture opens different options depending on
        // whether a photo has already been picked or not
        imgProfilePicture.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                showPhotoOptionsDialog(); // Photo already picked - show change/delete options
            } else {
                imagePicker.show(); // No photo yet - open camera/gallery picker directly
            }
        });

        btnRegister.setOnClickListener(v -> registerUser()); // Validate and register

        // Go back to LoginActivity when "Already have an account?" is tapped
        tvLogin.setOnClickListener(v -> finish());

        // Show the same guest warning dialog that appears on the login screen
        // If user confirms they want to continue as guest, go straight to HomeActivity
        btnGuest.setOnClickListener(v -> showGuestWarning());
    }

    // ==================== PERMISSION RESULT ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward the permission result to ImagePickerHelper
        // It requested the permission so it knows what to do with the result
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== GUEST WARNING ====================

    private void showGuestWarning() {
        // Shows the exact same warning dialog as the one on the login screen
        // This way the guest experience is consistent no matter which screen they're on
        new AlertDialog.Builder(this)
                .setTitle("Continue as Guest?")
                .setMessage("As a guest you can access the Learn section and Practice games, but your records, your daily streak, titles, your progress and your games history will not be saved.")
                .setPositiveButton("Continue as Guest", (dialog, which) -> {
                    // User accepted - go to HomeActivity as a guest
                    // No Firebase sign in needed - getCurrentUser() will be null which means guest
                    startActivity(new Intent(this, HomeActivity.class));
                    finish(); // Close RegisterActivity so user can't go back here
                })
                .setNegativeButton("Cancel", null) // User changed their mind - just close the dialog
                .show();
    }

    // ==================== PHOTO OPTIONS ====================

    private void showPhotoOptionsDialog() {
        // Shows when user taps on a profile picture that's already been picked
        // Change = open picker again, Delete = remove the photo and show default avatar
        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(new String[]{"Change Photo", "Delete Photo"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open camera/gallery picker again
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear the saved URI
                        imgProfilePicture.setImageResource(R.drawable.ic_default_avatar); // Reset to default
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== REGISTER USER ====================

    private void registerUser() {
        // Read and trim all input fields
        // trim() removes accidental spaces at the start or end
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false; // We use this flag to show ALL errors at once instead of one by one

        // Validate first name
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required");
            hasError = true;
        } else if (!firstName.matches("[a-zA-Z]+")) {
            etFirstName.setError("First name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(firstName.charAt(0))) {
            etFirstName.setError("First name must start with a capital letter");
            hasError = true;
        }

        // Validate last name
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

        // Validate email
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Please enter a valid email");
            hasError = true;
        }

        // Validate password - Firebase requires at least 6 characters
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            hasError = true;
        } else if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            etConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) return; // Stop here - don't register if there are any errors

        // All validation passed - check if a profile picture was picked
        if (selectedImageUri != null) {
            uploadProfilePictureAndRegister(firstName, lastName, email, password); // Upload then register
        } else {
            createFirebaseAccount(firstName, lastName, email, password, ""); // Register without photo
        }
    }

    // ==================== UPLOAD PROFILE PICTURE ====================

    private void uploadProfilePictureAndRegister(String firstName, String lastName,
                                                 String email, String password) {
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // Our unsigned Cloudinary preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload finished - get the secure HTTPS URL from Cloudinary
                        String profilePictureUrl = (String) result.get("secure_url");
                        createFirebaseAccount(firstName, lastName, email, password, profilePictureUrl);
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - still register but without a profile picture
                        Toast.makeText(RegisterActivity.this,
                                "Image upload failed: " + e.getDescription(), Toast.LENGTH_LONG).show();
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
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser(); // Get the newly created user

                        // Send a verification email - user must verify before logging in
                        user.sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    if (!emailTask.isSuccessful()) {
                                        Toast.makeText(this, "Could not send verification email", Toast.LENGTH_LONG).show();
                                    }
                                });

                        saveUserToFirestore(user.getUid(), firstName, lastName, email, profilePictureUrl);

                    } else {
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // Email already registered - show error on the email field
                            etEmail.setError("This email is already registered, please login instead");
                            etEmail.requestFocus();
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
        User user = new User(
                userId,                    // idFS - same as Firebase Auth UID
                firstName,
                lastName,
                email,
                UserRole.USER.name(),      // Role is always USER when self-registering
                profilePictureUrl,         // Cloudinary URL or empty string if no photo
                System.currentTimeMillis() // createdAt - current time in milliseconds
        );

        // Use the Firebase Auth UID as the Firestore document ID
        // This makes it easy to find user data later using just the UID
        db.collection("users").document(userId).set(user)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account created! Please verify your email", Toast.LENGTH_LONG).show();
                        finish(); // Close RegisterActivity and go back to LoginActivity
                    } else {
                        Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
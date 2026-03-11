package com.ilay.englishkingdom.Activities;

import android.Manifest;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.User;
import com.ilay.englishkingdom.Models.UserRole;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etFirstName;
    private EditText etLastName;
    private EditText etEmail;
    private EditText etPassword;
    private EditText etConfirmPassword;
    private Button btnRegister;
    private TextView tvLogin;
    private ImageView imgProfilePicture; // The circular profile picture

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private Uri selectedImageUri = null; // Stores the URI of selected profile picture - null means no image

    // Gallery launcher - opens gallery and waits for user to pick an image
    private ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) { // If user picked an image
                    selectedImageUri = uri; // Save the URI
                    // circleCrop() makes the image circular to match our avatar design
                    Glide.with(this).load(uri).circleCrop().into(imgProfilePicture);
                }
            });

    // Camera launcher - opens camera and waits for user to take a photo
    private ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> {
                if (success && selectedImageUri != null) { // If photo taken successfully
                    Glide.with(this).load(selectedImageUri).circleCrop().into(imgProfilePicture);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        imgProfilePicture = findViewById(R.id.imgProfilePicture);

        // Clicking the avatar opens different options based on whether a photo is already selected
        imgProfilePicture.setOnClickListener(v -> {
            if (selectedImageUri != null) { // Photo already selected - show Change/Delete options
                showPhotoOptionsDialog();
            } else { // No photo yet - show camera/gallery picker
                showImagePickerDialog();
            }
        });

        btnRegister.setOnClickListener(v -> registerUser());
        tvLogin.setOnClickListener(v -> finish());
    }

    private void showImagePickerDialog() { // Shows popup to choose camera or gallery
        new AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"📷 Camera", "🖼️ Gallery"}, (dialog, which) -> {
                    if (which == 0) { // Camera selected
                        // Check camera permission before launching
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                                == PackageManager.PERMISSION_GRANTED) {
                            selectedImageUri = createTempImageUri(); // Create temp file
                            cameraLauncher.launch(selectedImageUri); // Launch camera
                        } else {
                            // Permission not granted - request it
                            // 100 is our request code to identify this permission request
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.CAMERA}, 100);
                        }
                    } else { // Gallery selected
                        galleryLauncher.launch("image/*"); // "image/*" means any image type
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPhotoOptionsDialog() {
        // Shows a popup when user clicks on an already selected photo
        // Options: Change Photo or Delete Photo
        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(new String[]{"🔄 Change Photo", "🗑️ Delete Photo"}, (dialog, which) -> {
                    if (which == 0) { // Change Photo selected
                        showImagePickerDialog(); // Open camera/gallery picker again
                    } else { // Delete Photo selected
                        selectedImageUri = null; // Clear the selected image URI
                        // Reset the avatar back to the default placeholder
                        imgProfilePicture.setImageResource(R.drawable.ic_default_avatar);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Runs automatically after user responds to permission popup
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) { // Our camera permission request code
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted - launch camera
                selectedImageUri = createTempImageUri();
                cameraLauncher.launch(selectedImageUri);
            } else {
                Toast.makeText(this, "Camera permission is required to take a photo", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private Uri createTempImageUri() { // Creates temporary file URI for camera photo
        java.io.File photoFile = new java.io.File(getCacheDir(),
                "temp_photo_" + System.currentTimeMillis() + ".jpg");
        return androidx.core.content.FileProvider.getUriForFile(this,
                getPackageName() + ".provider", photoFile);
    }

    private void registerUser() {
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false;

        // Validate First Name
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required");
            hasError = true;
        } else if (!firstName.matches("[a-zA-Z]+")) {
            etFirstName.setError("First name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(firstName.charAt(0))) {
            // charAt(0) gets the first character - isUpperCase checks if it is capital
            etFirstName.setError("First name must start with a capital letter");
            hasError = true;
        }

        // Validate Last Name
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

        if (hasError) return;

        if (selectedImageUri != null) { // Profile picture selected - upload first then register
            uploadProfilePictureAndRegister(firstName, lastName, email, password);
        } else { // No profile picture - register without one
            createFirebaseAccount(firstName, lastName, email, password, "");
        }
    }

    private void uploadProfilePictureAndRegister(String firstName, String lastName, String email, String password) {
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {}

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {}

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Upload succeeded - get the HTTPS URL from Cloudinary
                        String profilePictureUrl = (String) resultData.get("secure_url");
                        createFirebaseAccount(firstName, lastName, email, password, profilePictureUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        // Upload failed - register without picture
                        Toast.makeText(RegisterActivity.this, "Error: " + error.getDescription(), Toast.LENGTH_LONG).show();
                        createFirebaseAccount(firstName, lastName, email, password, "");
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {}
                })
                .dispatch();
    }

    private void createFirebaseAccount(String firstName, String lastName, String email, String password, String profilePictureUrl) {
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();

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

    private void saveUserToFirestore(String userId, String firstName, String lastName, String email, String profilePictureUrl) {
        // Create a User object - cleaner than using HashMap
        User user = new User(
                userId, // idFS - Firebase Auth UID
                firstName, // first name
                lastName, // last name
                email, // email
                UserRole.USER.name(), // role - USER by default
                profilePictureUrl, // profile picture URL - empty string if no picture
                System.currentTimeMillis() // createdAt - current time in milliseconds
        );

        db.collection("users")
                .document(userId)
                .set(user) // Firestore automatically converts User object to document
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account created! Please verify your email 📧", Toast.LENGTH_LONG).show();
                        finish();
                    } else {
                        Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
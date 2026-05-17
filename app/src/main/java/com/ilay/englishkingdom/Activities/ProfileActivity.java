package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open GameHistoryActivity and RegisterActivity
import android.net.Uri; // Used to store the selected profile picture URI
import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used for the edit name dialog view
import android.widget.EditText; // Used for the edit name dialog fields
import android.widget.ImageView; // Used for the profile picture
import android.widget.ProgressBar; // Used for the overall progress bar
import android.widget.TextView; // Used for all text views
import android.widget.Toast; // Used to show short messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery and camera
import androidx.activity.result.contract.ActivityResultContracts; // Provides gallery and camera contracts
import androidx.appcompat.app.AlertDialog; // Used for the guest dialog and edit name dialog
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.bumptech.glide.Glide; // Used to load the profile picture from Cloudinary
import com.cloudinary.android.MediaManager; // Used to upload the profile picture
import com.cloudinary.android.callback.ErrorInfo; // Used to get upload error details
import com.cloudinary.android.callback.UploadCallback; // Used to listen for upload result
import com.google.firebase.auth.FirebaseAuth; // Used to get the current user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read and write user data
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single Firestore document
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery/permissions
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.text.SimpleDateFormat; // Used to format dates for streak calculation
import java.util.Calendar; // Used to calculate yesterday's date
import java.util.Date; // Used to get today's date
import java.util.List; // Used for the learnedWords list
import java.util.Locale; // Used for date formatting
import java.util.Map; // Used to read Cloudinary upload result

public class ProfileActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow
    private ImageView imgProfile; // Profile picture
    private TextView tvChangePhoto; // Camera icon to change photo
    private TextView tvName; // Full name
    private TextView tvEditName; // Edit name pencil button
    private TextView tvEmail; // Email - view only
    private TextView tvTitle2; // Title badge
    private TextView tvTotalWords; // Total words learned
    private TextView tvStreak; // Streak counter
    private ProgressBar progressOverall; // Overall progress bar
    private TextView tvProgressPercent; // Progress percentage
    private TextView tvTriviaBestScore; // Classic Trivia best score
    private TextView tvTriviaBestTime; // Classic Trivia best time
    private TextView tvSpeedTriviaBestScore; // Speed Trivia best score
    private TextView tvWordSearchBestTime; // Word Search best time
    private TextView tvWordMatchBestTime; // Word Match best time (won games only)
    private TextView tvWordMatchBestLives; // Word Match most lives remaining on a win
    private TextView btnViewHistory; // Button to open game history screen

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Our auth connection
    private String userId; // Current user's ID

    // ==================== IMAGE PICKER ====================

    private ImagePickerHelper imagePicker; // Handles camera/gallery/permissions

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (imagePicker != null) imagePicker.onGalleryResult(uri); });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> { if (imagePicker != null) imagePicker.onCameraResult(success); });

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        boolean isGuest = mAuth.getCurrentUser() == null;

        if (isGuest) {
            // Guest user - show dialog with Register and Exit options
            // We pass a flag to RegisterActivity so it knows it was opened from here
            // and hides the "Continue as Guest" button which doesn't make sense in this flow
            new AlertDialog.Builder(this)
                    .setTitle("Profile")
                    .setMessage("You are logged in as a guest.\n\nGuests cannot save progress, earn titles, track streaks, or store game stats.\n\nRegister a free account to access your Kingdom Card!")
                    .setPositiveButton("Register", (dialog, which) -> {
                        // Open RegisterActivity with a flag so it hides the guest button
                        // and knows to send the user to HomeActivity after email verification
                        Intent intent = new Intent(this, RegisterActivity.class);
                        intent.putExtra("fromProfileDialog", true); // This flag hides the guest button
                        startActivity(intent);
                        finish(); // Close profile so user doesn't return here after registering
                    })
                    .setNegativeButton("Exit", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
            return;
        }

        userId = mAuth.getCurrentUser().getUid();

        // Connect all variables to their XML views
        tvBack = findViewById(R.id.tvBack);
        imgProfile = findViewById(R.id.imgProfile);
        tvChangePhoto = findViewById(R.id.tvChangePhoto);
        tvName = findViewById(R.id.tvName);
        tvEditName = findViewById(R.id.tvEditName);
        tvEmail = findViewById(R.id.tvEmail);
        tvTitle2 = findViewById(R.id.tvTitle2);
        tvTotalWords = findViewById(R.id.tvTotalWords);
        tvStreak = findViewById(R.id.tvStreak);
        progressOverall = findViewById(R.id.progressOverall);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvTriviaBestScore = findViewById(R.id.tvTriviaBestScore);
        tvTriviaBestTime = findViewById(R.id.tvTriviaBestTime);
        tvSpeedTriviaBestScore = findViewById(R.id.tvSpeedTriviaBestScore);
        tvWordSearchBestTime = findViewById(R.id.tvWordSearchBestTime);
        tvWordMatchBestTime = findViewById(R.id.tvWordMatchBestTime);
        tvWordMatchBestLives = findViewById(R.id.tvWordMatchBestLives);
        btnViewHistory = findViewById(R.id.btnViewHistory);

        // Set up ImagePickerHelper
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> uploadProfilePicture(uri),
                galleryLauncher,
                cameraLauncher);

        // Load all data
        loadUserData();
        loadTotalWordsLearned();
        updateStreak();
        loadGameStats();

        // Click listeners
        tvBack.setOnClickListener(v -> finish());
        tvChangePhoto.setOnClickListener(v -> imagePicker.show());
        imgProfile.setOnClickListener(v -> imagePicker.show());
        tvEditName.setOnClickListener(v -> showEditNameDialog());
        btnViewHistory.setOnClickListener(v ->
                startActivity(new Intent(this, GameHistoryActivity.class)));
    }

    // ==================== PERMISSION RESULT ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (imagePicker != null) imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== LOAD USER DATA ====================

    private void loadUserData() {
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");
                    String email = document.getString("email");
                    String profilePicture = document.getString("profilePicture");

                    if (firstName != null && lastName != null) {
                        tvName.setText(firstName + " " + lastName);
                    }
                    if (email != null) tvEmail.setText(email);

                    if (profilePicture != null && !profilePicture.isEmpty()) {
                        Glide.with(this).load(profilePicture).circleCrop().into(imgProfile);
                    } else {
                        imgProfile.setImageResource(R.drawable.ic_default_avatar);
                    }
                });
    }

    // ==================== LOAD TOTAL WORDS LEARNED ====================

    private void loadTotalWordsLearned() {
        // Real time listener - fires every time any progress document changes
        db.collection("users").document(userId)
                .collection("progress")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) return;

                    int totalLearned = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        if (doc.get("learnedWords") != null) {
                            List<Object> learnedWords = (List<Object>) doc.get("learnedWords");
                            totalLearned += learnedWords.size();
                        }
                    }

                    final int finalTotalLearned = totalLearned;
                    tvTotalWords.setText(String.valueOf(finalTotalLearned));
                    updateTitle(finalTotalLearned);

                    db.collection("categories").get()
                            .addOnSuccessListener(categories -> {
                                int totalAvailable = 0;
                                for (QueryDocumentSnapshot categoryDoc : categories) {
                                    if (categoryDoc.getLong("wordCount") != null) {
                                        totalAvailable += categoryDoc.getLong("wordCount").intValue();
                                    }
                                }

                                if (totalAvailable > 0) {
                                    int percent = (finalTotalLearned * 100) / totalAvailable;
                                    progressOverall.setMax(100);
                                    progressOverall.setProgress(percent);
                                    tvProgressPercent.setText(percent + "%");
                                } else {
                                    progressOverall.setProgress(0);
                                    tvProgressPercent.setText("0%");
                                }
                            });
                });
    }

    // ==================== UPDATE TITLE ====================

    private void updateTitle(int totalWords) {
        String title;
        if (totalWords <= 20) title = "Apprentice";
        else if (totalWords <= 50) title = "Knight";
        else if (totalWords <= 100) title = "Warrior";
        else if (totalWords <= 200) title = "Master";
        else title = "Legend";
        tvTitle2.setText(title);
    }

    // ==================== UPDATE STREAK ====================

    private void updateStreak() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date());

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    String lastOpenDate = document.getString("lastOpenDate");
                    long currentStreak = document.getLong("currentStreak") != null
                            ? document.getLong("currentStreak") : 0;

                    if (lastOpenDate == null) {
                        saveStreak(today, 1);
                        tvStreak.setText("1 day");
                        return;
                    }

                    if (lastOpenDate.equals(today)) {
                        tvStreak.setText(currentStreak + " days");
                        return;
                    }

                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_YEAR, -1);
                    String yesterday = sdf.format(calendar.getTime());

                    if (lastOpenDate.equals(yesterday)) {
                        long newStreak = currentStreak + 1;
                        saveStreak(today, newStreak);
                        tvStreak.setText(newStreak + " days");
                    } else {
                        saveStreak(today, 1);
                        tvStreak.setText("1 day");
                    }
                });
    }

    private void saveStreak(String date, long streak) {
        db.collection("users").document(userId)
                .update("lastOpenDate", date, "currentStreak", streak);
    }

    // ==================== EDIT NAME DIALOG ====================

    private void showEditNameDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etLastName = dialogView.findViewById(R.id.etLastName);

        String[] nameParts = tvName.getText().toString().split(" ");
        if (nameParts.length >= 1) etFirstName.setText(nameParts[0]);
        if (nameParts.length >= 2) etLastName.setText(nameParts[1]);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Name")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            boolean hasError = false;

            if (firstName.isEmpty()) { etFirstName.setError("Required"); hasError = true; }
            else if (!firstName.matches("[a-zA-Z]+")) { etFirstName.setError("Letters only"); hasError = true; }
            else if (!Character.isUpperCase(firstName.charAt(0))) { etFirstName.setError("Must start with capital"); hasError = true; }

            if (lastName.isEmpty()) { etLastName.setError("Required"); hasError = true; }
            else if (!lastName.matches("[a-zA-Z]+")) { etLastName.setError("Letters only"); hasError = true; }
            else if (!Character.isUpperCase(lastName.charAt(0))) { etLastName.setError("Must start with capital"); hasError = true; }

            if (hasError) return;

            db.collection("users").document(userId)
                    .update("firstName", firstName, "lastName", lastName)
                    .addOnSuccessListener(aVoid -> {
                        tvName.setText(firstName + " " + lastName);
                        Toast.makeText(this, "Name updated!", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Error updating name", Toast.LENGTH_SHORT).show());
        });
    }

    // ==================== UPLOAD PROFILE PICTURE ====================

    private void uploadProfilePicture(Uri uri) {
        Toast.makeText(this, "Uploading photo...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(uri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        String url = (String) result.get("secure_url");
                        db.collection("users").document(userId)
                                .update("profilePicture", url)
                                .addOnSuccessListener(aVoid -> {
                                    Glide.with(ProfileActivity.this)
                                            .load(url).circleCrop().into(imgProfile);
                                    Toast.makeText(ProfileActivity.this,
                                            "Photo updated!", Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        Toast.makeText(ProfileActivity.this,
                                "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch();
    }

    // ==================== LOAD GAME STATS ====================

    private void loadGameStats() {
        // Real time listener - updates immediately when user plays a game and comes back
        db.collection("users").document(userId)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) return;

                    // Classic Trivia
                    tvTriviaBestScore.setText(document.getLong("triviaBestScore") != null
                            ? document.getLong("triviaBestScore") + "/10" : "-");
                    tvTriviaBestTime.setText(document.getString("triviaBestTimeFormatted") != null
                            ? document.getString("triviaBestTimeFormatted") : "-");

                    // Speed Trivia
                    tvSpeedTriviaBestScore.setText(document.getLong("speedTriviaBestScore") != null
                            ? String.valueOf(document.getLong("speedTriviaBestScore")) : "-");

                    // Word Search
                    tvWordSearchBestTime.setText(document.getString("wordSearchBestTimeFormatted") != null
                            ? document.getString("wordSearchBestTimeFormatted") : "-");

                    // Word Match - best time on a winning game and most lives remaining
                    tvWordMatchBestTime.setText(document.getString("wordMatchBestTime") != null
                            ? document.getString("wordMatchBestTime") : "-");
                    tvWordMatchBestLives.setText(document.getLong("wordMatchBestLives") != null
                            ? document.getLong("wordMatchBestLives") + "/3" : "-");
                });
    }
}
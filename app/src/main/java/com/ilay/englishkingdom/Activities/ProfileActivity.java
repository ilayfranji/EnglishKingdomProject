package com.ilay.englishkingdom.Activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.R;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow
    private ImageView imgProfile; // Profile picture
    private TextView tvChangePhoto; // Camera icon to change photo
    private TextView tvName; // Full name
    private TextView tvEditName; // Edit name pencil button
    private TextView tvEmail; // Email - view only
    private TextView tvTitle2; // Title badge (Apprentice, Knight etc.)
    private TextView tvTotalWords; // Total words learned number
    private TextView tvStreak; // Streak counter
    private ProgressBar progressOverall; // Overall progress bar
    private TextView tvProgressPercent; // Progress percentage text
    private TextView tvTriviaBestScore; // Trivia best score
    private TextView tvTriviaBestTime; // Trivia best time
    private TextView tvWordSearchBestTime; // Word Search best time

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Our auth connection
    private String userId; // Current user's ID - saved so we don't fetch it every time

    // ==================== IMAGE PICKER ====================

    private ImagePickerHelper imagePicker; // Handles camera/gallery/permissions

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These MUST live here in the Activity - passed into ImagePickerHelper
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
            // Show a non-dismissible dialog telling the guest they can't access the profile
            // setCancelable(false) means tapping outside the dialog does nothing
            // The only option is to tap Exit which closes the profile screen
            new AlertDialog.Builder(this)
                    .setTitle("👑 Profile")
                    .setMessage("You are logged in as a guest.\n\nGuests cannot save progress, earn titles, track streaks, or store game stats.\n\nPlease register for a free account to access your Kingdom Card!")
                    .setPositiveButton("Exit", (dialog, which) -> finish()) // Close profile screen
                    .setCancelable(false) // Cannot be dismissed by tapping outside or pressing back
                    .show();
            return; // Don't load anything else
        }

        userId = mAuth.getCurrentUser().getUid(); // Save user ID for all Firestore calls

        // Connect each variable to its XML view
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
        tvWordSearchBestTime = findViewById(R.id.tvWordSearchBestTime);

        // Set up ImagePickerHelper - when photo is picked upload it and update profile
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> uploadProfilePicture(uri), // Upload when photo is picked
                galleryLauncher,
                cameraLauncher);

        // Load all user data
        loadUserData();
        loadTotalWordsLearned(); // Real time listener - updates automatically
        updateStreak();
        loadGameStats(); // Load trivia and word search best stats


        // Click listeners
        tvBack.setOnClickListener(v -> finish());
        tvChangePhoto.setOnClickListener(v -> imagePicker.show()); // Open camera/gallery picker
        imgProfile.setOnClickListener(v -> imagePicker.show()); // Tapping photo also opens picker
        tvEditName.setOnClickListener(v -> showEditNameDialog()); // Open edit name dialog
    }

    // ==================== PERMISSION RESULT ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (imagePicker != null) imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== LOAD USER DATA ====================

    private void loadUserData() {
        // Fetch the user's profile data from Firestore once - name and email don't change often
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    String firstName = document.getString("firstName");
                    String lastName = document.getString("lastName");
                    String email = document.getString("email");
                    String profilePicture = document.getString("profilePicture");

                    // Set name
                    if (firstName != null && lastName != null) {
                        tvName.setText(firstName + " " + lastName);
                    }

                    // Set email
                    if (email != null) tvEmail.setText(email);

                    // Load profile picture - circleCrop() makes it circular
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

                    // Count total learned words across all categories
                    int totalLearned = 0;
                    for (QueryDocumentSnapshot doc : snapshots) {
                        if (doc.get("learnedWords") != null) {
                            List<Object> learnedWords = (List<Object>) doc.get("learnedWords");
                            totalLearned += learnedWords.size();
                        }
                    }

                    // Save in final variable so we can use it inside the lambda below
                    // Java requires variables used inside lambdas to be final
                    final int finalTotalLearned = totalLearned;

                    // Update total words text and title immediately
                    tvTotalWords.setText(String.valueOf(finalTotalLearned));
                    updateTitle(finalTotalLearned);

                    // Fetch real total word count directly from categories collection
                    // wordCount in each category is always accurate because we update it
                    // every time a word is added or deleted
                    db.collection("categories").get()
                            .addOnSuccessListener(categories -> {
                                int totalAvailable = 0;

                                for (QueryDocumentSnapshot categoryDoc : categories) {
                                    if (categoryDoc.getLong("wordCount") != null) {
                                        totalAvailable += categoryDoc.getLong("wordCount").intValue();
                                    }
                                }


                                // Calculate percentage
                                // formula: (learned / total) * 100
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
        // Set the title badge based on total words learned
        // Each range gives a different title and emoji
        String title;
        if (totalWords <= 20) {
            title = "🌱 Apprentice"; // Just starting out - 0 to 20 words
        } else if (totalWords <= 50) {
            title = "⚔️ Knight"; // Getting somewhere - 21 to 50 words
        } else if (totalWords <= 100) {
            title = "🛡️ Warrior"; // Halfway there - 51 to 100 words
        } else if (totalWords <= 200) {
            title = "👑 Master"; // Almost at the top - 101 to 200 words
        } else {
            title = "🔥 Legend"; // Mastered it all - 200+ words
        }
        tvTitle2.setText(title);
    }

    // ==================== UPDATE STREAK ====================

    private void updateStreak() {
        // Streak = how many days in a row the user opened the app
        // We save lastOpenDate and currentStreak in the user's Firestore document
        // Every time this screen opens we check if the user already opened today

        // Get today's date as a string e.g. "2026-03-16"
        // SimpleDateFormat formats a Date object into a readable string
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String today = sdf.format(new Date()); // Today's date

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) return;

                    // Read saved streak data from Firestore
                    String lastOpenDate = document.getString("lastOpenDate");
                    long currentStreak = document.getLong("currentStreak") != null
                            ? document.getLong("currentStreak") : 0;

                    if (lastOpenDate == null) {
                        // First time ever opening - start streak at 1
                        saveStreak(today, 1);
                        tvStreak.setText("1 day");
                        return;
                    }

                    if (lastOpenDate.equals(today)) {
                        // Already opened today - just show current streak, don't change it
                        tvStreak.setText(currentStreak + " days");
                        return;
                    }

                    // Check if last open was yesterday
                    // Calendar lets us do date math - subtracting 1 day gives us yesterday
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.DAY_OF_YEAR, -1); // Go back 1 day from today
                    String yesterday = sdf.format(calendar.getTime()); // Format as string

                    if (lastOpenDate.equals(yesterday)) {
                        // User opened yesterday - streak continues! Add 1 day
                        long newStreak = currentStreak + 1;
                        saveStreak(today, newStreak);
                        tvStreak.setText(newStreak + " days");
                    } else {
                        // User missed at least one day - streak resets back to 1
                        saveStreak(today, 1);
                        tvStreak.setText("1 day");
                    }
                });
    }

    private void saveStreak(String date, long streak) {
        // Save the updated streak and last open date to Firestore
        // update() only changes these 2 fields - firstName, email etc. stay the same
        db.collection("users").document(userId)
                .update("lastOpenDate", date, "currentStreak", streak);
    }

    // ==================== EDIT NAME DIALOG ====================

    private void showEditNameDialog() {
        // Shows a popup with two text fields to change first and last name
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        EditText etFirstName = dialogView.findViewById(R.id.etFirstName);
        EditText etLastName = dialogView.findViewById(R.id.etLastName);

        // Pre-fill with current name by splitting "John Smith" into ["John", "Smith"]
        String[] nameParts = tvName.getText().toString().split(" ");
        if (nameParts.length >= 1) etFirstName.setText(nameParts[0]); // First word = first name
        if (nameParts.length >= 2) etLastName.setText(nameParts[1]); // Second word = last name

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Name")
                .setView(dialogView)
                .setPositiveButton("Save", null) // null = handle click manually
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String firstName = etFirstName.getText().toString().trim();
            String lastName = etLastName.getText().toString().trim();
            boolean hasError = false;

            // Validate first name - letters only, must start with capital
            if (firstName.isEmpty()) { etFirstName.setError("Required"); hasError = true; }
            else if (!firstName.matches("[a-zA-Z]+")) { etFirstName.setError("Letters only"); hasError = true; }
            else if (!Character.isUpperCase(firstName.charAt(0))) { etFirstName.setError("Must start with capital"); hasError = true; }

            // Validate last name - same rules
            if (lastName.isEmpty()) { etLastName.setError("Required"); hasError = true; }
            else if (!lastName.matches("[a-zA-Z]+")) { etLastName.setError("Letters only"); hasError = true; }
            else if (!Character.isUpperCase(lastName.charAt(0))) { etLastName.setError("Must start with capital"); hasError = true; }

            if (hasError) return; // Stop - keep dialog open so user can fix errors

            // Save updated name to Firestore
            db.collection("users").document(userId)
                    .update("firstName", firstName, "lastName", lastName)
                    .addOnSuccessListener(aVoid -> {
                        tvName.setText(firstName + " " + lastName); // Update name on screen
                        Toast.makeText(this, "Name updated! ✅", Toast.LENGTH_SHORT).show();
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
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload done - save new URL to Firestore and show on screen
                        String url = (String) result.get("secure_url");
                        db.collection("users").document(userId)
                                .update("profilePicture", url)
                                .addOnSuccessListener(aVoid -> {
                                    // Load new photo into the ImageView as a circle
                                    Glide.with(ProfileActivity.this)
                                            .load(url).circleCrop().into(imgProfile);
                                    Toast.makeText(ProfileActivity.this,
                                            "Photo updated! ✅", Toast.LENGTH_SHORT).show();
                                });
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        Toast.makeText(ProfileActivity.this,
                                "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch();
    }

    private void loadGameStats() {
        // Load game stats from Firestore in real time
        // So if user plays a game and comes back to profile, stats update automatically
        db.collection("users").document(userId)
                .addSnapshotListener((document, error) -> {
                    if (error != null || document == null || !document.exists()) return;

                    // Read trivia best score - show "-" if not played yet
                    if (document.getLong("triviaBestScore") != null) {
                        tvTriviaBestScore.setText(document.getLong("triviaBestScore") + "/10");
                    } else {
                        tvTriviaBestScore.setText("-"); // Never played trivia yet
                    }

                    // Read trivia best time - show "-" if not played yet
                    if (document.getString("triviaBestTimeFormatted") != null) {
                        tvTriviaBestTime.setText(document.getString("triviaBestTimeFormatted"));
                    } else {
                        tvTriviaBestTime.setText("-"); // Never played trivia yet
                    }

                    // Read word search best time - show "-" if not played yet
                    if (document.getString("wordSearchBestTimeFormatted") != null) {
                        tvWordSearchBestTime.setText(document.getString("wordSearchBestTimeFormatted"));
                    } else {
                        tvWordSearchBestTime.setText("-"); // Never played word search yet
                    }
                });
    }
}
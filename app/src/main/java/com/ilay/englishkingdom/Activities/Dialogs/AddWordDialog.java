package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class AddWordDialog {
    // This class has ONE job: show the Add Word dialog and save the new word
    // It handles: showing the form, validating input, checking duplicates, uploading image, saving to Firestore
    // WordsActivity just calls addWordDialog.show() - one line

    public interface OnWordAddedListener {
        void onWordAdded(); // Called after word is successfully saved to Firestore
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to inflate views and show toasts
    private final ImagePickerHelper imagePicker; // Handles camera/gallery
    private final OnWordAddedListener listener; // Who to notify when word is saved
    private final FirebaseFirestore db; // Our database connection
    private final String categoryId; // The category this word belongs to

    private Uri selectedImageUri = null; // The image admin picked - null means no image yet
    private ImageView imgPreview; // Reference to preview ImageView inside dialog
    private Button btnAddImage; // Reference to Add Image button inside dialog
    private AlertDialog openDialog; // Reference to dialog so we can dismiss it after saving

    // ==================== CONSTRUCTOR ====================

    public AddWordDialog(Activity activity, ImagePickerHelper imagePicker,
                         String categoryId, OnWordAddedListener listener) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.categoryId = categoryId; // Save categoryId so we know where to save the word
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== SHOW ====================

    public void show() {
        selectedImageUri = null; // Reset image from any previous time dialog was opened

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_word, null);
        EditText etWordEnglish = view.findViewById(R.id.etWordEnglish);
        EditText etWordHebrew = view.findViewById(R.id.etWordHebrew);
        EditText etExampleSentence = view.findViewById(R.id.etExampleSentence);
        imgPreview = view.findViewById(R.id.imgPreview);
        btnAddImage = view.findViewById(R.id.btnAddImage);

        btnAddImage.setOnClickListener(v -> imagePicker.show()); // Open picker when Add Image tapped

        // null for positive button = handle click manually so dialog stays open on errors
        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Add New Word")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        // Override Add button click AFTER show() so we control when the dialog dismisses
        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String wordEnglish = etWordEnglish.getText().toString().trim();
            String wordHebrew = etWordHebrew.getText().toString().trim();
            String exampleSentence = etExampleSentence.getText().toString().trim();
            boolean hasError = false; // Show ALL errors at once

            // Validate English word
            if (wordEnglish.isEmpty()) { etWordEnglish.setError("Required"); hasError = true; }
            else if (!wordEnglish.matches("[a-zA-Z ]+")) { etWordEnglish.setError("English only"); hasError = true; }
            else if (!Character.isUpperCase(wordEnglish.charAt(0))) { etWordEnglish.setError("Must start with a capital letter"); hasError = true; }

            // Validate Hebrew word
            if (wordHebrew.isEmpty()) { etWordHebrew.setError("Required"); hasError = true; }
            else if (!wordHebrew.matches("[\\u0590-\\u05FF ]+")) { etWordHebrew.setError("Hebrew only"); hasError = true; }

            // Validate example sentence
            if (exampleSentence.isEmpty()) { etExampleSentence.setError("Required"); hasError = true; }
            else if (!exampleSentence.matches("[a-zA-Z0-9 .,!?']+")) { etExampleSentence.setError("English only"); hasError = true; }
            else if (!Character.isUpperCase(exampleSentence.charAt(0))) { etExampleSentence.setError("Must start with a capital letter"); hasError = true; }

            // Validate image
            if (selectedImageUri == null) {
                Toast.makeText(activity, "Please add an image", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return; // Stop here - keep dialog open so admin can fix errors

            // All validation passed - now check if this word already exists in this category
            // We query the words subcollection for any document where wordEnglish matches
            // We only check inside THIS category - same word can exist in different categories
            // This runs BEFORE uploading so we don't waste a Cloudinary upload on a duplicate
            db.collection("categories").document(categoryId)
                    .collection("words")
                    .whereEqualTo("wordEnglish", wordEnglish) // Search for matching English word
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            // snapshot.isEmpty() = false means we found at least one match
                            // A word with this name already exists in this category - show error
                            etWordEnglish.setError("This word already exists in this category");
                            Toast.makeText(activity, "Word already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            // No duplicate found - safe to upload image and save to Firestore
                            uploadAndSave(wordEnglish, wordHebrew, exampleSentence);
                        }
                    })
                    .addOnFailureListener(e ->
                            // Something went wrong with the Firestore query - show error
                            Toast.makeText(activity, "Error checking duplicates", Toast.LENGTH_SHORT).show());
        });
    }

    // ==================== IMAGE PICKED CALLBACK ====================

    public void onImagePicked(Uri uri) {
        // WordsActivity calls this after ImagePickerHelper returns a photo
        selectedImageUri = uri; // Save the URI so we can upload it when Add is tapped

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE); // Show the preview
            Glide.with(activity).load(uri).into(imgPreview); // Load picked image
            btnAddImage.setVisibility(View.GONE); // Hide Add Image button

            // Make preview clickable so admin can Change or Delete after selecting
            imgPreview.setOnClickListener(v -> showImageOptions(uri));
        }
    }

    // ==================== IMAGE OPTIONS ====================

    private void showImageOptions(Uri uri) {
        // Shows when admin taps on the image preview inside the dialog
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"Change", "Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open picker again
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear URI
                        imgPreview.setVisibility(View.GONE); // Hide preview
                        imgPreview.setOnClickListener(null); // Remove click listener
                        btnAddImage.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== UPLOAD AND SAVE ====================

    private void uploadAndSave(String wordEnglish, String wordHebrew, String exampleSentence) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        String url = (String) result.get("secure_url"); // Get Cloudinary image URL
                        Word word = new Word(null, wordEnglish, wordHebrew, url, exampleSentence);

                        // Save word under categories/[categoryId]/words/
                        db.collection("categories").document(categoryId)
                                .collection("words").add(word)
                                .addOnSuccessListener(r -> {

                                    // After saving the word, recount total words in this category
                                    // and update the wordCount field in the category document
                                    // This keeps the word count text and progress bar accurate
                                    db.collection("categories").document(categoryId)
                                            .collection("words").get()
                                            .addOnSuccessListener(snapshot -> {

                                                // snapshot.size() = total words after adding the new one
                                                int totalWords = snapshot.size();

                                                // Update wordCount in the category document
                                                db.collection("categories").document(categoryId)
                                                        .update("wordCount", totalWords)
                                                        .addOnSuccessListener(v -> {
                                                            Toast.makeText(activity, "Word added!", Toast.LENGTH_SHORT).show();
                                                            if (openDialog != null) openDialog.dismiss();
                                                            if (listener != null) listener.onWordAdded();
                                                        });
                                            });
                                })
                                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving word", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - keep dialog open so admin can retry
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch(); // dispatch() actually starts the upload
    }
}
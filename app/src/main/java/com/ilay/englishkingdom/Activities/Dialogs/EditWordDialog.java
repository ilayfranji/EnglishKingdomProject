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

public class EditWordDialog {
    // This class has ONE job: show the Edit Word dialog and update the existing word
    // Very similar to AddWordDialog but pre-fills existing data and updates instead of adding

    public interface OnWordEditedListener {
        void onWordEdited(); // Called after word is successfully updated in Firestore
    }

    private final Activity activity;
    private final ImagePickerHelper imagePicker;
    private final OnWordEditedListener listener;
    private final FirebaseFirestore db;
    private final String categoryId; // Needed to find the word document in Firestore

    private Uri selectedImageUri = null; // New image picked - null means keep existing image
    private ImageView imgPreview;
    private Button btnAddImage;
    private AlertDialog openDialog;
    private Word currentWord; // The word being edited - we need its ID and existing data

    public EditWordDialog(Activity activity, ImagePickerHelper imagePicker,
                          String categoryId, OnWordEditedListener listener) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word word) {
        this.currentWord = word; // Save reference - needed in updateFirestore()
        selectedImageUri = null; // null = keep existing image unless admin picks a new one

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_word, null);
        EditText etWordEnglish = view.findViewById(R.id.etWordEnglish);
        EditText etWordHebrew = view.findViewById(R.id.etWordHebrew);
        EditText etExampleSentence = view.findViewById(R.id.etExampleSentence);
        imgPreview = view.findViewById(R.id.imgPreview);
        btnAddImage = view.findViewById(R.id.btnAddImage);

        // Pre-fill with existing word data so admin can see current values
        etWordEnglish.setText(word.getWordEnglish());
        etWordHebrew.setText(word.getWordHebrew());
        etExampleSentence.setText(word.getExampleSentence());

        // Show existing image and hide Add Image button since image already exists
        imgPreview.setVisibility(View.VISIBLE);
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imgPreview.setAdjustViewBounds(true);
        Glide.with(activity).load(word.getImage()).into(imgPreview); // Load from Cloudinary URL
        btnAddImage.setVisibility(View.GONE);

        // Tapping existing image shows Change/Delete options
        imgPreview.setOnClickListener(v -> showImageOptions());

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Word")
                .setView(view)
                .setPositiveButton("Save", null) // null = handle click manually
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String wordEnglish = etWordEnglish.getText().toString().trim();
            String wordHebrew = etWordHebrew.getText().toString().trim();
            String exampleSentence = etExampleSentence.getText().toString().trim();
            boolean hasError = false;

            if (wordEnglish.isEmpty()) { etWordEnglish.setError("Required"); hasError = true; }
            else if (!wordEnglish.matches("[a-zA-Z ]+")) { etWordEnglish.setError("English only"); hasError = true; }

            if (wordHebrew.isEmpty()) { etWordHebrew.setError("Required"); hasError = true; }
            else if (!wordHebrew.matches("[\\u0590-\\u05FF ]+")) { etWordHebrew.setError("Hebrew only"); hasError = true; }

            if (exampleSentence.isEmpty()) { etExampleSentence.setError("Required"); hasError = true; }

            if (hasError) return;

            if (selectedImageUri != null) {
                // New image selected - upload it first then update Firestore
                uploadAndUpdate(wordEnglish, wordHebrew, exampleSentence);
            } else {
                // No new image - keep existing Cloudinary URL
                updateFirestore(wordEnglish, wordHebrew, exampleSentence, currentWord.getImage());
                openDialog.dismiss();
            }
        });
    }

    public void onImagePicked(Uri uri) {
        // WordsActivity calls this after ImagePickerHelper returns a photo
        selectedImageUri = uri;

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE);
            Glide.with(activity).load(uri).into(imgPreview);
            btnAddImage.setVisibility(View.GONE);
            imgPreview.setOnClickListener(v -> showImageOptions());
        }
    }

    private void showImageOptions() {
        // Shows when admin taps on the image in the edit dialog
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"🔄 Change", "🗑️ Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open picker to pick a new image
                    } else { // Delete tapped
                        selectedImageUri = null;
                        imgPreview.setVisibility(View.GONE);
                        imgPreview.setOnClickListener(null);
                        btnAddImage.setVisibility(View.VISIBLE);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void uploadAndUpdate(String wordEnglish, String wordHebrew, String exampleSentence) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        String url = (String) result.get("secure_url"); // New Cloudinary URL
                        updateFirestore(wordEnglish, wordHebrew, exampleSentence, url);
                        if (openDialog != null) openDialog.dismiss();
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch();
    }

    private void updateFirestore(String wordEnglish, String wordHebrew,
                                 String exampleSentence, String imageUrl) {
        // Update only the 4 fields - other fields stay the same
        // Path: categories/[categoryId]/words/[wordId]
        db.collection("categories").document(categoryId)
                .collection("words").document(currentWord.getIdFS())
                .update("wordEnglish", wordEnglish,
                        "wordHebrew", wordHebrew,
                        "exampleSentence", exampleSentence,
                        "image", imageUrl)
                .addOnSuccessListener(v -> {
                    Toast.makeText(activity, "Word updated! ✅", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onWordEdited();
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error updating word", Toast.LENGTH_SHORT).show());
    }
}
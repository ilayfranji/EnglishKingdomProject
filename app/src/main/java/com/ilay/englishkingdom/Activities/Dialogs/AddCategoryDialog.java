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
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class AddCategoryDialog {
    // This class has ONE job: show the Add Category dialog and save the new category
    // It handles: showing the form, validating input, uploading image, saving to Firestore
    // LearnActivity just calls addDialog.show() - one line instead of 100+

    // ==================== CALLBACK INTERFACE ====================

    public interface OnCategoryAddedListener {
        // Called after the category is successfully saved to Firestore
        // LearnActivity uses this to know when saving is done
        void onCategoryAdded();
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to inflate views and show toasts
    private final ImagePickerHelper imagePicker; // Handles camera/gallery - we just call imagePicker.show()
    private final OnCategoryAddedListener listener; // Who to notify when category is saved
    private final FirebaseFirestore db; // Our database connection

    private Uri selectedImageUri = null; // The image the admin picked - null means no image yet
    private ImageView imgPreview; // Reference to the preview ImageView inside the dialog
    private Button btnAddImage; // Reference to the Add Image button inside the dialog
    private AlertDialog openDialog; // Reference to the dialog itself so we can dismiss it after saving

    // ==================== CONSTRUCTOR ====================

    public AddCategoryDialog(Activity activity, ImagePickerHelper imagePicker,
                             OnCategoryAddedListener listener) {
        // Constructor saves all the things we need to do our job
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== SHOW ====================

    public void show() {
        selectedImageUri = null; // Reset image from any previous time this dialog was opened

        // Inflate = read the XML layout file and build the View objects from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etNameHebrew = view.findViewById(R.id.etCategoryNameHebrew);
        imgPreview = view.findViewById(R.id.imgPreview); // Save reference - needed in onImagePicked()
        btnAddImage = view.findViewById(R.id.btnAddImage); // Save reference - needed in onImagePicked()

        // When admin taps Add Image button, show the camera/gallery picker
        btnAddImage.setOnClickListener(v -> imagePicker.show());

        // Build the dialog with null for positive button so we can handle click manually
        // null = don't auto-dismiss when Add is tapped - we want to keep it open if there are errors
        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Add New Category")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        // Override Add button click AFTER show() so we control when the dialog dismisses
        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String nameHebrew = etNameHebrew.getText().toString().trim();
            boolean hasError = false; // We use this flag to show ALL errors at once

            // Validate English name
            if (name.isEmpty()) { etName.setError("Required"); hasError = true; }
            else if (!name.matches("[a-zA-Z ]+")) { etName.setError("English only"); hasError = true; }

            // Validate Hebrew name
            if (nameHebrew.isEmpty()) { etNameHebrew.setError("Required"); hasError = true; }
            else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { etNameHebrew.setError("Hebrew only"); hasError = true; }

            // Validate image was selected
            if (selectedImageUri == null) {
                Toast.makeText(activity, "Please add an image", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return; // Stop here - keep dialog open so admin can fix errors

            uploadAndSave(name, nameHebrew); // All valid - upload image then save to Firestore
        });
    }

    // ==================== IMAGE PICKED CALLBACK ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity calls this after ImagePickerHelper returns a photo
        // LearnActivity is the "bridge" between ImagePickerHelper and this dialog
        // because the launchers must live in the Activity
        selectedImageUri = uri; // Save the URI so we can upload it when Add is tapped

        // Only update the UI if the dialog is currently open and visible
        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE); // Show the image preview
            Glide.with(activity).load(uri).into(imgPreview); // Load the picked image into preview
            btnAddImage.setVisibility(View.GONE); // Hide the Add Image button - image already selected

            // Make the preview clickable so admin can Change or Delete the image
            // Without this, tapping the image after selecting does nothing
            imgPreview.setOnClickListener(v -> showSelectedImageOptions(uri));
        }
    }

    // ==================== SELECTED IMAGE OPTIONS ====================

    private void showSelectedImageOptions(Uri uri) {
        // Shows when admin taps on the image preview inside the Add dialog
        // This lets admin Change (pick different image) or Delete (remove image entirely)
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"🔄 Change", "🗑️ Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open camera/gallery picker again to pick a different image
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear the URI - no image selected anymore
                        imgPreview.setVisibility(View.GONE); // Hide the preview
                        imgPreview.setOnClickListener(null); // Remove click listener - nothing to click anymore
                        btnAddImage.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close, keep image as is
                .show();
    }

    // ==================== UPLOAD AND SAVE ====================

    private void uploadAndSave(String name, String nameHebrew) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // Nothing to do when upload starts
                    @Override public void onProgress(String id, long bytes, long total) {} // Could add progress bar here
                    @Override public void onReschedule(String id, ErrorInfo e) {} // Nothing to do if rescheduled

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload finished - get the HTTPS URL of the uploaded image from Cloudinary
                        String url = (String) result.get("secure_url");
                        // Create a new Category object - 0 words because it's brand new
                        Category category = new Category(null, name, nameHebrew, url, 0);
                        // Save to Firestore - add() auto-generates a document ID
                        db.collection("categories").add(category)
                                .addOnSuccessListener(r -> {
                                    Toast.makeText(activity, "Category added! 🎉", Toast.LENGTH_SHORT).show();
                                    if (openDialog != null) openDialog.dismiss(); // Close the dialog
                                    if (listener != null) listener.onCategoryAdded(); // Notify LearnActivity
                                })
                                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving category", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - show the error, keep dialog open so admin can retry
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch(); // dispatch() actually starts the upload
    }
}
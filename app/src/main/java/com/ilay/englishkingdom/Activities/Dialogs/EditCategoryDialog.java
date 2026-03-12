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

public class EditCategoryDialog {
    // This class has ONE job: show the Edit Category dialog and update the existing category
    // Very similar to AddCategoryDialog but pre-fills existing data and updates instead of adding
    // LearnActivity just calls editCategoryDialog.show(category) - one line instead of 100+

    // ==================== CALLBACK INTERFACE ====================

    public interface OnCategoryEditedListener {
        // Called after the category is successfully updated in Firestore
        void onCategoryEdited();
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to inflate views and show toasts
    private final ImagePickerHelper imagePicker; // Handles camera/gallery
    private final OnCategoryEditedListener listener; // Who to notify when category is updated
    private final FirebaseFirestore db; // Our database connection

    private Uri selectedImageUri = null; // New image picked by admin - null means keep existing image
    private ImageView imgPreview; // Reference to image preview inside the dialog
    private Button btnAddImage; // Reference to Add Image button inside the dialog
    private AlertDialog openDialog; // Reference to dialog so we can dismiss it after saving
    private Category currentCategory; // The category being edited - we need its ID and existing image URL

    // ==================== CONSTRUCTOR ====================

    public EditCategoryDialog(Activity activity, ImagePickerHelper imagePicker,
                              OnCategoryEditedListener listener) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== SHOW ====================

    public void show(Category category) {
        // Takes the category to edit as a parameter so we can pre-fill its current values
        this.currentCategory = category; // Save reference - needed in updateFirestore()
        selectedImageUri = null; // Reset - null means "keep existing image" unless admin picks a new one

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etNameHebrew = view.findViewById(R.id.etCategoryNameHebrew);
        imgPreview = view.findViewById(R.id.imgPreview);
        btnAddImage = view.findViewById(R.id.btnAddImage);

        // Pre-fill with existing values so admin can see what's currently saved
        etName.setText(category.getCategoryName());
        etNameHebrew.setText(category.getCategoryNameHebrew());

        // Show the existing category image since it already has one
        imgPreview.setVisibility(View.VISIBLE);
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); // Full image no cropping
        imgPreview.setAdjustViewBounds(true); // Keep image proportions
        Glide.with(activity).load(category.getImage()).into(imgPreview); // Load from Cloudinary URL
        btnAddImage.setVisibility(View.GONE); // Hide Add Image button - image already exists

        // Tapping the existing image shows Change / Delete options
        imgPreview.setOnClickListener(v -> showImageOptions());

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Category")
                .setView(view)
                .setPositiveButton("Save", null) // null = handle click manually
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String nameHebrew = etNameHebrew.getText().toString().trim();
            boolean hasError = false;

            if (name.isEmpty()) { etName.setError("Required"); hasError = true; }
            else if (!name.matches("[a-zA-Z ]+")) { etName.setError("English only"); hasError = true; }

            if (nameHebrew.isEmpty()) { etNameHebrew.setError("Required"); hasError = true; }
            else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { etNameHebrew.setError("Hebrew only"); hasError = true; }

            if (hasError) return;

            if (selectedImageUri != null) {
                // Admin picked a new image - upload it first then update Firestore with new URL
                uploadAndUpdate(name, nameHebrew);
            } else {
                // No new image - keep the existing Cloudinary URL from the category object
                updateFirestore(name, nameHebrew, currentCategory.getImage());
                openDialog.dismiss();
            }
        });
    }

    // ==================== IMAGE PICKED CALLBACK ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity calls this after ImagePickerHelper returns a photo
        selectedImageUri = uri; // Save - will be uploaded when Save is tapped

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE);
            Glide.with(activity).load(uri).into(imgPreview); // Show newly picked image
            btnAddImage.setVisibility(View.GONE);
        }
    }

    // ==================== IMAGE OPTIONS ====================

    private void showImageOptions() {
        // Shows when admin taps on the existing image in the edit dialog
        // Change = pick a new image, Delete = remove image entirely
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"🔄 Change", "🗑️ Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open camera/gallery picker
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear any new image selection
                        imgPreview.setVisibility(View.GONE); // Hide the image preview
                        btnAddImage.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close, keep image as is
                .show();
    }

    // ==================== UPLOAD AND UPDATE ====================

    private void uploadAndUpdate(String name, String nameHebrew) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload done - get the new Cloudinary URL and update Firestore
                        String url = (String) result.get("secure_url");
                        updateFirestore(name, nameHebrew, url);
                        if (openDialog != null) openDialog.dismiss(); // Close dialog
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch();
    }

    // ==================== FIRESTORE UPDATE ====================

    private void updateFirestore(String name, String nameHebrew, String imageUrl) {
        // update() only changes the fields we list - wordCount and other fields stay the same
        db.collection("categories").document(currentCategory.getIdFS())
                .update("categoryName", name, "categoryNameHebrew", nameHebrew, "image", imageUrl)
                .addOnSuccessListener(v -> {
                    Toast.makeText(activity, "Category updated! ✅", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onCategoryEdited(); // Notify LearnActivity
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error updating category", Toast.LENGTH_SHORT).show());
    }
}
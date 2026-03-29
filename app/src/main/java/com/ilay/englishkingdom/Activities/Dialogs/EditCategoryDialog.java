package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class EditCategoryDialog {
    // This class shows the Edit Category dialog and updates the existing category
    // Same as AddCategoryDialog but pre-fills existing data and updates instead of adding

    public interface OnCategoryEditedListener {
        void onCategoryEdited(); // Called after category is successfully updated in Firestore
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to inflate views and show toasts
    private final ImagePickerHelper imagePicker; // Handles camera/gallery
    private final OnCategoryEditedListener listener; // Who to notify when category is updated
    private final FirebaseFirestore db; // Our database connection

    private Uri selectedImageUri = null; // New image picked - null means keep existing image
    private ImageView imgPreview; // Reference to preview ImageView inside dialog
    private Button btnAddImage; // Reference to Add Image button inside dialog
    private AlertDialog openDialog; // Reference to dialog so we can dismiss it after saving
    private Category currentCategory; // The category being edited - we need its ID and existing data

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
        this.currentCategory = category; // Save reference - needed in updateFirestore()
        selectedImageUri = null; // null = keep existing image unless admin picks a new one

        // Inflate = read dialog_category.xml and build the View objects from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etNameHebrew = view.findViewById(R.id.etCategoryNameHebrew);
        imgPreview = view.findViewById(R.id.imgPreview);
        btnAddImage = view.findViewById(R.id.btnAddImage);
        Spinner spinnerType = view.findViewById(R.id.spinnerCategoryType); // The type dropdown

        // Set up the spinner with the 3 category types
        // ArrayAdapter connects our string array to the spinner
        // android.R.layout.simple_spinner_item is the built in Android spinner item layout
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"Words", "Letters", "Sentences"});
        // simple_spinner_dropdown_item is the built in layout for the dropdown list items
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(spinnerAdapter); // Connect the adapter to the spinner

        // Pre-select the current category type in the spinner
        // So admin can see what type this category currently is before making changes
        CategoryType currentType = category.getCategoryTypeEnum(); // Get current type as enum
        if (currentType == CategoryType.WORDS) spinnerType.setSelection(0); // Select "Words"
        else if (currentType == CategoryType.LETTERS) spinnerType.setSelection(1); // Select "Letters"
        else spinnerType.setSelection(2); // Select "Sentences"

        // Pre-fill fields with existing category data so admin can see current values
        etName.setText(category.getCategoryName()); // Show current English name
        etNameHebrew.setText(category.getCategoryNameHebrew()); // Show current Hebrew name

        // Show existing image since category already has one
        imgPreview.setVisibility(View.VISIBLE);
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); // Full image no cropping
        imgPreview.setAdjustViewBounds(true); // Keep image proportions
        Glide.with(activity).load(category.getImage()).into(imgPreview); // Load from Cloudinary URL
        btnAddImage.setVisibility(View.GONE); // Hide Add Image button - image already exists

        // Tapping existing image shows Change/Delete options
        imgPreview.setOnClickListener(v -> showImageOptions());

        // Open image picker when Add Image button is tapped
        btnAddImage.setOnClickListener(v -> imagePicker.show());

        // null for positive button = handle click manually so dialog stays open on errors
        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Category")
                .setView(view)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        // Override Save button click AFTER show() so we control when the dialog dismisses
        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String nameHebrew = etNameHebrew.getText().toString().trim();
            boolean hasError = false;

            // Read which type admin selected from the spinner
            // getSelectedItemPosition() returns 0 = Words, 1 = Letters, 2 = Sentences
            int selectedPosition = spinnerType.getSelectedItemPosition();
            CategoryType selectedType;
            if (selectedPosition == 0) selectedType = CategoryType.WORDS;
            else if (selectedPosition == 1) selectedType = CategoryType.LETTERS;
            else selectedType = CategoryType.SENTENCES;

            // Validate English name
            if (name.isEmpty()) { etName.setError("Required"); hasError = true; }
            else if (!name.matches("[a-zA-Z ]+")) { etName.setError("English only"); hasError = true; }

            // Validate Hebrew name
            if (nameHebrew.isEmpty()) { etNameHebrew.setError("Required"); hasError = true; }
            else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { etNameHebrew.setError("Hebrew only"); hasError = true; }

            if (hasError) return; // Stop here - keep dialog open so admin can fix errors

            // Check if admin is trying to change the category type
            // getCategoryTypeEnum() converts the String stored in Firestore back to a CategoryType enum
            // We compare it to selectedType to know if admin changed it
            if (selectedType != currentCategory.getCategoryTypeEnum()) {
                // Admin changed the type - check if category still has words inside
                // We can't allow the type to change if words exist because the existing
                // words were added with the old type's fields and won't work with the new type
                // For example a Letters word has letter name + Hebrew explanation
                // but a Words word has English + Hebrew + image + example sentence
                db.collection("categories").document(currentCategory.getIdFS())
                        .collection("words").get()
                        .addOnSuccessListener(snapshot -> {
                            if (!snapshot.isEmpty()) {
                                // Category has words - block the type change
                                // snapshot.size() tells us how many words are still inside
                                // We show this number so admin knows how many to delete
                                Toast.makeText(activity,
                                        "Please delete all " + snapshot.size() +
                                                " items first before changing the category type",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                // No words inside - safe to change the type and save
                                proceedWithSave(name, nameHebrew, selectedType);
                            }
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(activity, "Error checking words", Toast.LENGTH_SHORT).show());
            } else {
                // Type did not change - no need to check words, just save directly
                proceedWithSave(name, nameHebrew, selectedType);
            }
        });
    }

    // ==================== PROCEED WITH SAVE ====================

    private void proceedWithSave(String name, String nameHebrew, CategoryType selectedType) {
        // This runs when we are sure it is safe to save
        // Either the type didn't change, or it changed but there are no words inside
        if (selectedImageUri != null) {
            // Admin picked a new image - upload it first then update Firestore
            uploadAndUpdate(name, nameHebrew, selectedType);
        } else {
            // No new image - keep the existing Cloudinary URL
            updateFirestore(name, nameHebrew, currentCategory.getImage(), selectedType);
            if (openDialog != null) openDialog.dismiss(); // Close the dialog
        }
    }

    // ==================== IMAGE PICKED CALLBACK ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity calls this after ImagePickerHelper returns a photo
        selectedImageUri = uri; // Save - will be uploaded when Save is tapped

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE);
            Glide.with(activity).load(uri).into(imgPreview); // Show newly picked image
            btnAddImage.setVisibility(View.GONE); // Hide Add Image button
            // Make preview clickable so admin can Change or Delete after selecting
            imgPreview.setOnClickListener(v -> showImageOptions());
        }
    }

    // ==================== IMAGE OPTIONS ====================

    private void showImageOptions() {
        // Shows when admin taps on the image in the edit dialog
        // Change = pick a new image, Delete = remove image entirely
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"Change", "Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open camera/gallery picker
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear any new image selection
                        imgPreview.setVisibility(View.GONE); // Hide the preview
                        imgPreview.setOnClickListener(null); // Remove click listener
                        btnAddImage.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close, keep image as is
                .show();
    }

    // ==================== UPLOAD AND UPDATE ====================

    private void uploadAndUpdate(String name, String nameHebrew, CategoryType type) {
        // Upload new image to Cloudinary first then update Firestore with new URL
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // Nothing to do when upload starts
                    @Override public void onProgress(String id, long bytes, long total) {} // Could add progress bar
                    @Override public void onReschedule(String id, ErrorInfo e) {} // Nothing to do if rescheduled

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload done - get new Cloudinary URL and update Firestore
                        String url = (String) result.get("secure_url");
                        updateFirestore(name, nameHebrew, url, type);
                        if (openDialog != null) openDialog.dismiss(); // Close the dialog
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - keep dialog open so admin can retry
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch(); // dispatch() actually starts the upload
    }

    // ==================== UPDATE FIRESTORE ====================

    private void updateFirestore(String name, String nameHebrew, String imageUrl, CategoryType type) {
        // update() only changes the fields we list - wordCount and other fields stay the same
        // We also update categoryType in case admin changed it
        // type.name() converts the enum to a String e.g. CategoryType.WORDS → "WORDS"
        // We save it as a String because Firestore doesn't understand Java enums directly
        db.collection("categories").document(currentCategory.getIdFS())
                .update(
                        "categoryName", name,           // Update English name
                        "categoryNameHebrew", nameHebrew, // Update Hebrew name
                        "image", imageUrl,              // Update image URL
                        "categoryType", type.name()     // Update type as String
                )
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(activity, "Category updated!", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onCategoryEdited(); // Notify LearnActivity
                })
                .addOnFailureListener(e -> Toast.makeText(activity,
                        "Error updating category", Toast.LENGTH_SHORT).show());
    }
}
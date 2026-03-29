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

public class AddCategoryDialog {
    // This class shows the Add Category dialog and saves the new category
    // Admin picks the category type from a spinner (dropdown)
    // All types now require an image

    public interface OnCategoryAddedListener {
        void onCategoryAdded(); // Called after category is successfully saved to Firestore
    }

    // ==================== FIELDS ====================

    private final Activity activity; // Needed to inflate views and show toasts
    private final ImagePickerHelper imagePicker; // Handles camera/gallery
    private final OnCategoryAddedListener listener; // Who to notify when category is saved
    private final FirebaseFirestore db; // Our database connection

    private Uri selectedImageUri = null; // The image admin picked - null means no image yet
    private ImageView imgPreview; // Reference to preview ImageView inside dialog
    private Button btnAddImage; // Reference to Add Image button inside dialog
    private AlertDialog openDialog; // Reference to dialog so we can dismiss it after saving

    // ==================== CONSTRUCTOR ====================

    public AddCategoryDialog(Activity activity, ImagePickerHelper imagePicker,
                             OnCategoryAddedListener listener) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== SHOW ====================

    public void show() {
        selectedImageUri = null; // Reset image from any previous time dialog was opened

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

        // Open image picker when Add Image button is tapped
        btnAddImage.setOnClickListener(v -> imagePicker.show());

        // null for positive button = handle click manually so dialog stays open on errors
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
            boolean hasError = false; // Track if any validation failed - show ALL errors at once

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

            // Image is required for ALL category types - not just Words
            if (selectedImageUri == null) {
                Toast.makeText(activity, "Please add an image", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return; // Stop here - keep dialog open so admin can fix errors

            // Check if a category with this name already exists in Firestore
            // This runs BEFORE uploading so we don't waste a Cloudinary upload on a duplicate
            db.collection("categories")
                    .whereEqualTo("categoryName", name)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            // Found a duplicate - show error and stop
                            etName.setError("A category with this name already exists");
                            Toast.makeText(activity, "Category already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            // No duplicate found - upload image and save for ALL types
                            uploadAndSave(name, nameHebrew, selectedType);
                        }
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(activity, "Error checking duplicates", Toast.LENGTH_SHORT).show());
        });
    }

    // ==================== IMAGE PICKED CALLBACK ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity calls this after ImagePickerHelper returns a photo
        selectedImageUri = uri; // Save the URI so we can upload it when Add is tapped

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE); // Show the image preview
            Glide.with(activity).load(uri).into(imgPreview); // Load the picked image
            btnAddImage.setVisibility(View.GONE); // Hide Add Image button

            // Make preview clickable so admin can Change or Delete after selecting
            imgPreview.setOnClickListener(v -> showSelectedImageOptions(uri));
        }
    }

    // ==================== SELECTED IMAGE OPTIONS ====================

    private void showSelectedImageOptions(Uri uri) {
        // Shows when admin taps on the image preview inside the dialog
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"Change", "Delete"}, (dialog, which) -> {
                    if (which == 0) { // Change tapped
                        imagePicker.show(); // Open camera/gallery picker again
                    } else { // Delete tapped
                        selectedImageUri = null; // Clear the URI
                        imgPreview.setVisibility(View.GONE); // Hide the preview
                        imgPreview.setOnClickListener(null); // Remove click listener
                        btnAddImage.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== UPLOAD AND SAVE ====================

    private void uploadAndSave(String name, String nameHebrew, CategoryType type) {
        // Upload image to Cloudinary first then save category to Firestore
        // All types now go through this method since all types have images
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary unsigned preset
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // Nothing to do when upload starts
                    @Override public void onProgress(String id, long bytes, long total) {} // Could add progress bar
                    @Override public void onReschedule(String id, ErrorInfo e) {} // Nothing to do if rescheduled

                    @Override
                    public void onSuccess(String id, Map result) {
                        // Upload finished - get the HTTPS Cloudinary URL
                        String url = (String) result.get("secure_url");
                        saveToFirestore(name, nameHebrew, url, type); // Save with image URL
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // Upload failed - show error, keep dialog open so admin can retry
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch(); // dispatch() actually starts the upload
    }

    // ==================== SAVE TO FIRESTORE ====================

    private void saveToFirestore(String name, String nameHebrew, String imageUrl, CategoryType type) {
        // type.name() converts the enum to a String e.g. CategoryType.WORDS → "WORDS"
        // We save it as a String because Firestore doesn't understand Java enums directly
        Category category = new Category(null, name, nameHebrew, imageUrl, 0, type.name());
        db.collection("categories").add(category) // add() auto-generates a document ID
                .addOnSuccessListener(r -> {
                    Toast.makeText(activity, "Category added!", Toast.LENGTH_SHORT).show();
                    if (openDialog != null) openDialog.dismiss(); // Close the dialog
                    if (listener != null) listener.onCategoryAdded(); // Notify LearnActivity
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving category", Toast.LENGTH_SHORT).show());
    }
}
package com.ilay.englishkingdom.Activities;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri; // Uri represents a file path or URL - used for images
import android.os.Bundle;
import android.view.View;
import android.widget.Button; // Used for the Add Image button
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher; // Modern way to handle activity results (camera/gallery)
import androidx.activity.result.contract.ActivityResultContracts; // Contains contracts for camera and gallery
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Adapters.CategoryAdapter;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;

import com.cloudinary.android.MediaManager; // Cloudinary's main class for uploading
import com.cloudinary.android.callback.ErrorInfo; // Represents an error from Cloudinary
import com.cloudinary.android.callback.UploadCallback; // Callback for when upload finishes

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    // UI elements
    private RecyclerView recyclerCategories;
    private FloatingActionButton fabCategory;
    private FloatingActionButton fabExitEditMode;
    private TextView tvBack;
    private TextView tvEditMode;
    private TextView tvEditBanner;

    // Firebase
    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    // Adapter and list
    private CategoryAdapter categoryAdapter;
    private List<Category> categoryList;

    // State flags
    private boolean isEditMode = false; // Tracks if admin is in edit mode
    private boolean isAdmin = false; // Tracks if current user is admin

    // Image handling
    private Uri selectedImageUri = null; // Stores the URI of the image picked by admin
    private ImageView currentDialogImagePreview; // Reference to the image preview in the dialog
    private boolean isUploadingImage = false; // Tracks if an image is currently being uploaded

    // ActivityResultLauncher for gallery - modern replacement for onActivityResult
    // This launches the gallery and waits for the user to pick an image
    private ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), // Contract for picking content from gallery
            uri -> { // This runs when the user picks an image
                if (uri != null) { // If user picked an image (not cancelled)
                    selectedImageUri = uri; // Save the image URI
                    if (currentDialogImagePreview != null) { // If the dialog is still open
                        currentDialogImagePreview.setVisibility(View.VISIBLE); // Show the preview
                        Glide.with(this).load(uri).into(currentDialogImagePreview); // Load image into preview
                    }
                }
            });

    // ActivityResultLauncher for camera - launches the camera and waits for a photo
    private ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), // Contract for taking a picture
            success -> { // This runs when the user takes a photo
                if (success && selectedImageUri != null) { // If photo was taken successfully
                    if (currentDialogImagePreview != null) { // If the dialog is still open
                        currentDialogImagePreview.setVisibility(View.VISIBLE); // Show the preview
                        Glide.with(this).load(selectedImageUri).into(currentDialogImagePreview); // Load photo into preview
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Connect Java variables to XML views
        recyclerCategories = findViewById(R.id.recyclerCategories);
        fabCategory = findViewById(R.id.fabCategory);
        fabExitEditMode = findViewById(R.id.fabExitEditMode);
        tvBack = findViewById(R.id.tvBack);
        tvEditMode = findViewById(R.id.tvEditMode);
        tvEditBanner = findViewById(R.id.tvEditBanner);

        categoryList = new ArrayList<>();

        setupRecyclerView();
        checkIfAdmin();
        loadCategories();

        tvBack.setOnClickListener(v -> finish()); // Go back to HomeActivity
        tvEditMode.setOnClickListener(v -> enterEditMode()); // Enter edit mode
        fabCategory.setOnClickListener(v -> showAddCategoryDialog()); // Show add dialog
        fabExitEditMode.setOnClickListener(v -> exitEditMode()); // Exit edit mode
    }

    private void setupRecyclerView() {
        // GridLayoutManager with 2 columns - shows 2 category cards per row
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerCategories.setLayoutManager(layoutManager);
        categoryAdapter = new CategoryAdapter(this, categoryList, this);
        recyclerCategories.setAdapter(categoryAdapter);
    }

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return; // Guest - skip check

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users")
                .document(userId)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        if (role != null && role.equals("ADMIN")) {
                            isAdmin = true;
                            tvEditMode.setVisibility(View.VISIBLE); // Show edit mode button for admin
                        }
                    }
                });
    }

    private void loadCategories() {
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    categoryList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Category category = doc.toObject(Category.class);
                        category.setIdFS(doc.getId());
                        categoryList.add(category);
                    }
                    categoryAdapter.notifyDataSetChanged();
                });
    }

    private void enterEditMode() {
        isEditMode = true;
        tvEditBanner.setVisibility(View.VISIBLE);
        fabCategory.setVisibility(View.VISIBLE);
        fabExitEditMode.setVisibility(View.VISIBLE);
        tvEditMode.setVisibility(View.GONE);
    }

    private void exitEditMode() {
        isEditMode = false;
        tvEditBanner.setVisibility(View.GONE);
        fabCategory.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE);
    }

    private void showImagePickerDialog() {
        // Show a simple dialog with 2 options - Camera or Gallery
        new AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"📷 Camera", "🖼️ Gallery"}, (dialog, which) -> {
                    if (which == 0) { // Camera selected
                        // Create a temporary file URI to save the photo
                        selectedImageUri = createTempImageUri();
                        cameraLauncher.launch(selectedImageUri); // Launch camera
                    } else { // Gallery selected
                        galleryLauncher.launch("image/*"); // Launch gallery - "image/*" means any image type
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private Uri createTempImageUri() {
        // Create a temporary file to store the camera photo
        // getExternalCacheDir() returns a temporary folder in the app's storage
        java.io.File photoFile = new java.io.File(getExternalCacheDir(), "temp_photo_" + System.currentTimeMillis() + ".jpg");
        // FileProvider converts the file path into a URI that the camera app can use
        return androidx.core.content.FileProvider.getUriForFile(this, getPackageName() + ".provider", photoFile);
    }

    private void showAddCategoryDialog() {
        selectedImageUri = null; // Reset the selected image

        // Inflate our dialog_category.xml layout
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category, null);

        // Get the views from the dialog layout
        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etCategoryNameHebrew = dialogView.findViewById(R.id.etCategoryNameHebrew);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);
        Button btnAddImage = dialogView.findViewById(R.id.btnAddImage);

        currentDialogImagePreview = imgPreview; // Save reference to the preview ImageView

        // Add Image button click - show camera/gallery picker
        btnAddImage.setOnClickListener(v -> showImagePickerDialog());

        // Build the dialog
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add New Category")
                .setView(dialogView)
                .setPositiveButton("Add", null) // null because we handle click manually below
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show(); // Show the dialog first so we can access the Add button

        // Override the Add button click to validate before closing
        // We do this AFTER show() so the button exists
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            String nameHebrew = etCategoryNameHebrew.getText().toString().trim();

            // Validate all 3 fields - block if any is empty
            if (name.isEmpty()) {
                etCategoryName.setError("Please enter the category name in English");
                return; // Stop here - don't close the dialog
            }
            if (nameHebrew.isEmpty()) {
                etCategoryNameHebrew.setError("Please enter the category name in Hebrew");
                return; // Stop here
            }
            if (selectedImageUri == null) { // No image selected
                Toast.makeText(this, "Please add an image", Toast.LENGTH_SHORT).show();
                return; // Stop here
            }

            // All fields are valid - upload image to Cloudinary then save to Firestore
            uploadImageAndSaveCategory(name, nameHebrew, null, dialog);
        });
    }

    private void showEditCategoryDialog(Category category) {
        selectedImageUri = null; // Reset selected image

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category, null);

        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etCategoryNameHebrew = dialogView.findViewById(R.id.etCategoryNameHebrew);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);
        Button btnAddImage = dialogView.findViewById(R.id.btnAddImage);

        currentDialogImagePreview = imgPreview;

        // Pre-fill the fields with existing data
        etCategoryName.setText(category.getCategoryName());
        etCategoryNameHebrew.setText(category.getCategoryNameHebrew());

        // Show the existing image in the preview
        imgPreview.setVisibility(View.VISIBLE);
        Glide.with(this).load(category.getImage()).into(imgPreview);

        // Change button text to show the image can be changed
        btnAddImage.setText("🔄 Change Image");
        btnAddImage.setOnClickListener(v -> showImagePickerDialog());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Category")
                .setView(dialogView)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            String nameHebrew = etCategoryNameHebrew.getText().toString().trim();

            if (name.isEmpty()) {
                etCategoryName.setError("Please enter the category name in English");
                return;
            }
            if (nameHebrew.isEmpty()) {
                etCategoryNameHebrew.setError("Please enter the category name in Hebrew");
                return;
            }

            // If admin picked a new image, upload it - otherwise keep the existing URL
            if (selectedImageUri != null) {
                uploadImageAndSaveCategory(name, nameHebrew, category.getIdFS(), dialog);
            } else {
                // No new image selected - keep existing image URL
                updateCategoryInFirestore(category.getIdFS(), name, nameHebrew, category.getImage());
                dialog.dismiss(); // Close the dialog
            }
        });
    }

    private void uploadImageAndSaveCategory(String name, String nameHebrew, String categoryId, AlertDialog dialog) {
        // Show loading message while uploading
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();
        isUploadingImage = true;

        // Upload the image to Cloudinary
        MediaManager.get()
                .upload(selectedImageUri) // The image URI to upload
                .option("upload_preset", "EnglishKingdom") // Our Cloudinary upload preset
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        // Called when upload starts - nothing to do here
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                        // Called while uploading - could show a progress bar here later
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Called when upload succeeds
                        isUploadingImage = false;
                        String imageUrl = (String) resultData.get("secure_url"); // Get the uploaded image URL

                        if (categoryId == null) { // Adding new category
                            addCategoryToFirestore(name, nameHebrew, imageUrl);
                        } else { // Editing existing category
                            updateCategoryInFirestore(categoryId, name, nameHebrew, imageUrl);
                        }
                        dialog.dismiss(); // Close the dialog
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        // Called when upload fails
                        isUploadingImage = false;
                        Toast.makeText(LearnActivity.this, "Image upload failed: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                        // Called when upload is rescheduled - nothing to do here
                    }
                })
                .dispatch(); // Start the upload
    }

    private void addCategoryToFirestore(String name, String nameHebrew, String imageUrl) {
        Category category = new Category(null, name, nameHebrew, imageUrl, 0);
        db.collection("categories")
                .add(category)
                .addOnSuccessListener(documentReference -> {
                    Toast.makeText(this, "Category added! 🎉", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error adding category", Toast.LENGTH_SHORT).show();
                });
    }

    private void updateCategoryInFirestore(String categoryId, String name, String nameHebrew, String imageUrl) {
        db.collection("categories")
                .document(categoryId)
                .update("categoryName", name, "categoryNameHebrew", nameHebrew, "image", imageUrl)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Category updated! ✅", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error updating category", Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteConfirmationDialog(Category category) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete \"" + category.getCategoryName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    deleteCategoryFromFirestore(category.getIdFS());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteCategoryFromFirestore(String categoryId) {
        db.collection("categories")
                .document(categoryId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Category deleted! 🗑️", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onCategoryClick(Category category) {
        if (isEditMode) {
            showEditCategoryDialog(category); // Edit mode - show edit dialog
        } else {
            // TODO: Go to Words screen
        }
    }

    @Override
    public void onCategoryLongClick(Category category) {
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // Show delete confirmation
        }
    }
}
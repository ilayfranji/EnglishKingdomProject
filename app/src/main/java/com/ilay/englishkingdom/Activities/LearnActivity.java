package com.ilay.englishkingdom.Activities;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Activities.Dialogs.AddCategoryDialog;
import com.ilay.englishkingdom.Activities.Dialogs.EditCategoryDialog;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.Adapters.CategoryAdapter;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;
import com.ilay.englishkingdom.Utils.PermissionManager;

import java.util.ArrayList;
import java.util.List;

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    // ==================== UI ELEMENTS ====================

    private RecyclerView recyclerCategories; // The scrollable grid of category cards
    private FloatingActionButton fabCategory; // The + button shown in edit mode to add categories
    private FloatingActionButton fabExitEditMode; // The X button to exit edit mode
    private TextView tvBack; // Back arrow to go back to HomeActivity
    private TextView tvEditMode; // Pencil button shown only to admins
    private TextView tvEditBanner; // Red banner shown at top when in edit mode

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Our connection to Firebase Authentication

    // ==================== ADAPTER AND DATA ====================

    private CategoryAdapter categoryAdapter; // Connects our category list to the RecyclerView
    private List<Category> categoryList; // The list of all categories loaded from Firestore

    // ==================== STATE FLAGS ====================

    private boolean isEditMode = false; // true = admin is in edit mode, false = normal mode
    private boolean isAdmin = false; // true = current user is an admin

    // ==================== HELPERS AND DIALOGS ====================

    // ImagePickerHelper handles ALL camera/gallery/permission logic
    // We create it once here and pass it to both dialogs so they share it
    private ImagePickerHelper imagePicker;

    // AddCategoryDialog handles the entire "Add New Category" flow
    // We create it once and call addDialog.show() when admin taps +
    private AddCategoryDialog addDialog;

    // EditCategoryDialog handles the entire "Edit Category" flow
    // We create it once and call editDialog.show(category) when admin taps a card
    private EditCategoryDialog editDialog;

    // ==================== SAVE/RESTORE STATE ====================

    // Key used to save and restore the camera URI when Android kills the activity
    // Android might kill LearnActivity when camera opens to free up memory
    // We save the URI before that happens and restore it when activity comes back
    private static final String KEY_CAMERA_URI = "camera_uri";

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These MUST be created here in the Activity - Android does not allow creating
    // them inside helper classes or dialogs. We pass them into ImagePickerHelper
    // so it can launch them, but they live here.

    // Gallery launcher - opens gallery, result comes back here, we forward it to ImagePickerHelper
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // Forward result to ImagePickerHelper

    // Camera launcher - opens camera, result comes back here, we forward it to ImagePickerHelper
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> imagePicker.onCameraResult(success)); // Forward result to ImagePickerHelper

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        // Restore camera URI if Android killed and recreated the activity
        // savedInstanceState is null the first time, but has our saved data if recreated
        Uri restoredUri = null;
        if (savedInstanceState != null) {
            restoredUri = savedInstanceState.getParcelable(KEY_CAMERA_URI);
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

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

        // Create ImagePickerHelper - pass the launchers and a callback
        // The callback (onImagePicked) runs when the user picks a photo
        // We decide here which dialog gets the photo based on which one is currently open
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    // This runs after the user picks or takes a photo
                    // We tell BOTH dialogs about the new image
                    // Each dialog ignores it if it's not currently open (checked with isShowing())
                    addDialog.onImagePicked(uri);
                    editDialog.onImagePicked(uri);
                },
                galleryLauncher,
                cameraLauncher);

        // Restore the camera URI into ImagePickerHelper if activity was recreated
        if (restoredUri != null) {
            imagePicker.setPendingCameraUri(restoredUri);
        }

        // Create the dialogs - pass imagePicker so they can trigger image picking
        addDialog = new AddCategoryDialog(this, imagePicker,
                () -> {}); // Empty callback - snapshot listener already refreshes the list automatically

        editDialog = new EditCategoryDialog(this, imagePicker,
                () -> {}); // Empty callback - same reason

        // Set click listeners
        tvBack.setOnClickListener(v -> finish()); // Close this screen and go back
        tvEditMode.setOnClickListener(v -> enterEditMode()); // Enter edit mode
        fabCategory.setOnClickListener(v -> addDialog.show()); // Show Add dialog - ONE LINE!
        fabExitEditMode.setOnClickListener(v -> exitEditMode()); // Exit edit mode
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save the camera URI before Android might kill the activity
        // imagePicker.getPendingCameraUri() returns the URI we created for the camera
        // If it's null (no camera in progress) nothing gets saved - that's fine
        if (imagePicker != null && imagePicker.getPendingCameraUri() != null) {
            outState.putParcelable(KEY_CAMERA_URI, imagePicker.getPendingCameraUri());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward the permission result to ImagePickerHelper
        // ImagePickerHelper is the one who requested the permission so it handles the result
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== SETUP ====================

    private void setupRecyclerView() {
        // GridLayoutManager with 2 columns arranges cards side by side
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerCategories.setLayoutManager(layoutManager);
        categoryAdapter = new CategoryAdapter(this, categoryList, this);
        recyclerCategories.setAdapter(categoryAdapter);
    }

    // ==================== ADMIN CHECK ====================

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return; // No user logged in - skip

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        if (role != null && role.equals("ADMIN")) {
                            isAdmin = true;
                            tvEditMode.setVisibility(View.VISIBLE); // Show pencil button for admin only
                        }
                    }
                });
    }

    // ==================== LOAD CATEGORIES ====================

    private void loadCategories() {
        // addSnapshotListener = real time updates - runs every time Firestore data changes
        // So when we add/edit/delete a category it refreshes automatically
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    categoryList.clear(); // Clear old data before adding fresh data
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Category category = doc.toObject(Category.class); // Convert document to Category object
                        category.setIdFS(doc.getId()); // Save the Firestore document ID into the object
                        categoryList.add(category);
                    }
                    categoryAdapter.notifyDataSetChanged(); // Tell RecyclerView to refresh
                });
    }

    // ==================== EDIT MODE ====================

    private void enterEditMode() {
        isEditMode = true;
        tvEditBanner.setVisibility(View.VISIBLE); // Show red edit mode banner
        fabCategory.setVisibility(View.VISIBLE); // Show + button
        fabExitEditMode.setVisibility(View.VISIBLE); // Show X button
        tvEditMode.setVisibility(View.GONE); // Hide pencil button
    }

    private void exitEditMode() {
        isEditMode = false;
        tvEditBanner.setVisibility(View.GONE);
        fabCategory.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE); // Show pencil button again
    }

    // ==================== CATEGORY CLICK LISTENERS ====================

    @Override
    public void onCategoryClick(Category category) {
        // Called by CategoryAdapter when a card is tapped
        if (isEditMode) {
            editDialog.show(category); // Edit mode → open edit dialog - ONE LINE!
        } else {
            // TODO: Navigate to Words screen
        }
    }

    @Override
    public void onCategoryLongClick(Category category) {
        // Called by CategoryAdapter when a card is long pressed
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // Edit mode → show delete confirmation
        }
    }

    // ==================== DELETE ====================

    private void showDeleteConfirmationDialog(Category category) {
        // Simple confirmation popup before permanently deleting - prevents accidents
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete \"" + category.getCategoryName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirestore(category.getIdFS()))
                .setNegativeButton("Cancel", null) // null = just close, don't delete
                .show();
    }

    private void deleteCategoryFromFirestore(String categoryId) {
        // Permanently deletes the category document from Firestore
        db.collection("categories").document(categoryId).delete()
                .addOnSuccessListener(v -> Toast.makeText(this, "Category deleted! 🗑️", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show());
    }
}
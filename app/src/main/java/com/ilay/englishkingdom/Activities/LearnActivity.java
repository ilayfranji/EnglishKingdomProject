package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open WordsActivity and pass categoryId
import android.net.Uri; // Used to store the camera photo file path
import android.os.Bundle; // Used when creating the activity and saving state
import android.view.View; // Used to show and hide UI elements
import android.widget.TextView; // Used for the back button, edit button, banner
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery/camera
import androidx.activity.result.contract.ActivityResultContracts; // Provides contracts for gallery and camera
import androidx.appcompat.app.AlertDialog; // Used to show the delete confirmation popup
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.recyclerview.widget.GridLayoutManager; // Arranges category cards in a 2 column grid
import androidx.recyclerview.widget.RecyclerView; // The scrollable grid of category cards

import com.google.android.material.floatingactionbutton.FloatingActionButton; // The round + and X buttons
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read/write categories
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single category document
import com.ilay.englishkingdom.Activities.Dialogs.AddCategoryDialog; // Handles the Add Category flow
import com.ilay.englishkingdom.Activities.Dialogs.EditCategoryDialog; // Handles the Edit Category flow
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery/permissions
import com.ilay.englishkingdom.Adapters.CategoryAdapter; // Connects our category list to RecyclerView
import com.ilay.englishkingdom.Models.Category; // Our Category data model
import com.ilay.englishkingdom.R; // Used to reference XML resources
import com.ilay.englishkingdom.Utils.PermissionManager; // Our helper class for permissions

import java.util.ArrayList; // Used to create the category list
import java.util.List; // The List interface for our category list

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {

    // ==================== UI ELEMENTS ====================

    private RecyclerView recyclerCategories; // The scrollable grid of category cards
    private FloatingActionButton fabCategory; // The + button shown in edit mode
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

    // ==================== STATE ====================

    private boolean isEditMode = false; // true = admin is in edit mode, false = normal mode
    private boolean isAdmin = false; // true = current user is an admin

    // ==================== HELPERS AND DIALOGS ====================

    private ImagePickerHelper imagePicker; // Handles ALL camera/gallery/permission logic
    private AddCategoryDialog addDialog; // Handles the Add Category flow
    private EditCategoryDialog editDialog; // Handles the Edit Category flow

    // ==================== SAVE/RESTORE STATE ====================

    private static final String KEY_CAMERA_URI = "camera_uri"; // Key to save camera URI

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These MUST live here in the Activity - passed into ImagePickerHelper
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // Forward result to ImagePickerHelper

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> imagePicker.onCameraResult(success)); // Forward result to ImagePickerHelper

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_learn);

        // Restore camera URI if Android killed and recreated the activity
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

        // GridLayoutManager with 2 columns arranges cards side by side
        recyclerCategories.setLayoutManager(new GridLayoutManager(this, 2));
        categoryAdapter = new CategoryAdapter(this, categoryList, this);
        recyclerCategories.setAdapter(categoryAdapter);

        checkIfAdmin();
        loadCategories();

        // Create ImagePickerHelper - forward image to whichever dialog is open
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    // Tell both dialogs about the new image
                    // Each dialog checks isShowing() and ignores it if not currently open
                    addDialog.onImagePicked(uri);
                    editDialog.onImagePicked(uri);
                },
                galleryLauncher,
                cameraLauncher);

        if (restoredUri != null) imagePicker.setPendingCameraUri(restoredUri);

        // Create dialogs
        addDialog = new AddCategoryDialog(this, imagePicker, () -> {});
        editDialog = new EditCategoryDialog(this, imagePicker, () -> {});

        tvBack.setOnClickListener(v -> finish());
        tvEditMode.setOnClickListener(v -> enterEditMode());
        fabCategory.setOnClickListener(v -> addDialog.show());
        fabExitEditMode.setOnClickListener(v -> exitEditMode());
    }

    // ==================== RESUME ====================

    @Override
    protected void onResume() {
        super.onResume(); // Always call super first
        // This runs every time we come back to this screen
        // For example when user presses back from WordsActivity
        // We refresh all cards so the progress bars show the latest learned word counts
        // This works together with the get() in CategoryAdapter -
        // notifyDataSetChanged() triggers onBindViewHolder for every card
        // which calls get() again to fetch the latest progress from Firestore
        if (categoryAdapter != null) {
            categoryAdapter.notifyDataSetChanged();
        }
    }

    // ==================== SAVE STATE ====================

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (imagePicker != null && imagePicker.getPendingCameraUri() != null) {
            outState.putParcelable(KEY_CAMERA_URI, imagePicker.getPendingCameraUri());
        }
    }

    // ==================== PERMISSION RESULT ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Forward permission result to ImagePickerHelper - it knows what to do
        imagePicker.onPermissionResult(requestCode, grantResults);
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
                            tvEditMode.setVisibility(View.VISIBLE); // Show pencil for admin only
                        }
                    }
                });
    }

    // ==================== LOAD CATEGORIES ====================

    private void loadCategories() {
        // Real time listener - updates automatically when categories are added/edited/deleted
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    categoryList.clear(); // Clear old data before adding fresh data
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Category category = doc.toObject(Category.class); // Convert to Category object
                        category.setIdFS(doc.getId()); // Save document ID into the object
                        categoryList.add(category);
                    }
                    categoryAdapter.notifyDataSetChanged(); // Refresh the grid
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
        tvEditMode.setVisibility(View.VISIBLE);
    }

    // ==================== CATEGORY CLICK LISTENERS ====================

    @Override
    public void onCategoryClick(Category category) {
        // Called by CategoryAdapter when a category card is tapped
        if (isEditMode) {
            editDialog.show(category); // Edit mode → open edit dialog
        } else {
            // Normal mode → open WordsActivity and pass the categoryId and categoryName
            Intent intent = new Intent(this, WordsActivity.class);
            intent.putExtra("categoryId", category.getIdFS()); // So WordsActivity knows which words to load
            intent.putExtra("categoryName", category.getCategoryName()); // For display at the top
            intent.putExtra("categoryType", category.getCategoryType()); // "WORDS"/"LETTERS"/"SENTENCES"
            startActivity(intent);
        }
    }

    @Override
    public void onCategoryLongClick(Category category) {
        // Called by CategoryAdapter when a category card is long pressed
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // Edit mode → show delete confirmation
        }
    }

    // ==================== DELETE ====================

    private void showDeleteConfirmationDialog(Category category) {
        // Shows a confirmation popup before deleting - prevents accidental deletion
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("Are you sure you want to delete \"" + category.getCategoryName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteCategoryFromFirestore(category.getIdFS()))
                .setNegativeButton("Cancel", null) // null = just close, don't delete
                .show();
    }

    private void deleteCategoryFromFirestore(String categoryId) {
        // Permanently deletes the category document from Firestore
        db.collection("categories").document(categoryId).delete()
                .addOnSuccessListener(v -> Toast.makeText(this, "Category deleted!", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show());
    }
}
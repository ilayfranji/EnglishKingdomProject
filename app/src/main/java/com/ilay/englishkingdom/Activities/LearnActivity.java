package com.ilay.englishkingdom.Activities;

import android.app.AlertDialog; // Used to create popup dialogs
import android.net.Uri; // Used to store the path to an image file
import android.os.Bundle; // Used when creating the activity and saving state
import android.view.View; // Used to reference UI elements
import android.widget.Button; // Used for the Add Image button
import android.widget.EditText; // Used for text input fields
import android.widget.ImageView; // Used to display images
import android.widget.LinearLayout; // Used as a container to wrap the image in dialogs
import android.widget.TextView; // Used for text views
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery/camera and get result back
import androidx.activity.result.contract.ActivityResultContracts; // Provides the contracts for gallery and camera
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.recyclerview.widget.GridLayoutManager; // Used to arrange category cards in a grid
import androidx.recyclerview.widget.RecyclerView; // Used to show the scrollable list of categories

import com.bumptech.glide.Glide; // Used to load images from URLs or URIs into ImageViews
import com.google.android.material.floatingactionbutton.FloatingActionButton; // The round floating buttons
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read/write data from our database
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single document from Firestore query
import com.ilay.englishkingdom.Adapters.CategoryAdapter; // Our custom adapter for the category RecyclerView
import com.ilay.englishkingdom.Models.Category; // Our Category data model class
import com.ilay.englishkingdom.R; // Used to reference our XML resources

import com.cloudinary.android.MediaManager; // Used to upload images to Cloudinary
import com.cloudinary.android.callback.ErrorInfo; // Used to get error details if upload fails
import com.cloudinary.android.callback.UploadCallback; // Used to listen for upload progress and result

import java.util.ArrayList; // Used to create the category list
import java.util.List; // The List interface for our category list
import java.util.Map; // Used to read the Cloudinary upload result

public class LearnActivity extends AppCompatActivity implements CategoryAdapter.OnCategoryClickListener {
    // AppCompatActivity = this is a screen in our app
    // implements CategoryAdapter.OnCategoryClickListener = this class handles card click and long click events

    // ==================== UI ELEMENTS ====================

    private RecyclerView recyclerCategories; // The scrollable grid of category cards
    private FloatingActionButton fabCategory; // The + FAB button shown in edit mode to add categories
    private FloatingActionButton fabExitEditMode; // The X FAB button to exit edit mode
    private TextView tvBack; // The back arrow/text to go back to HomeActivity
    private TextView tvEditMode; // The pencil button shown to admins to enter edit mode
    private TextView tvEditBanner; // The red banner shown at the top when in edit mode

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Our connection to Firebase Authentication

    // ==================== ADAPTER AND DATA ====================

    private CategoryAdapter categoryAdapter; // The adapter that connects our list to the RecyclerView
    private List<Category> categoryList; // The list of all categories loaded from Firestore

    // ==================== STATE FLAGS ====================

    private boolean isEditMode = false; // true = admin is in edit mode, false = normal mode
    private boolean isAdmin = false; // true = current user is an admin

    // ==================== IMAGE HANDLING ====================

    private Uri selectedImageUri = null; // Stores the URI (file path) of the image picked by admin - null means no image selected
    private ImageView currentDialogImagePreview; // Reference to the ImageView inside the currently open dialog
    private Button currentDialogAddImageButton; // Reference to the Add Image button inside the currently open dialog

    // ==================== SAVE/RESTORE STATE ====================

    // This is the KEY we use to save and restore selectedImageUri
    // When the camera app opens, Android might kill LearnActivity to free up memory
    // We save selectedImageUri in onSaveInstanceState() so we can restore it when the activity comes back
    // Think of this key like a label on a bag - we put selectedImageUri in the bag before Android takes it
    private static final String KEY_SELECTED_IMAGE_URI = "selected_image_uri";

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // Gallery launcher - opens the gallery app and waits for the user to pick an image
    // registerForActivityResult sets this up BEFORE onCreate runs - this is required by Android
    private ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(), // GetContent = open file picker for a specific type
            uri -> { // This runs AFTER the user picks an image and returns to our app
                if (uri != null) { // If user actually picked something (didn't cancel)
                    selectedImageUri = uri; // Save the URI so we can upload it later
                    showImageConfirmationDialog(uri, true); // true = came from gallery, no Retake button
                }
            });

    // Camera launcher - opens the camera app and waits for the user to take a photo
    private ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), // TakePicture = open camera and save to a URI we provide
            success -> { // This runs AFTER the user takes a photo and returns to our app
                // selectedImageUri is restored from onSaveInstanceState if Android killed the activity
                // so it will NEVER be null here even if Android killed the activity while camera was open
                if (success && selectedImageUri != null) { // Photo was taken successfully
                    showImageConfirmationDialog(selectedImageUri, false); // false = came from camera, show Retake
                } else if (!success) { // User cancelled the camera without taking a photo
                    selectedImageUri = null; // Clear the URI since no photo was taken
                }
            });

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Always call super first - required by Android
        setContentView(R.layout.activity_learn); // Connect this Java file to activity_learn.xml

        // CAMERA CRASH FIX - Restore selectedImageUri if Android killed and recreated the activity
        // savedInstanceState is the "bag" Android gives back to us after killing and recreating the activity
        // It is null the very first time the activity opens, but contains our saved data if activity was recreated
        if (savedInstanceState != null) { // Activity was recreated (e.g. after camera opened)
            // getParcelable reads the Uri we saved in onSaveInstanceState
            // Uri is "Parcelable" meaning Android knows how to save and restore it
            selectedImageUri = savedInstanceState.getParcelable(KEY_SELECTED_IMAGE_URI);
        }

        // Connect to Firebase
        db = FirebaseFirestore.getInstance(); // Get the shared Firestore instance
        mAuth = FirebaseAuth.getInstance(); // Get the shared Auth instance

        // Connect each Java variable to its XML view using the ID we gave in XML
        recyclerCategories = findViewById(R.id.recyclerCategories);
        fabCategory = findViewById(R.id.fabCategory);
        fabExitEditMode = findViewById(R.id.fabExitEditMode);
        tvBack = findViewById(R.id.tvBack);
        tvEditMode = findViewById(R.id.tvEditMode);
        tvEditBanner = findViewById(R.id.tvEditBanner);

        categoryList = new ArrayList<>(); // Initialize as empty list - will be filled by loadCategories()

        setupRecyclerView(); // Set up the grid layout and attach the adapter
        checkIfAdmin(); // Check if current user is admin and show edit button if so
        loadCategories(); // Start listening to Firestore for categories in real time

        // Set click listeners for all buttons
        tvBack.setOnClickListener(v -> finish()); // finish() closes this activity and goes back
        tvEditMode.setOnClickListener(v -> enterEditMode()); // Enter edit mode when pencil is clicked
        fabCategory.setOnClickListener(v -> showAddCategoryDialog()); // Show add dialog when + is clicked
        fabExitEditMode.setOnClickListener(v -> exitEditMode()); // Exit edit mode when X is clicked
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState); // Always call super first

        // Save selectedImageUri into the Bundle BEFORE Android might kill the activity
        // This happens automatically when Android needs to free memory (e.g. camera app opens)
        // outState is the "bag" - putParcelable puts our Uri into it under the key label
        // Later in onCreate, savedInstanceState.getParcelable() takes it back out of the bag
        if (selectedImageUri != null) { // Only save if we actually have an image selected
            outState.putParcelable(KEY_SELECTED_IMAGE_URI, selectedImageUri);
        }
    }

    // ==================== SETUP ====================

    private void setupRecyclerView() {
        // GridLayoutManager arranges items in a grid - 2 means 2 columns side by side
        GridLayoutManager layoutManager = new GridLayoutManager(this, 2);
        recyclerCategories.setLayoutManager(layoutManager); // Tell RecyclerView to use this layout
        categoryAdapter = new CategoryAdapter(this, categoryList, this); // Create adapter with our list and click listener
        recyclerCategories.setAdapter(categoryAdapter); // Attach adapter to RecyclerView
    }

    // ==================== ADMIN CHECK ====================

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return; // Guest user - no admin features

        String userId = mAuth.getCurrentUser().getUid(); // Get the logged in user's unique ID
        db.collection("users").document(userId).get() // Fetch this user's document from Firestore
                .addOnSuccessListener(document -> { // Runs when Firestore responds
                    if (document.exists()) { // If the document exists
                        String role = document.getString("role"); // Read the "role" field
                        if (role != null && role.equals("ADMIN")) { // If role is ADMIN
                            isAdmin = true; // Mark this user as admin
                            tvEditMode.setVisibility(View.VISIBLE); // Show the pencil edit button
                        }
                    }
                });
    }

    // ==================== LOAD CATEGORIES ====================

    private void loadCategories() {
        // addSnapshotListener listens for real time changes - runs every time Firestore data changes
        db.collection("categories")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) { // Something went wrong with Firestore
                        Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show();
                        return; // Stop here - don't try to process the data
                    }
                    categoryList.clear(); // Remove all old categories before adding fresh ones
                    for (QueryDocumentSnapshot doc : snapshots) { // Loop through every category document
                        Category category = doc.toObject(Category.class); // Convert document to Category object
                        category.setIdFS(doc.getId()); // Save the Firestore document ID into the object
                        categoryList.add(category); // Add to our list
                    }
                    categoryAdapter.notifyDataSetChanged(); // Tell RecyclerView the data changed so it refreshes
                });
    }

    // ==================== EDIT MODE ====================

    private void enterEditMode() {
        isEditMode = true; // Set flag so click and long click behave differently
        tvEditBanner.setVisibility(View.VISIBLE); // Show red "EDIT MODE" banner at top
        fabCategory.setVisibility(View.VISIBLE); // Show + FAB to add categories
        fabExitEditMode.setVisibility(View.VISIBLE); // Show X FAB to exit edit mode
        tvEditMode.setVisibility(View.GONE); // Hide the pencil button
    }

    private void exitEditMode() {
        isEditMode = false; // Reset flag back to normal mode
        tvEditBanner.setVisibility(View.GONE); // Hide the red banner
        fabCategory.setVisibility(View.GONE); // Hide + FAB
        fabExitEditMode.setVisibility(View.GONE); // Hide X FAB
        tvEditMode.setVisibility(View.VISIBLE); // Show pencil button again
    }

    // ==================== IMAGE PICKER ====================

    private void showImagePickerDialog() {
        // Shows a simple popup with two options: Camera or Gallery
        new AlertDialog.Builder(this)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"📷 Camera", "🖼️ Gallery"}, (dialog, which) -> {
                    if (which == 0) { // Index 0 = Camera was tapped
                        selectedImageUri = createTempImageUri(); // Create the temp file BEFORE launching camera
                        // We must create the URI first because the camera needs to know WHERE to save the photo
                        cameraLauncher.launch(selectedImageUri); // Open camera app
                    } else { // Index 1 = Gallery was tapped
                        galleryLauncher.launch("image/*"); // "image/*" means accept any image type
                    }
                })
                .setNegativeButton("Cancel", null) // null = just close the dialog, no extra action
                .show(); // Display the dialog
    }

    // ==================== IMAGE CONFIRMATION DIALOG ====================

    private void showImageConfirmationDialog(Uri imageUri, boolean fromGallery) {
        // Shows the selected image in a popup BEFORE it gets added to the dialog
        // Admin can then choose: Select (add to dialog), Delete (discard), or Retake (camera only)
        // fromGallery = true → came from gallery → NO Retake button
        // fromGallery = false → came from camera → show Retake button

        // LinearLayout is a simple container - we use it to wrap the ImageView
        // Without a container, AlertDialog doesn't display the ImageView properly
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL); // Stack children from top to bottom
        layout.setPadding(8, 8, 8, 8); // Small padding so image doesn't touch the dialog edges

        // Create the ImageView that will show the selected/taken image
        ImageView previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER); // Show entire image without cropping
        previewImage.setAdjustViewBounds(true); // Automatically adjust height to keep image proportions
        Glide.with(this).load(imageUri).into(previewImage); // Load the image from the URI using Glide
        previewImage.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, // Full width of the dialog
                600 // Fixed height in pixels - tall enough to clearly see the image
        ));
        layout.addView(previewImage); // Add the ImageView into our LinearLayout container

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setView(layout) // Show our layout (with the image) as the dialog content
                .setPositiveButton("✅ Select", (dialog, which) -> {
                    // Admin confirmed they want this image
                    if (currentDialogImagePreview != null) {
                        currentDialogImagePreview.setVisibility(View.VISIBLE); // Show the preview in the dialog
                        currentDialogImagePreview.setScaleType(ImageView.ScaleType.FIT_CENTER); // No cropping
                        currentDialogImagePreview.setAdjustViewBounds(true); // Keep proportions
                        Glide.with(this).load(imageUri).into(currentDialogImagePreview); // Load image into dialog

                        // FIX FOR ISSUE 1: After image is shown in dialog, make it clickable
                        // Without this, clicking the preview after selecting did nothing
                        // Now clicking opens showSelectedImageOptionsDialog so admin can Change or Delete
                        currentDialogImagePreview.setOnClickListener(v ->
                                showSelectedImageOptionsDialog(imageUri));
                    }
                    if (currentDialogAddImageButton != null) {
                        currentDialogAddImageButton.setVisibility(View.GONE); // Hide Add Image button
                    }
                })
                .setNegativeButton("🗑️ Delete", (dialog, which) -> {
                    // Admin discarded the image - reset everything back
                    selectedImageUri = null; // Clear the saved URI
                    if (currentDialogImagePreview != null) {
                        currentDialogImagePreview.setVisibility(View.GONE); // Hide the preview
                        currentDialogImagePreview.setOnClickListener(null); // Remove click listener - image is gone
                    }
                    if (currentDialogAddImageButton != null) {
                        currentDialogAddImageButton.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                });

        // Only add the Retake button if the image came from the camera
        // Gallery images don't need Retake because the user can simply pick a different image
        if (!fromGallery) {
            builder.setNeutralButton("📷 Retake", (dialog, which) -> {
                showImagePickerDialog(); // Open the Camera/Gallery picker again
            });
        }

        builder.show(); // Display the confirmation dialog
    }

    // ==================== SELECTED IMAGE OPTIONS (after picking from gallery/camera) ====================

    private void showSelectedImageOptionsDialog(Uri imageUri) {
        // Shows when admin clicks on a NEWLY picked image shown in the Add/Edit dialog
        // This is different from showExistingImageOptionsDialog which is for images already saved in Firestore
        // Options: Change (pick a new image), Delete (remove image), Cancel (do nothing)

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(8, 8, 8, 8);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER); // Full image no cropping
        imageView.setAdjustViewBounds(true); // Keep proportions
        Glide.with(this).load(imageUri).into(imageView); // Load the newly picked image
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                600 // Fixed height
        ));
        layout.addView(imageView); // Add image to the layout container

        new AlertDialog.Builder(this)
                .setTitle("Image Options") // Title so admin knows what the buttons do
                .setView(layout) // Show the image in the popup
                .setPositiveButton("🔄 Change", (dialog, which) -> {
                    // Admin wants to pick a different image - open picker again
                    showImagePickerDialog();
                })
                .setNegativeButton("🗑️ Delete", (dialog, which) -> {
                    // Admin wants to remove the image entirely
                    selectedImageUri = null; // Clear the URI
                    if (currentDialogImagePreview != null) {
                        currentDialogImagePreview.setVisibility(View.GONE); // Hide the preview in the dialog
                        currentDialogImagePreview.setOnClickListener(null); // Remove click listener - nothing to click anymore
                    }
                    if (currentDialogAddImageButton != null) {
                        currentDialogAddImageButton.setVisibility(View.VISIBLE); // Show Add Image button again
                    }
                })
                .setNeutralButton("❌ Cancel", null) // null = just close the popup, keep image as is
                .show();
    }

    // ==================== EXISTING IMAGE OPTIONS (for images already saved in Firestore) ====================

    private void showExistingImageOptionsDialog(String imageUrl) {
        // Shows when admin clicks on an image that is ALREADY SAVED in Firestore (only in Edit dialog)
        // imageUrl is a Cloudinary HTTPS URL string, not a local URI
        // Options: Change (pick new image), Delete (remove image), Cancel (do nothing)

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(8, 8, 8, 8);

        ImageView imageView = new ImageView(this);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER); // Full image no cropping
        imageView.setAdjustViewBounds(true); // Keep proportions
        Glide.with(this).load(imageUrl).into(imageView); // Load from Cloudinary URL (not local URI)
        imageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                600 // Fixed height
        ));
        layout.addView(imageView);

        new AlertDialog.Builder(this)
                .setTitle("Image Options") // Title so admin knows what the buttons do
                .setView(layout)
                .setPositiveButton("🔄 Change", (dialog, which) -> {
                    // Admin wants to replace the image - open picker
                    showImagePickerDialog();
                })
                .setNeutralButton("🗑️ Delete", (dialog, which) -> {
                    // Admin wants to remove the existing image
                    selectedImageUri = null; // Clear any selected URI
                    if (currentDialogImagePreview != null) {
                        currentDialogImagePreview.setVisibility(View.GONE); // Hide preview in dialog
                        currentDialogImagePreview.setOnClickListener(null); // Remove click listener
                    }
                    if (currentDialogAddImageButton != null) {
                        currentDialogAddImageButton.setVisibility(View.VISIBLE); // Show Add Image button
                    }
                })
                .setNegativeButton("❌ Cancel", (dialog, which) -> {
                    dialog.dismiss(); // Just close the popup - keep existing image unchanged
                })
                .show();
    }

    // ==================== CREATE TEMP URI FOR CAMERA ====================

    private Uri createTempImageUri() {
        // Creates a temporary file on disk that the camera app will save the photo into
        // We need this because camera apps can't save directly into our app - they need a file path
        // System.currentTimeMillis() makes the filename unique so we don't overwrite old photos
        java.io.File photoFile = new java.io.File(getCacheDir(), // getCacheDir() = app's private temp folder
                "temp_photo_" + System.currentTimeMillis() + ".jpg"); // Unique filename
        // FileProvider converts our file path into a secure URI that the camera app is allowed to write to
        // Without FileProvider the camera app would be blocked from saving the photo (security restriction)
        return androidx.core.content.FileProvider.getUriForFile(this,
                getPackageName() + ".provider", // Must match the authority in AndroidManifest.xml
                photoFile);
    }

    // ==================== ADD CATEGORY DIALOG ====================

    private void showAddCategoryDialog() {
        selectedImageUri = null; // Reset any previously selected image

        // Inflate means read the XML file and build the View from it
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category, null);

        // Connect variables to the views inside the dialog layout
        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etCategoryNameHebrew = dialogView.findViewById(R.id.etCategoryNameHebrew);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);
        Button btnAddImage = dialogView.findViewById(R.id.btnAddImage);

        // Save references so showImageConfirmationDialog can update them after image is picked
        currentDialogImagePreview = imgPreview;
        currentDialogAddImageButton = btnAddImage;

        btnAddImage.setOnClickListener(v -> showImagePickerDialog()); // Open picker when Add Image is clicked

        // Build the dialog - setPositiveButton null means we handle the click manually below
        // We do this so the dialog stays open when there are validation errors
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add New Category")
                .setView(dialogView)
                .setPositiveButton("Add", null) // null = don't auto-dismiss on click
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show(); // Display the dialog

        // Override the Add button click AFTER showing so we can prevent auto-dismiss on error
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim(); // Read and trim the English name
            String nameHebrew = etCategoryNameHebrew.getText().toString().trim(); // Read and trim the Hebrew name
            boolean hasError = false; // Track if any validation failed

            // Validate English name
            if (name.isEmpty()) {
                etCategoryName.setError("Please enter the category name in English");
                hasError = true;
            } else if (!name.matches("[a-zA-Z ]+")) { // Only letters and spaces allowed
                etCategoryName.setError("Category name must be in English only");
                hasError = true;
            }

            // Validate Hebrew name
            if (nameHebrew.isEmpty()) {
                etCategoryNameHebrew.setError("Please enter the category name in Hebrew");
                hasError = true;
            } else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { // \u0590-\u05FF = Hebrew unicode range
                etCategoryNameHebrew.setError("Category name must be in Hebrew only");
                hasError = true;
            }

            // Validate image was selected
            if (selectedImageUri == null) {
                Toast.makeText(this, "Please add an image", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return; // Stop here - don't submit if there are errors

            uploadImageAndSaveCategory(name, nameHebrew, null, dialog); // null = no existing ID, this is a new category
        });
    }

    // ==================== EDIT CATEGORY DIALOG ====================

    private void showEditCategoryDialog(Category category) {
        selectedImageUri = null; // Reset any previously selected image

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etCategoryName = dialogView.findViewById(R.id.etCategoryName);
        EditText etCategoryNameHebrew = dialogView.findViewById(R.id.etCategoryNameHebrew);
        ImageView imgPreview = dialogView.findViewById(R.id.imgPreview);
        Button btnAddImage = dialogView.findViewById(R.id.btnAddImage);

        currentDialogImagePreview = imgPreview;
        currentDialogAddImageButton = btnAddImage;

        // Pre-fill fields with existing category data so admin can see current values
        etCategoryName.setText(category.getCategoryName()); // Show current English name
        etCategoryNameHebrew.setText(category.getCategoryNameHebrew()); // Show current Hebrew name

        // Show existing image and hide the Add Image button since image already exists
        imgPreview.setVisibility(View.VISIBLE); // Make the preview visible
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); // Full image no cropping
        imgPreview.setAdjustViewBounds(true); // Keep image proportions
        Glide.with(this).load(category.getImage()).into(imgPreview); // Load existing image from Cloudinary URL
        btnAddImage.setVisibility(View.GONE); // Hide Add Image button - image already exists

        // Clicking the existing image opens showExistingImageOptionsDialog
        // This is the Firestore image (already saved) so we pass the URL string, not a URI
        imgPreview.setOnClickListener(v -> showExistingImageOptionsDialog(category.getImage()));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Edit Category")
                .setView(dialogView)
                .setPositiveButton("Save", null) // null = handle click manually to prevent auto-dismiss
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etCategoryName.getText().toString().trim();
            String nameHebrew = etCategoryNameHebrew.getText().toString().trim();
            boolean hasError = false;

            // Validate English name
            if (name.isEmpty()) {
                etCategoryName.setError("Please enter the category name in English");
                hasError = true;
            } else if (!name.matches("[a-zA-Z ]+")) {
                etCategoryName.setError("Category name must be in English only");
                hasError = true;
            }

            // Validate Hebrew name
            if (nameHebrew.isEmpty()) {
                etCategoryNameHebrew.setError("Please enter the category name in Hebrew");
                hasError = true;
            } else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) {
                etCategoryNameHebrew.setError("Category name must be in Hebrew only");
                hasError = true;
            }

            if (hasError) return; // Stop if there are errors

            if (selectedImageUri != null) { // Admin picked a new image - upload it then update Firestore
                uploadImageAndSaveCategory(name, nameHebrew, category.getIdFS(), dialog);
            } else { // No new image - keep the existing Cloudinary URL
                updateCategoryInFirestore(category.getIdFS(), name, nameHebrew, category.getImage());
                dialog.dismiss(); // Close the dialog manually since we didn't upload
            }
        });
    }

    // ==================== UPLOAD IMAGE AND SAVE ====================

    private void uploadImageAndSaveCategory(String name, String nameHebrew, String categoryId, AlertDialog dialog) {
        Toast.makeText(this, "Uploading image...", Toast.LENGTH_SHORT).show();

        MediaManager.get()
                .upload(selectedImageUri) // Upload the image URI to Cloudinary
                .option("upload_preset", "EnglishKingdom") // Use our unsigned upload preset
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {} // Upload started - nothing to do

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {} // Could show progress bar here

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        // Upload finished successfully
                        // resultData is a Map containing all details about the uploaded file
                        String imageUrl = (String) resultData.get("secure_url"); // Get the HTTPS URL of the uploaded image
                        if (categoryId == null) { // null categoryId means this is a new category
                            addCategoryToFirestore(name, nameHebrew, imageUrl);
                        } else { // Non-null categoryId means we're updating an existing category
                            updateCategoryInFirestore(categoryId, name, nameHebrew, imageUrl);
                        }
                        dialog.dismiss(); // Close the Add/Edit dialog
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        // Upload failed - show the error message
                        Toast.makeText(LearnActivity.this, "Upload failed: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {} // Upload was rescheduled - nothing to do
                })
                .dispatch(); // Start the upload
    }

    // ==================== FIRESTORE OPERATIONS ====================

    private void addCategoryToFirestore(String name, String nameHebrew, String imageUrl) {
        // Creates a new Category object and saves it as a new document in the "categories" collection
        Category category = new Category(null, name, nameHebrew, imageUrl, 0); // 0 = no words yet
        db.collection("categories").add(category) // add() auto-generates a document ID
                .addOnSuccessListener(ref -> Toast.makeText(this, "Category added! 🎉", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error adding category", Toast.LENGTH_SHORT).show());
    }

    private void updateCategoryInFirestore(String categoryId, String name, String nameHebrew, String imageUrl) {
        // Updates only the 3 specified fields of an existing category document
        // update() only changes the fields listed - other fields like wordCount stay the same
        db.collection("categories").document(categoryId) // Find the specific document by ID
                .update("categoryName", name, "categoryNameHebrew", nameHebrew, "image", imageUrl)
                .addOnSuccessListener(v -> Toast.makeText(this, "Category updated! ✅", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating category", Toast.LENGTH_SHORT).show());
    }

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
                .addOnSuccessListener(v -> Toast.makeText(this, "Category deleted! 🗑️", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show());
    }

    // ==================== CLICK LISTENER IMPLEMENTATIONS ====================

    @Override
    public void onCategoryClick(Category category) { // Called by CategoryAdapter when a card is tapped
        if (isEditMode) {
            showEditCategoryDialog(category); // Edit mode → open edit dialog
        } else {
            // TODO: Navigate to Words screen when normal user taps a category
        }
    }

    @Override
    public void onCategoryLongClick(Category category) { // Called by CategoryAdapter when a card is long pressed
        if (isEditMode) {
            showDeleteConfirmationDialog(category); // Edit mode → show delete confirmation
        }
    }
}
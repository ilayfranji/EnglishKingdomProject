package com.ilay.englishkingdom.Activities;

import android.net.Uri; // Used to store the camera photo file path
import android.os.Bundle; // Used when creating the activity and saving state
import android.view.View; // Used to show and hide UI elements
import android.widget.TextView; // Used for the back button, edit button, banner, category name
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery/camera and get result back
import androidx.activity.result.contract.ActivityResultContracts; // Provides contracts for gallery and camera
import androidx.appcompat.app.AlertDialog; // Used to show the delete confirmation popup
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.recyclerview.widget.LinearLayoutManager; // Arranges word cards in a single vertical list
import androidx.recyclerview.widget.RecyclerView; // The scrollable list of word cards

import com.google.android.material.floatingactionbutton.FloatingActionButton; // The round + and X buttons
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read/write words from our database
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single word document from Firestore
import com.ilay.englishkingdom.Activities.Dialogs.AddWordDialog; // Handles the Add Word flow
import com.ilay.englishkingdom.Activities.Dialogs.EditWordDialog; // Handles the Edit Word flow
import com.ilay.englishkingdom.Activities.Dialogs.FlashcardDialog; // Shows the flashcard popup
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery/permissions
import com.ilay.englishkingdom.Adapters.WordAdapter; // Connects our word list to the RecyclerView
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to create the word list
import java.util.List; // The List interface for our word list

public class WordsActivity extends AppCompatActivity implements WordAdapter.OnWordClickListener {

    // ==================== UI ELEMENTS ====================

    private RecyclerView recyclerWords; // The scrollable list of word cards
    private FloatingActionButton fabWord; // The + button shown in edit mode to add words
    private FloatingActionButton fabExitEditMode; // The X button to exit edit mode
    private TextView tvBack; // Back arrow to go back to LearnActivity
    private TextView tvEditMode; // Pencil button shown only to admins
    private TextView tvEditBanner; // Red banner shown at top when in edit mode
    private TextView tvCategoryName; // Shows the category name at the top of the screen

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Our connection to Firebase Authentication

    // ==================== ADAPTER AND DATA ====================

    private WordAdapter wordAdapter; // Connects our word list to the RecyclerView
    private List<Word> wordList; // The list of all words loaded from Firestore

    // ==================== STATE ====================

    private boolean isEditMode = false; // true = admin is in edit mode, false = normal mode
    private String categoryId; // The Firestore ID of the category we are showing words for

    // ==================== HELPERS AND DIALOGS ====================

    private ImagePickerHelper imagePicker; // Handles ALL camera/gallery/permission logic
    private AddWordDialog addWordDialog; // Handles the entire Add Word flow
    private EditWordDialog editWordDialog; // Handles the entire Edit Word flow
    private FlashcardDialog flashcardDialog; // Shows the flashcard when user taps a word card

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
        setContentView(R.layout.activity_words);

        // Get the categoryId and categoryName that LearnActivity passed via Intent
        categoryId = getIntent().getStringExtra("categoryId");
        String categoryName = getIntent().getStringExtra("categoryName");

        // If categoryId is missing something went wrong - close immediately
        if (categoryId == null) {
            Toast.makeText(this, "Error loading words", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Restore camera URI if Android killed and recreated the activity
        Uri restoredUri = null;
        if (savedInstanceState != null) {
            restoredUri = savedInstanceState.getParcelable(KEY_CAMERA_URI);
        }

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        recyclerWords = findViewById(R.id.recyclerWords);
        fabWord = findViewById(R.id.fabWord);
        fabExitEditMode = findViewById(R.id.fabExitEditMode);
        tvBack = findViewById(R.id.tvBack);
        tvEditMode = findViewById(R.id.tvEditMode);
        tvEditBanner = findViewById(R.id.tvEditBanner);
        tvCategoryName = findViewById(R.id.tvCategoryName);

        if (categoryName != null) tvCategoryName.setText(categoryName);

        wordList = new ArrayList<>();

        // LinearLayoutManager = single vertical list, one card per row
        recyclerWords.setLayoutManager(new LinearLayoutManager(this));
        wordAdapter = new WordAdapter(this, wordList, categoryId, this);
        recyclerWords.setAdapter(wordAdapter);

        checkIfAdmin();
        loadWords(); // Load words from Firestore in real time
        loadLearnedWords(); // Load learned words in real time so checkmarks stay updated

        // Create ImagePickerHelper - forward image to whichever dialog is open
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    addWordDialog.onImagePicked(uri);
                    editWordDialog.onImagePicked(uri);
                },
                galleryLauncher,
                cameraLauncher);

        if (restoredUri != null) imagePicker.setPendingCameraUri(restoredUri);

        addWordDialog = new AddWordDialog(this, imagePicker, categoryId, () -> {});
        editWordDialog = new EditWordDialog(this, imagePicker, categoryId, () -> {});

        // FlashcardDialog doesn't need a callback anymore because loadLearnedWords()
        // is already listening in real time - checkmarks update automatically
        flashcardDialog = new FlashcardDialog(this, categoryId, () -> {});

        tvBack.setOnClickListener(v -> finish());
        tvEditMode.setOnClickListener(v -> enterEditMode());
        fabWord.setOnClickListener(v -> addWordDialog.show());
        fabExitEditMode.setOnClickListener(v -> exitEditMode());
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
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== ADMIN CHECK ====================

    private void checkIfAdmin() {
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        String role = document.getString("role");
                        if (role != null && role.equals("ADMIN")) {
                            tvEditMode.setVisibility(View.VISIBLE); // Show pencil for admin only
                        }
                    }
                });
    }

    // ==================== LOAD WORDS ====================

    private void loadWords() {
        // Real time listener - updates automatically when words are added/edited/deleted
        db.collection("categories").document(categoryId)
                .collection("words")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        Toast.makeText(this, "Error loading words", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    wordList.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        Word word = doc.toObject(Word.class);
                        word.setIdFS(doc.getId());
                        wordList.add(word);
                    }
                    wordAdapter.notifyDataSetChanged(); // Refresh the list
                });
    }

    // ==================== LOAD LEARNED WORDS ====================

    private void loadLearnedWords() {
        // Only load for logged in users - guests have no progress
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // addSnapshotListener = real time listener
        // Every time the user taps I Know This or Still Learning in FlashcardDialog,
        // Firestore updates the progress document and this listener fires automatically
        // It then passes the updated list to the adapter so checkmarks refresh instantly
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) return; // Something went wrong - skip silently

                    // Read the learnedWords array - list of wordIds the user has learned
                    // If the document doesn't exist yet, use an empty list
                    List<String> learnedWords = new ArrayList<>();
                    if (document != null && document.exists()
                            && document.get("learnedWords") != null) {
                        // Firestore returns arrays as List<Object> so we cast each item to String
                        List<Object> raw = (List<Object>) document.get("learnedWords");
                        for (Object item : raw) {
                            learnedWords.add((String) item); // Add each wordId to our list
                        }
                    }

                    // Pass the list to the adapter
                    // The adapter uses it to show/hide the green checkmark on each card
                    wordAdapter.setLearnedWords(learnedWords);
                });
    }

    // ==================== EDIT MODE ====================

    private void enterEditMode() {
        isEditMode = true;
        tvEditBanner.setVisibility(View.VISIBLE);
        fabWord.setVisibility(View.VISIBLE);
        fabExitEditMode.setVisibility(View.VISIBLE);
        tvEditMode.setVisibility(View.GONE);
    }

    private void exitEditMode() {
        isEditMode = false;
        tvEditBanner.setVisibility(View.GONE);
        fabWord.setVisibility(View.GONE);
        fabExitEditMode.setVisibility(View.GONE);
        tvEditMode.setVisibility(View.VISIBLE);
    }

    // ==================== WORD CLICK LISTENERS ====================

    @Override
    public void onWordClick(Word word) {
        // Called by WordAdapter when a word card is tapped
        if (isEditMode) {
            editWordDialog.show(word); // Edit mode → open edit dialog
        } else {
            flashcardDialog.show(word); // Normal mode → show flashcard
        }
    }

    @Override
    public void onWordLongClick(Word word) {
        // Called by WordAdapter when a word card is long pressed
        if (isEditMode) {
            showDeleteConfirmationDialog(word); // Edit mode → show delete confirmation
        }
    }

    // ==================== DELETE ====================

    private void showDeleteConfirmationDialog(Word word) {
        // Shows a confirmation popup before deleting - prevents accidental deletion
        new AlertDialog.Builder(this)
                .setTitle("Delete Word")
                .setMessage("Are you sure you want to delete \"" + word.getWordEnglish() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteWord(word.getIdFS()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteWord(String wordId) {
        // Delete the word document from Firestore
        db.collection("categories").document(categoryId)
                .collection("words").document(wordId).delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Word deleted! 🗑️", Toast.LENGTH_SHORT).show();

                    // After deleting recount the words and update wordCount in the category
                    // This keeps the word count text and progress bar in LearnActivity accurate
                    db.collection("categories").document(categoryId)
                            .collection("words").get()
                            .addOnSuccessListener(snapshot -> {
                                int totalWords = snapshot.size(); // How many words are left
                                db.collection("categories").document(categoryId)
                                        .update("wordCount", totalWords); // Update the category
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Error deleting word", Toast.LENGTH_SHORT).show());
    }
}
package com.ilay.englishkingdom.Activities;

import android.net.Uri; // Used to store the camera photo file path
import android.os.Bundle; // Used when creating the activity and saving state
import android.view.View; // Used to show and hide UI elements
import android.widget.TextView; // Used for the back button, edit button, banner, category name
import android.widget.Toast; // Used to show short popup messages

import androidx.activity.result.ActivityResultLauncher; // Used to launch gallery/camera
import androidx.activity.result.contract.ActivityResultContracts; // Provides contracts for gallery and camera
import androidx.appcompat.app.AlertDialog; // Used to show the delete confirmation popup
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.recyclerview.widget.LinearLayoutManager; // Arranges word cards in a single vertical list
import androidx.recyclerview.widget.RecyclerView; // The scrollable list of word cards

import com.google.android.material.floatingactionbutton.FloatingActionButton; // The round + and X buttons
import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FieldValue; // Used to remove items from Firestore arrays
import com.google.firebase.firestore.FirebaseFirestore; // Used to read/write words from our database
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single word document
import com.ilay.englishkingdom.Activities.Dialogs.AddLetterDialog; // Handles adding a letter
import com.ilay.englishkingdom.Activities.Dialogs.AddSentenceDialog; // Handles adding a sentence
import com.ilay.englishkingdom.Activities.Dialogs.AddWordDialog; // Handles adding a word
import com.ilay.englishkingdom.Activities.Dialogs.EditLetterDialog; // Handles editing a letter
import com.ilay.englishkingdom.Activities.Dialogs.EditSentenceDialog; // Handles editing a sentence
import com.ilay.englishkingdom.Activities.Dialogs.EditWordDialog; // Handles editing a word
import com.ilay.englishkingdom.Activities.Dialogs.FlashcardDialog; // Shows the flashcard popup
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper; // Handles camera/gallery
import com.ilay.englishkingdom.Adapters.WordAdapter; // Connects our word list to the RecyclerView
import com.ilay.englishkingdom.Models.CategoryType; // Our CategoryType enum
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to create the word list
import java.util.List; // The List interface for our word list

public class WordsActivity extends AppCompatActivity implements WordAdapter.OnWordClickListener {

    // ==================== UI ELEMENTS ====================

    private RecyclerView recyclerWords; // The scrollable list of word cards
    private FloatingActionButton fabWord; // The + button shown in edit mode
    private FloatingActionButton fabExitEditMode; // The X button to exit edit mode
    private TextView tvBack; // Back arrow to go back to LearnActivity
    private TextView tvEditMode; // Pencil button shown only to admins
    private TextView tvEditBanner; // Red banner shown at top when in edit mode
    private TextView tvCategoryName; // Shows the category name at the top

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to Firestore
    private FirebaseAuth mAuth; // Our connection to Firebase Authentication

    // ==================== ADAPTER AND DATA ====================

    private WordAdapter wordAdapter; // Connects our word list to the RecyclerView
    private List<Word> wordList; // The list of all words loaded from Firestore

    // ==================== STATE ====================

    private boolean isEditMode = false; // true = admin is in edit mode
    private String categoryId; // The Firestore ID of the category we are showing

    // The type of this category - determines which dialog opens and which fields show
    // Passed from LearnActivity via Intent along with categoryId
    private CategoryType categoryType;

    // ==================== DIALOGS ====================

    // We create all 3 pairs of dialogs but only use the ones matching the category type
    // This keeps the code clean - WordsActivity doesn't need to know the details of each dialog
    private ImagePickerHelper imagePicker; // Handles camera/gallery - only used for WORDS type
    private AddWordDialog addWordDialog; // Add dialog for WORDS type
    private EditWordDialog editWordDialog; // Edit dialog for WORDS type
    private AddLetterDialog addLetterDialog; // Add dialog for LETTERS type
    private EditLetterDialog editLetterDialog; // Edit dialog for LETTERS type
    private AddSentenceDialog addSentenceDialog; // Add dialog for SENTENCES type
    private EditSentenceDialog editSentenceDialog; // Edit dialog for SENTENCES type
    private FlashcardDialog flashcardDialog; // Flashcard shown when user taps any item

    // ==================== SAVE/RESTORE STATE ====================

    private static final String KEY_CAMERA_URI = "camera_uri"; // Key to save camera URI

    // ==================== ACTIVITY RESULT LAUNCHERS ====================

    // These MUST live here in the Activity - passed into ImagePickerHelper
    // Only used when category type is WORDS because only words have images
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> { if (imagePicker != null) imagePicker.onGalleryResult(uri); });

    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> { if (imagePicker != null) imagePicker.onCameraResult(success); });

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_words);

        // Get the data passed from LearnActivity via Intent
        categoryId = getIntent().getStringExtra("categoryId"); // Which category to load
        String categoryName = getIntent().getStringExtra("categoryName"); // For display only
        String categoryTypeString = getIntent().getStringExtra("categoryType"); // "WORDS"/"LETTERS"/"SENTENCES"

        // Convert the String back to a CategoryType enum
        // If missing or unrecognized, default to WORDS so the screen still works
        try {
            categoryType = CategoryType.valueOf(categoryTypeString);
        } catch (Exception e) {
            categoryType = CategoryType.WORDS; // Default to WORDS if something goes wrong
        }

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
        recyclerWords.setLayoutManager(new LinearLayoutManager(this));

        // Pass the category type to the adapter so it knows which fields to show/hide
        wordAdapter = new WordAdapter(this, wordList, categoryId, categoryType, this);
        recyclerWords.setAdapter(wordAdapter);

        checkIfAdmin();
        loadWords();
        loadLearnedWords();

        // Only set up image picker for WORDS type - letters and sentences don't need it
        if (categoryType == CategoryType.WORDS) {
            imagePicker = new ImagePickerHelper(this,
                    (uri, fromGallery) -> {
                        // Forward image to whichever word dialog is currently open
                        if (addWordDialog != null) addWordDialog.onImagePicked(uri);
                        if (editWordDialog != null) editWordDialog.onImagePicked(uri);
                    },
                    galleryLauncher,
                    cameraLauncher);

            if (restoredUri != null) imagePicker.setPendingCameraUri(restoredUri);

            // Create word dialogs
            addWordDialog = new AddWordDialog(this, imagePicker, categoryId, () -> {});
            editWordDialog = new EditWordDialog(this, imagePicker, categoryId, () -> {});

        } else if (categoryType == CategoryType.LETTERS) {
            // Create letter dialogs - no image picker needed
            addLetterDialog = new AddLetterDialog(this, categoryId, () -> {});
            editLetterDialog = new EditLetterDialog(this, categoryId, () -> {});

        } else if (categoryType == CategoryType.SENTENCES) {
            // Create sentence dialogs - no image picker needed
            addSentenceDialog = new AddSentenceDialog(this, categoryId, () -> {});
            editSentenceDialog = new EditSentenceDialog(this, categoryId, () -> {});
        }

        // Flashcard dialog works for all types - no changes needed
        flashcardDialog = new FlashcardDialog(this, categoryId, wordList, () -> {});

        tvBack.setOnClickListener(v -> finish());
        tvEditMode.setOnClickListener(v -> enterEditMode());
        fabWord.setOnClickListener(v -> showAddDialog()); // Show the right add dialog based on type
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
        // Only forward to imagePicker if it exists (only exists for WORDS type)
        if (imagePicker != null) imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== SHOW RIGHT ADD DIALOG ====================

    private void showAddDialog() {
        // Opens the correct Add dialog based on the category type
        if (categoryType == CategoryType.WORDS) {
            addWordDialog.show(); // Regular word dialog with image
        } else if (categoryType == CategoryType.LETTERS) {
            addLetterDialog.show(); // Letter dialog - letter name + Hebrew explanation
        } else if (categoryType == CategoryType.SENTENCES) {
            addSentenceDialog.show(); // Sentence dialog - English + Hebrew only
        }
    }

    // ==================== SHOW RIGHT EDIT DIALOG ====================

    private void showEditDialog(Word word) {
        // Opens the correct Edit dialog based on the category type
        if (categoryType == CategoryType.WORDS) {
            editWordDialog.show(word); // Regular word edit dialog
        } else if (categoryType == CategoryType.LETTERS) {
            editLetterDialog.show(word); // Letter edit dialog
        } else if (categoryType == CategoryType.SENTENCES) {
            editSentenceDialog.show(word); // Sentence edit dialog
        }
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
                            tvEditMode.setVisibility(View.VISIBLE);
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
                    wordAdapter.notifyDataSetChanged();
                });
    }

    // ==================== LOAD LEARNED WORDS ====================

    private void loadLearnedWords() {
        if (mAuth.getCurrentUser() == null) return;
        String userId = mAuth.getCurrentUser().getUid();

        // Real time listener - fires every time the user marks an item as learned or not learned
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .addSnapshotListener((document, error) -> {
                    if (error != null) return;

                    List<String> learnedWords = new ArrayList<>();
                    if (document != null && document.exists()
                            && document.get("learnedWords") != null) {
                        List<Object> raw = (List<Object>) document.get("learnedWords");
                        for (Object item : raw) {
                            learnedWords.add((String) item);
                        }
                    }
                    // Pass updated list to adapter so checkmarks refresh instantly
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
        if (isEditMode) {
            showEditDialog(word); // Edit mode → open correct edit dialog
        } else {
            flashcardDialog.show(word); // Normal mode → show flashcard
        }
    }

    @Override
    public void onWordLongClick(Word word) {
        if (isEditMode) {
            showDeleteConfirmationDialog(word);
        }
    }

    // ==================== DELETE ====================

    private void showDeleteConfirmationDialog(Word word) {
        new AlertDialog.Builder(this)
                .setTitle("Delete")
                .setMessage("Are you sure you want to delete \"" + word.getWordEnglish() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteWord(word.getIdFS()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteWord(String wordId) {
        // Step 1 - delete the word/letter/sentence document from Firestore
        db.collection("categories").document(categoryId)
                .collection("words").document(wordId).delete()
                .addOnSuccessListener(v -> {
                    Toast.makeText(this, "Deleted! 🗑️", Toast.LENGTH_SHORT).show();

                    // Step 2 - recount remaining items and update wordCount in the category
                    db.collection("categories").document(categoryId)
                            .collection("words").get()
                            .addOnSuccessListener(snapshot -> {
                                db.collection("categories").document(categoryId)
                                        .update("wordCount", snapshot.size());
                            });

                    // Step 3 - remove this wordId from ALL users' learnedWords arrays
                    // so their progress doesn't count a deleted item
                    db.collection("users").get()
                            .addOnSuccessListener(usersSnapshot -> {
                                for (QueryDocumentSnapshot userDoc : usersSnapshot) {
                                    String userId = userDoc.getId();
                                    db.collection("users").document(userId)
                                            .collection("progress").document(categoryId)
                                            .get()
                                            .addOnSuccessListener(progressDoc -> {
                                                if (!progressDoc.exists()) return;

                                                List<String> learnedWords = new ArrayList<>();
                                                if (progressDoc.get("learnedWords") != null) {
                                                    List<Object> raw = (List<Object>) progressDoc.get("learnedWords");
                                                    for (Object item : raw) learnedWords.add((String) item);
                                                }

                                                // Only update users who had learned this specific item
                                                if (learnedWords.contains(wordId)) {
                                                    db.collection("users").document(userId)
                                                            .collection("progress").document(categoryId)
                                                            .update(
                                                                    "learnedWords", FieldValue.arrayRemove(wordId),
                                                                    "wordsLearned", learnedWords.size() - 1
                                                            );
                                                }
                                            });
                                }
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this,
                        "Error deleting", Toast.LENGTH_SHORT).show());
    }
}
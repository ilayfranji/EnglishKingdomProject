package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

public class AddLetterDialog {
    // This dialog handles adding a new letter to the Letters category
    // Letters only have a letter name (e.g. A-a) and Hebrew explanation (e.g. אלף)
    // No image, no example sentence - much simpler than AddWordDialog

    public interface OnLetterAddedListener {
        void onLetterAdded(); // Called after letter is successfully saved to Firestore
    }

    private final Activity activity; // Needed to inflate views and show toasts
    private final OnLetterAddedListener listener; // Who to notify when letter is saved
    private final FirebaseFirestore db; // Our database connection
    private final String categoryId; // The Letters category ID - where to save the letter
    private AlertDialog openDialog; // Reference to dialog so we can dismiss it after saving

    public AddLetterDialog(Activity activity, String categoryId, OnLetterAddedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show() {
        // Inflate = read dialog_letter.xml and build the View from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_letter, null);
        EditText etLetterName = view.findViewById(R.id.etLetterName); // e.g. A-a
        EditText etLetterExplanation = view.findViewById(R.id.etLetterExplanation); // e.g. אלף

        // null for positive button = handle click manually so dialog stays open on errors
        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Add New Letter")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String letterName = etLetterName.getText().toString().trim();
            String explanation = etLetterExplanation.getText().toString().trim();
            boolean hasError = false;

            // Validate letter name - must follow format like A-a or B-b
            if (letterName.isEmpty()) {
                etLetterName.setError("Required");
                hasError = true;
            } else if (!letterName.matches("[A-Z]-[a-z]")) {
                // Regex [A-Z]-[a-z] means: one capital letter, a dash, one lowercase letter
                // This enforces the A-a format strictly
                etLetterName.setError("Format must be A-a");
                hasError = true;
            }

            // Validate Hebrew explanation
            if (explanation.isEmpty()) {
                etLetterExplanation.setError("Required");
                hasError = true;
            } else if (!explanation.matches("[\\u0590-\\u05FF ]+")) {
                // \u0590-\u05FF is the Hebrew unicode range
                etLetterExplanation.setError("Hebrew only");
                hasError = true;
            }

            if (hasError) return; // Stop here - keep dialog open so admin can fix errors

            // Check if this letter already exists in the Letters category
            db.collection("categories").document(categoryId)
                    .collection("words")
                    .whereEqualTo("wordEnglish", letterName) // We store letter name in wordEnglish field
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            // Letter already exists - show error
                            etLetterName.setError("This letter already exists");
                            Toast.makeText(activity, "Letter already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            // No duplicate - save to Firestore
                            saveToFirestore(letterName, explanation);
                        }
                    });
        });
    }

    private void saveToFirestore(String letterName, String explanation) {
        // We reuse the Word model to store letters
        // wordEnglish = letter name (A-a)
        // wordHebrew = Hebrew explanation (אלף)
        // image = empty string because letters have no image
        // exampleSentence = empty string because letters have no example sentence
        Word letter = new Word(null, letterName, explanation, "", "");

        // Save under categories/[categoryId]/words/ - same path as regular words
        db.collection("categories").document(categoryId)
                .collection("words").add(letter)
                .addOnSuccessListener(r -> {
                    // After saving, update the wordCount in the category document
                    db.collection("categories").document(categoryId)
                            .collection("words").get()
                            .addOnSuccessListener(snapshot -> {
                                int totalWords = snapshot.size(); // Count all letters
                                db.collection("categories").document(categoryId)
                                        .update("wordCount", totalWords)
                                        .addOnSuccessListener(v -> {
                                            Toast.makeText(activity, "Letter added! 🎉", Toast.LENGTH_SHORT).show();
                                            if (openDialog != null) openDialog.dismiss();
                                            if (listener != null) listener.onLetterAdded();
                                        });
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving letter", Toast.LENGTH_SHORT).show());
    }
}
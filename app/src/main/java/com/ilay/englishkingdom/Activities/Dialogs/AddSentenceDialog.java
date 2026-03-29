package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

public class AddSentenceDialog {
    // This dialog handles adding a new sentence to the Sentences category
    // Sentences only have an English sentence and Hebrew translation
    // No image, no example sentence - simpler than AddWordDialog

    public interface OnSentenceAddedListener {
        void onSentenceAdded(); // Called after sentence is successfully saved to Firestore
    }

    private final Activity activity;
    private final OnSentenceAddedListener listener;
    private final FirebaseFirestore db;
    private final String categoryId;
    private AlertDialog openDialog;

    public AddSentenceDialog(Activity activity, String categoryId, OnSentenceAddedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show() {
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_sentence, null);
        EditText etSentenceEnglish = view.findViewById(R.id.etSentenceEnglish);
        EditText etSentenceHebrew = view.findViewById(R.id.etSentenceHebrew);

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Add New Sentence")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String sentenceEnglish = etSentenceEnglish.getText().toString().trim();
            String sentenceHebrew = etSentenceHebrew.getText().toString().trim();
            boolean hasError = false;

            // Validate English sentence
            if (sentenceEnglish.isEmpty()) {
                etSentenceEnglish.setError("Required");
                hasError = true;
            } else if (!sentenceEnglish.matches("[a-zA-Z0-9 .,!?']+")) {
                // Allow letters, numbers, spaces and common punctuation
                etSentenceEnglish.setError("English only");
                hasError = true;
            } else if (!Character.isUpperCase(sentenceEnglish.charAt(0))) {
                // Sentence must start with a capital letter
                etSentenceEnglish.setError("Must start with a capital letter");
                hasError = true;
            }

            // Validate Hebrew translation
            if (sentenceHebrew.isEmpty()) {
                etSentenceHebrew.setError("Required");
                hasError = true;
            } else if (!sentenceHebrew.matches("[\\u0590-\\u05FF .,!?']+")) {
                // Allow Hebrew letters and common punctuation
                etSentenceHebrew.setError("Hebrew only");
                hasError = true;
            }

            if (hasError) return;

            // Check if this sentence already exists in this category
            db.collection("categories").document(categoryId)
                    .collection("words")
                    .whereEqualTo("wordEnglish", sentenceEnglish) // We store sentence in wordEnglish field
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            etSentenceEnglish.setError("This sentence already exists");
                            Toast.makeText(activity, "Sentence already exists!", Toast.LENGTH_SHORT).show();
                        } else {
                            saveToFirestore(sentenceEnglish, sentenceHebrew);
                        }
                    });
        });
    }

    private void saveToFirestore(String sentenceEnglish, String sentenceHebrew) {
        // We reuse the Word model to store sentences
        // wordEnglish = English sentence
        // wordHebrew = Hebrew translation
        // image = empty string because sentences have no image
        // exampleSentence = empty string because sentences have no example sentence
        Word sentence = new Word(null, sentenceEnglish, sentenceHebrew, "", "");

        db.collection("categories").document(categoryId)
                .collection("words").add(sentence)
                .addOnSuccessListener(r -> {
                    // After saving update the wordCount in the category document
                    db.collection("categories").document(categoryId)
                            .collection("words").get()
                            .addOnSuccessListener(snapshot -> {
                                int totalWords = snapshot.size();
                                db.collection("categories").document(categoryId)
                                        .update("wordCount", totalWords)
                                        .addOnSuccessListener(v -> {
                                            Toast.makeText(activity, "Sentence added!", Toast.LENGTH_SHORT).show();
                                            if (openDialog != null) openDialog.dismiss();
                                            if (listener != null) listener.onSentenceAdded();
                                        });
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving sentence", Toast.LENGTH_SHORT).show());
    }
}
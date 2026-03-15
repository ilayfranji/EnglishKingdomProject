package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

public class EditSentenceDialog {
    // This dialog handles editing an existing sentence in the Sentences category
    // Same as AddSentenceDialog but pre-fills the existing data

    public interface OnSentenceEditedListener {
        void onSentenceEdited(); // Called after sentence is successfully updated in Firestore
    }

    private final Activity activity;
    private final OnSentenceEditedListener listener;
    private final FirebaseFirestore db;
    private final String categoryId;
    private AlertDialog openDialog;
    private Word currentSentence; // The sentence being edited - we need its ID

    public EditSentenceDialog(Activity activity, String categoryId, OnSentenceEditedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word sentence) {
        this.currentSentence = sentence; // Save reference - needed in update call

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_sentence, null);
        EditText etSentenceEnglish = view.findViewById(R.id.etSentenceEnglish);
        EditText etSentenceHebrew = view.findViewById(R.id.etSentenceHebrew);

        // Pre-fill with existing sentence data
        etSentenceEnglish.setText(sentence.getWordEnglish()); // wordEnglish stores the English sentence
        etSentenceHebrew.setText(sentence.getWordHebrew()); // wordHebrew stores the Hebrew translation

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Sentence")
                .setView(view)
                .setPositiveButton("Save", null)
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
                etSentenceEnglish.setError("English only");
                hasError = true;
            } else if (!Character.isUpperCase(sentenceEnglish.charAt(0))) {
                etSentenceEnglish.setError("Must start with a capital letter");
                hasError = true;
            }

            // Validate Hebrew translation
            if (sentenceHebrew.isEmpty()) {
                etSentenceHebrew.setError("Required");
                hasError = true;
            } else if (!sentenceHebrew.matches("[\\u0590-\\u05FF .,!?']+")) {
                etSentenceHebrew.setError("Hebrew only");
                hasError = true;
            }

            if (hasError) return;

            // Update only the two fields that letters have
            db.collection("categories").document(categoryId)
                    .collection("words").document(currentSentence.getIdFS())
                    .update("wordEnglish", sentenceEnglish, "wordHebrew", sentenceHebrew)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(activity, "Sentence updated! ✅", Toast.LENGTH_SHORT).show();
                        if (openDialog != null) openDialog.dismiss();
                        if (listener != null) listener.onSentenceEdited();
                    })
                    .addOnFailureListener(e -> Toast.makeText(activity, "Error updating sentence", Toast.LENGTH_SHORT).show());
        });
    }
}
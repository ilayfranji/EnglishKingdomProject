package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.HashMap;

public class FlashcardDialog {
    // This class shows a flashcard popup when a user taps a word card
    // Front: image + English + Hebrew + example sentence
    // Back: two buttons - I know this / Still learning
    // After tapping a button it updates the user's progress in Firestore

    public interface OnStatusChangedListener {
        // Called after the user taps either button so WordsActivity can refresh the list
        void onStatusChanged();
    }

    private final Activity activity; // Needed to inflate the dialog view
    private final FirebaseFirestore db; // Our database connection
    private final String categoryId; // Which category this word belongs to
    private final OnStatusChangedListener listener; // Who to notify after status changes

    public FlashcardDialog(Activity activity, String categoryId, OnStatusChangedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word word) {
        // Inflate = read the XML layout file and build the View from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_flashcard, null);

        // Connect variables to views inside the dialog layout
        ImageView imgWord = view.findViewById(R.id.imgFlashcardWord); // The word image
        TextView tvWordEnglish = view.findViewById(R.id.tvFlashcardEnglish); // English word
        TextView tvWordHebrew = view.findViewById(R.id.tvFlashcardHebrew); // Hebrew translation
        TextView tvExampleSentence = view.findViewById(R.id.tvFlashcardExample); // Example sentence
        Button btnKnow = view.findViewById(R.id.btnKnow); // ✅ I know this button
        Button btnStillLearning = view.findViewById(R.id.btnStillLearning); // ❌ Still learning button

        // Fill the views with the word's data
        tvWordEnglish.setText(word.getWordEnglish()); // Set English word
        tvWordHebrew.setText(word.getWordHebrew()); // Set Hebrew translation
        tvExampleSentence.setText(word.getExampleSentence()); // Set example sentence

        // Load word image from Cloudinary URL using Glide
        Glide.with(activity).load(word.getImage()).into(imgWord);

        // Build the dialog - no built in buttons, we use our own buttons inside the layout
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .create();

        // ✅ I know this button - marks the word as learned
        btnKnow.setOnClickListener(v -> {
            markWord(word.getIdFS(), true); // true = learned
            dialog.dismiss(); // Close the flashcard
        });

        // ❌ Still learning button - marks the word as not learned
        btnStillLearning.setOnClickListener(v -> {
            markWord(word.getIdFS(), false); // false = not learned
            dialog.dismiss(); // Close the flashcard
        });

        dialog.show();
    }

    private void markWord(String wordId, boolean learned) {
        // Only save progress for logged in users
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // First read the current progress document to get the learnedWords array
        // Path: users/[userId]/progress/[categoryId]
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .get()
                .addOnSuccessListener(document -> {

                    // Read the current learnedWords array - list of wordIds the user has learned
                    // If document doesn't exist yet, start with an empty list
                    java.util.List<String> learnedWords = new java.util.ArrayList<>();
                    if (document.exists() && document.get("learnedWords") != null) {
                        // Firestore returns arrays as List<Object> so we cast each item to String
                        java.util.List<Object> raw = (java.util.List<Object>) document.get("learnedWords");
                        for (Object item : raw) {
                            learnedWords.add((String) item); // Add each wordId to our list
                        }
                    }

                    if (learned) {
                        // User tapped ✅ - add this wordId to learned list if not already there
                        // We check first to avoid counting the same word twice
                        if (!learnedWords.contains(wordId)) {
                            learnedWords.add(wordId); // Add wordId to the learned list
                        }
                        Toast.makeText(activity, "Great job! ⭐", Toast.LENGTH_SHORT).show();
                    } else {
                        // User tapped ❌ - remove this wordId from learned list if it's there
                        learnedWords.remove(wordId); // Remove wordId from the learned list
                        Toast.makeText(activity, "Keep practicing! 💪", Toast.LENGTH_SHORT).show();
                    }

                    // Build the updated progress data to save back to Firestore
                    HashMap<String, Object> progress = new HashMap<>();
                    progress.put("learnedWords", learnedWords); // The updated list of learned wordIds
                    progress.put("wordsLearned", learnedWords.size()); // Count = size of the list

                    // SetOptions.merge() updates only these fields without deleting other fields
                    db.collection("users").document(userId)
                            .collection("progress").document(categoryId)
                            .set(progress, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                // Notify WordsActivity to refresh the list so checkmarks update
                                if (listener != null) listener.onStatusChanged();
                            });
                });
    }
}
package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity; // Needed to inflate the dialog view
import android.view.Gravity; // Used to center the guest message text
import android.view.View; // Used to build the dialog layout
import android.widget.Button; // Used for the I Know This and Still Learning buttons
import android.widget.ImageView; // Used to show the word image
import android.widget.LinearLayout; // Used to add the guest message into the dialog
import android.widget.TextView; // Used for English, Hebrew, example sentence and guest message
import android.widget.Toast; // Used to show short messages after marking a word

import androidx.appcompat.app.AlertDialog; // Used to build and show the popup dialog

import com.bumptech.glide.Glide; // Used to load the word image from Cloudinary URL
import com.google.firebase.auth.FirebaseAuth; // Used to check if user is logged in
import com.google.firebase.firestore.FirebaseFirestore; // Used to save learned word progress
import com.google.firebase.firestore.SetOptions; // Used to merge progress without deleting other fields
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to build the learnedWords list
import java.util.HashMap; // Used to build the progress data map
import java.util.List; // The List interface

public class FlashcardDialog {
    // This class shows a flashcard popup when a user taps a word card
    // Shows: image + English word + Hebrew translation + example sentence
    // Logged in users see: ✅ I Know This and ❌ Still Learning buttons
    // Guests see: a message saying they need to log in to track progress

    public interface OnStatusChangedListener {
        void onStatusChanged(); // Called after user taps either button - used to refresh word list
    }

    private final Activity activity; // Needed to inflate the dialog view and show toasts
    private final FirebaseFirestore db; // Our database connection
    private final String categoryId; // Which category this word belongs to - used when saving progress
    private final List<Word> wordList; // The full list of words in this category
    // wordList is needed so we can save totalWords correctly when marking progress
    // Without this totalWords would always be saved as 0
    private final OnStatusChangedListener listener; // Who to notify after status changes

    public FlashcardDialog(Activity activity, String categoryId,
                           List<Word> wordList, OnStatusChangedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.wordList = wordList; // Save reference - used in markWord() to get total word count
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word word) {
        // Inflate = read dialog_flashcard.xml and build the View from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_flashcard, null);

        // Connect variables to views inside the dialog layout
        ImageView imgWord = view.findViewById(R.id.imgFlashcardWord);
        TextView tvWordEnglish = view.findViewById(R.id.tvFlashcardEnglish);
        TextView tvWordHebrew = view.findViewById(R.id.tvFlashcardHebrew);
        TextView tvExampleSentence = view.findViewById(R.id.tvFlashcardExample);
        Button btnKnow = view.findViewById(R.id.btnKnow);
        Button btnStillLearning = view.findViewById(R.id.btnStillLearning);

        // Fill views with the word's data
        tvWordEnglish.setText(word.getWordEnglish());
        tvWordHebrew.setText(word.getWordHebrew());
        tvExampleSentence.setText(word.getExampleSentence());
        Glide.with(activity).load(word.getImage()).into(imgWord); // Load image from Cloudinary URL

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .create();

        // Check if the user is a guest - guests cannot mark words as learned
        boolean isGuest = FirebaseAuth.getInstance().getCurrentUser() == null;

        if (isGuest) {
            // Guest user - hide both buttons so they can't mark words
            btnKnow.setVisibility(View.GONE);
            btnStillLearning.setVisibility(View.GONE);

            // Show a message explaining why the buttons are hidden
            // We create the TextView programmatically and add it to the button's parent layout
            TextView tvGuestNote = new TextView(activity);
            tvGuestNote.setText("👤 Register a free account to track your progress!"); // Message text
            tvGuestNote.setTextColor(android.graphics.Color.parseColor("#B0BEC5")); // Grey text color
            tvGuestNote.setTextSize(13); // Small text size
            tvGuestNote.setGravity(Gravity.CENTER); // Center the text horizontally
            tvGuestNote.setPadding(16, 16, 16, 16); // Padding around the message

            // btnKnow.getParent() gives us the LinearLayout that contains both buttons
            // We add our message TextView into that same layout so it appears where the buttons were
            ((LinearLayout) btnKnow.getParent()).addView(tvGuestNote);

        } else {
            // Logged in user - show buttons normally so they can mark words

            // ✅ I Know This - marks this word as learned for this user
            btnKnow.setOnClickListener(v -> {
                markWord(word.getIdFS(), true); // true = learned
                dialog.dismiss(); // Close the flashcard
            });

            // ❌ Still Learning - removes this word from learned list if it was there
            btnStillLearning.setOnClickListener(v -> {
                markWord(word.getIdFS(), false); // false = not learned
                dialog.dismiss(); // Close the flashcard
            });
        }

        dialog.show(); // Display the flashcard dialog
    }

    private void markWord(String wordId, boolean learned) {
        // Saves or removes this word from the user's learned words list in Firestore
        // This method is only called for logged in users - guests never reach this code
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return; // Extra safety check

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Read the current progress document for this category first
        // We need to read it so we can add or remove the wordId from the existing array
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .get()
                .addOnSuccessListener(document -> {

                    // Build the current learnedWords list from what's already saved
                    // Start empty if this is the first time saving progress for this category
                    List<String> learnedWords = new ArrayList<>();
                    if (document.exists() && document.get("learnedWords") != null) {
                        // Firestore returns arrays as List<Object> so we cast each item to String
                        List<Object> raw = (List<Object>) document.get("learnedWords");
                        for (Object item : raw) {
                            learnedWords.add((String) item); // Add each wordId to our list
                        }
                    }

                    if (learned) {
                        // User tapped ✅ I Know This - add wordId to learned list
                        // We check first so we don't count the same word twice
                        if (!learnedWords.contains(wordId)) {
                            learnedWords.add(wordId);
                        }
                        Toast.makeText(activity, "Great job! ⭐", Toast.LENGTH_SHORT).show();
                    } else {
                        // User tapped ❌ Still Learning - remove wordId from learned list
                        learnedWords.remove(wordId);
                        Toast.makeText(activity, "Keep practicing! 💪", Toast.LENGTH_SHORT).show();
                    }

                    // Build the updated progress data to save back to Firestore
                    HashMap<String, Object> progress = new HashMap<>();
                    progress.put("learnedWords", learnedWords); // Updated array of learned wordIds
                    progress.put("wordsLearned", learnedWords.size()); // Total count of learned words
                    progress.put("totalWords", wordList.size()); // Total words available in this category

                    // SetOptions.merge() = only update these specific fields
                    // Without merge() Firestore would delete all other fields in the document
                    db.collection("users").document(userId)
                            .collection("progress").document(categoryId)
                            .set(progress, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                if (listener != null) listener.onStatusChanged(); // Notify WordsActivity
                            });
                });
    }
}
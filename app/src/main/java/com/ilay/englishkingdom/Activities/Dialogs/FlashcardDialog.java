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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FlashcardDialog {
    // This class shows a flashcard popup when a user taps a word card
    // Front: image + English + Hebrew + example sentence
    // Back: two buttons - I know this / Still learning

    public interface OnStatusChangedListener {
        void onStatusChanged(); // Called after user taps either button
    }

    private final Activity activity; // Needed to inflate the dialog view
    private final FirebaseFirestore db; // Our database connection
    private final String categoryId; // Which category this word belongs to
    private final List<Word> wordList; // The full list of words in this category
    // We need wordList to know the total number of words when saving progress
    // Without this totalWords would always be saved as 0
    private final OnStatusChangedListener listener; // Who to notify after status changes

    public FlashcardDialog(Activity activity, String categoryId,
                           List<Word> wordList, OnStatusChangedListener listener) {
        this.activity = activity;
        this.categoryId = categoryId;
        this.wordList = wordList; // Save reference - used in markWord() to get total count
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    public void show(Word word) {
        // Inflate = read dialog_flashcard.xml and build the View from it
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_flashcard, null);

        ImageView imgWord = view.findViewById(R.id.imgFlashcardWord);
        TextView tvWordEnglish = view.findViewById(R.id.tvFlashcardEnglish);
        TextView tvWordHebrew = view.findViewById(R.id.tvFlashcardHebrew);
        TextView tvExampleSentence = view.findViewById(R.id.tvFlashcardExample);
        Button btnKnow = view.findViewById(R.id.btnKnow);
        Button btnStillLearning = view.findViewById(R.id.btnStillLearning);

        // Fill views with word data
        tvWordEnglish.setText(word.getWordEnglish());
        tvWordHebrew.setText(word.getWordHebrew());
        tvExampleSentence.setText(word.getExampleSentence());
        Glide.with(activity).load(word.getImage()).into(imgWord);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(view)
                .create();

        // ✅ I know this - marks word as learned
        btnKnow.setOnClickListener(v -> {
            markWord(word.getIdFS(), true); // true = learned
            dialog.dismiss();
        });

        // ❌ Still learning - marks word as not learned
        btnStillLearning.setOnClickListener(v -> {
            markWord(word.getIdFS(), false); // false = not learned
            dialog.dismiss();
        });

        dialog.show();
    }

    private void markWord(String wordId, boolean learned) {
        // Only save progress for logged in users
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;

        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Read current progress document to get the existing learnedWords array
        db.collection("users").document(userId)
                .collection("progress").document(categoryId)
                .get()
                .addOnSuccessListener(document -> {

                    // Read current learnedWords array - start empty if document doesn't exist yet
                    List<String> learnedWords = new ArrayList<>();
                    if (document.exists() && document.get("learnedWords") != null) {
                        // Firestore returns arrays as List<Object> so we cast each item to String
                        List<Object> raw = (List<Object>) document.get("learnedWords");
                        for (Object item : raw) {
                            learnedWords.add((String) item);
                        }
                    }

                    if (learned) {
                        // User tapped ✅ - add wordId if not already in the list
                        // We check first to avoid counting the same word twice
                        if (!learnedWords.contains(wordId)) {
                            learnedWords.add(wordId);
                        }
                        Toast.makeText(activity, "Great job! ⭐", Toast.LENGTH_SHORT).show();
                    } else {
                        // User tapped ❌ - remove wordId from the list if it's there
                        learnedWords.remove(wordId);
                        Toast.makeText(activity, "Keep practicing! 💪", Toast.LENGTH_SHORT).show();
                    }

                    // Build updated progress data to save back to Firestore
                    HashMap<String, Object> progress = new HashMap<>();
                    progress.put("learnedWords", learnedWords); // Updated list of learned wordIds
                    progress.put("wordsLearned", learnedWords.size()); // Count = size of list
                    progress.put("totalWords", wordList.size()); // Total words in this category
                    // wordList.size() is correct here because we pass wordList from WordsActivity
                    // This is what ProfileActivity reads to calculate the overall progress percentage

                    // SetOptions.merge() updates only these fields without deleting other fields
                    db.collection("users").document(userId)
                            .collection("progress").document(categoryId)
                            .set(progress, SetOptions.merge())
                            .addOnSuccessListener(v -> {
                                if (listener != null) listener.onStatusChanged();
                            });
                });
    }
}
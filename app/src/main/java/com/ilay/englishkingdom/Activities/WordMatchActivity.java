package com.ilay.englishkingdom.Activities;

import android.os.Bundle;
import android.util.Pair;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.R;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class WordMatchActivity extends AppCompatActivity {
    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Used to get the current user for saving best time and history
    private TextView tvLoading; // Shows "Loading..." while words are being fetched from Firestore

    private List<TextView> wordTextViews = new ArrayList<>(); // One TextView per word in the list below grid
    private TextView word1Text, word2Text;
    private ImageView word1ImageView, word2ImageView;
    private LinearLayout wordListContainer;


    private Stage stage1Words, stage2Words, stage3Words;
    private int currentStage = 1;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_match);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();
        tvLoading = findViewById(R.id.tvLoading);
        word1Text = findViewById(R.id.wordText1);
        word2Text = findViewById(R.id.wordText2);
        word1ImageView = findViewById(R.id.wordImage1);
        word2ImageView = findViewById(R.id.wordImage2);
        wordListContainer = findViewById(R.id.wordListContainer);
    }


    private void loadWords() {
        // Loads all words from WORDS type categories only
        db.collection("categories")
                .whereEqualTo("categoryType", CategoryType.WORDS.name())
                .get()
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {categories.size()};

                    if (categoriesLeft[0] == 0) {
                        tvLoading.setText("No words found! Please add some words first.");
                        return;
                    }

                    
                    // (Word, Image)
                    List<Pair<String,String>> allWordsList = new ArrayList<>();
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        String english = wordDoc.getString("wordEnglish");
                                        String image = wordDoc.getString("image");
                                        // Only add single words that fit in the grid
                                        if (english != null
                                                && !english.isEmpty()
                                                && !english.contains(" ")) {
                                            allWordsList.add(new Pair<>(english.toLowerCase(), image));
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        startGame(allWordsList); // All categories loaded - build grid
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    private void startGame(List<Pair<String,String>> allWordsList) {
        if(allWordsList.size() < 2) {
            Toast.makeText(this, "Opps, there are not enough words, ending game", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        Set<String> previouslyUsed = new HashSet<>();

        stage1Words = buildStage(allWordsList, previouslyUsed);
        stage2Words = buildStage(allWordsList, previouslyUsed);
        stage3Words = buildStage(allWordsList, previouslyUsed);
        // Start stage
        currentStage = 1;
        startStage();
    }

    private void startStage() {

        word1Text.setText("");
        word2Text.setText("");

        if(currentStage == 1) {
            Glide.with(this).load(stage1Words.firstWord.second).into(word1ImageView);
            Glide.with(this).load(stage1Words.secondWord.second).into(word2ImageView);

            buildWordList(stage1Words);
        } else if(currentStage == 2){
            Glide.with(this).load(stage2Words.firstWord.second).into(word1ImageView);
            Glide.with(this).load(stage2Words.secondWord.second).into(word2ImageView);
            buildWordList(stage2Words);
        } else if(currentStage == 3) {
            Glide.with(this).load(stage3Words.firstWord.second).into(word1ImageView);
            Glide.with(this).load(stage3Words.secondWord.second).into(word2ImageView);
            buildWordList(stage3Words);
        }
    }



    static class Stage {
        private List<Pair<String, String>> stageWordStorage;
        private Pair<String, String> firstWord, secondWord;

        public Stage(
                List<Pair<String, String>> stageWordStorage,
                Pair<String, String> firstWord,
                Pair<String, String> secondWord
        ) {
            this.stageWordStorage = stageWordStorage;
            this.firstWord = firstWord;
            this.secondWord = secondWord;
        }
    }


    private Stage buildStage(List<Pair<String, String>> allWordsList, Set<String> previouslyUsed) {
        List<Pair<String, String>> tempCopy = new ArrayList<>(allWordsList);
        // Select 2 random words
        Random rnd = new Random();
        Pair<String, String> firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        Pair<String, String> secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        int attempts = 20;
        while(attempts > 0 && previouslyUsed.contains(firstWord.first)) {
            attempts--;
            if(tempCopy.isEmpty()) {
                return null; // Failed to build stage (no more words)
            }
            firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        }
        if(attempts == 0) {
            return null; // Failed after 20 attempts -> Most likely not enough words
        }
        attempts = 20;
        while(attempts > 0 && previouslyUsed.contains(secondWord.first)) {
            attempts--;
            if(tempCopy.isEmpty()) {
                return null; // Failed to build stage (no more words)
            }
            secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        }
        previouslyUsed.add(firstWord.first);
        previouslyUsed.add(secondWord.second);
        Collections.shuffle(tempCopy);
        List<Pair<String, String>> usedWords =  tempCopy.stream()
                .limit(4)
                .collect(Collectors.toList());
        usedWords.add(firstWord);
        usedWords.add(secondWord);
        Collections.shuffle(usedWords);

        // Build the list below the game
        return new Stage(usedWords, firstWord, secondWord);
    }
    private void buildWordList(Stage stage) {
        List<String> usedWords = stage.stageWordStorage.stream().map(p -> p.first).collect(Collectors.toList());
        wordTextViews.clear();
        wordListContainer.removeAllViews();

        // Header label
        TextView header = new TextView(this);
        header.setText("Find these words:");
        header.setTextColor(0xFFFFD700); // Gold
        header.setTextSize(14);
        header.setPadding(8, 8, 8, 8);
        wordListContainer.addView(header);

        // Show 3 words per row to save vertical space
        LinearLayout currentRow = null;

        for (int i = 0; i < usedWords.size(); i++) {
            if (i % 3 == 0) { // Start a new row every 3 words
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                wordListContainer.addView(currentRow);
            }

            TextView tv = getBottomTextView(usedWords, i);

            currentRow.addView(tv);
            wordTextViews.add(tv); // Save reference so we can strike through when found
        }
    }


    // Creates a text view for a word below the game (word storage)
    @NonNull
    private TextView getBottomTextView(List<String> usedWords, int i) {
        String word = usedWords.get(i);

        TextView tv = new TextView(this);
        tv.setText(word.toUpperCase()); // Show uppercase to match the grid
        tv.setTextColor(0xFFFFFFFF); // White
        tv.setTextSize(13);
        tv.setPadding(8, 4, 8, 4);

        // Equal width for all 3 words in a row using weight
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(params);
        return tv;
    }

}

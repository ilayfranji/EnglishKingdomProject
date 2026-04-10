package com.ilay.englishkingdom.Activities;

import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.Models.Stage;
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
    private TextView word1Text, word2Text, infoTv;
    private ImageView word1ImageView, word2ImageView;
    private LinearLayout wordListContainer;


    private Stage stage1Words, stage2Words, stage3Words;
    private int currentStage = 1;

    private LinearLayout layout1, layout2;

    private String selectedWord = null;
    private TextView selectedTv = null;


    private LinearLayout wordMatchLayout;

    private boolean inTransition = false;
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
        infoTv = findViewById(R.id.info);
        layout1 = findViewById(R.id.layout1);
        layout2 = findViewById(R.id.layout2);
        wordMatchLayout = findViewById(R.id.wordMatchLayout);

        tvLoading.setVisibility(View.VISIBLE);
        wordMatchLayout.setVisibility(View.GONE);
        loadWords();
        layout1.setOnClickListener(v -> {
            if(inTransition) return;
            if(!isWordSelected())return;
            Stage stage = getCurrentStageObject();
            if(stage.isFirstWordSuccess())return;
            if(selectedWord.equalsIgnoreCase(stage.getFirstWord().first)) {
                // success
                Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show();
                inTransition = true;
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green));
                word1Text.setText(stage.getFirstWord().first);
                resetSelectedWord();
                stage.setFirstWordSuccess(true);
                if(stage.isSecondWordSuccess()) {
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                        nextStage();
                    },2000);
                }else {
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                    },2000);
                }
            } else {
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red));
                inTransition = true;
                new Handler().postDelayed(() -> {
                    layout1.setBackground(null);
                    inTransition = false;
                },500);
                resetSelectedWord();

                Toast.makeText(this, "Opps! wrong, try again", Toast.LENGTH_SHORT).show();
            }
        });
        layout2.setOnClickListener(v -> {
            if(inTransition) return;
            if(!isWordSelected())return;

            Stage stage = getCurrentStageObject();
            if(stage.isSecondWordSuccess())return;
            if(selectedWord.equalsIgnoreCase(stage.getSecondWord().first)) {
                // success
                Toast.makeText(this, "Correct! Advancing to next stage", Toast.LENGTH_SHORT).show();
                inTransition = true;
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green));
                word2Text.setText(stage.getSecondWord().first);
                resetSelectedWord();
                stage.setSecondWordSuccess(true);
                if(stage.isFirstWordSuccess()) {
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                        nextStage();
                    },2000);
                }else {
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                    },2000);
                }
            } else {
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red));
                inTransition = true;
                new Handler().postDelayed(() -> {
                    layout2.setBackground(null);
                    inTransition = false;
                },500);
                resetSelectedWord();
                Toast.makeText(this, "Opps! wrong, try again", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetSelectedWord() {
        selectedWord = null;
        selectedTv = null;
        for(TextView some: wordTextViews) {
            some.setTextColor(Color.WHITE);
        }
    }

    private Stage getCurrentStageObject() {
        switch(currentStage) {
            case 2:
                return stage2Words;
            case 3:
                return stage3Words;
            default:
                return stage1Words;
        }
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
        tvLoading.setVisibility(View.GONE);
        wordMatchLayout.setVisibility(View.VISIBLE);
        Set<String> previouslyUsed = new HashSet<>();

        stage1Words = buildStage(allWordsList, previouslyUsed);
        stage2Words = buildStage(allWordsList, previouslyUsed);
        stage3Words = buildStage(allWordsList, previouslyUsed);
        // Start stage
        currentStage = 1;
        startStage();
    }

    private void nextStage() {
        if(currentStage == 3) {

            return; // Finished game
        }
        currentStage++;
        layout1.setBackground(null);
        layout2.setBackground(null);
        startStage();
    }

    private void startStage() {

        word1Text.setText("");
        word2Text.setText("");

        if(currentStage == 1) {
            Glide.with(this).load(stage1Words.getFirstWord().second).into(word1ImageView);
            Glide.with(this).load(stage1Words.getSecondWord().second).into(word2ImageView);

            buildWordList(stage1Words);
        } else if(currentStage == 2){
            Glide.with(this).load(stage2Words.getFirstWord().second).into(word1ImageView);
            Glide.with(this).load(stage2Words.getSecondWord().second).into(word2ImageView);
            buildWordList(stage2Words);
        } else if(currentStage == 3) {
            Glide.with(this).load(stage3Words.getFirstWord().second).into(word1ImageView);
            Glide.with(this).load(stage3Words.getSecondWord().second).into(word2ImageView);
            buildWordList(stage3Words);
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
        List<String> usedWords = stage.getStageWordStorage().stream().map(p -> p.first).collect(Collectors.toList());
        wordTextViews.clear();
        wordListContainer.removeAllViews();

        // Header label
        TextView header = new TextView(this);
        header.setText("Word Storage:");
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


    private boolean isWordSelected() {
        return selectedWord != null;
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

        tv.setOnClickListener(v -> {
            if(inTransition) return;
            if(word.equals(selectedWord)) {
                selectedWord = null;
                tv.setTextColor(0xFFFFFFFF);
                infoTv.setText("Pick a word from the word storage");
                return;
            }
           selectedWord = word;
           selectedTv = tv;
           for(TextView other : wordTextViews) {
               if(other != tv) {
                   other.setTextColor(0xFFFFFFFF);
               }
           }
           tv.setTextColor(Color.YELLOW);
           infoTv.setText("Pick an image that matches the word: " + word);
        });

        // Equal width for all 3 words in a row using weight
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(params);
        return tv;
    }

}

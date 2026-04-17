package com.ilay.englishkingdom.Activities;

import android.graphics.Color; // Used to color selected words yellow and correct words green
import android.os.Bundle; // Used when creating the activity
import android.os.Handler; // Used for the timer and the transition delay after correct/wrong answer
import android.os.Looper; // Used with Handler to run on the main thread
import android.util.Pair; // Used to store word + image URL pairs
import android.view.View; // Used to show and hide UI elements
import android.widget.ImageView; // Used to display the pictures
import android.widget.LinearLayout; // Used for the image rows and word bank rows
import android.widget.TextView; // Used for the back button, timer, lives, info label and word bank items
import android.widget.Toast; // Used to show short messages

import androidx.annotation.NonNull; // Used for the @NonNull annotation on the helper method
import androidx.appcompat.app.AlertDialog; // Used for the back confirmation, game over and win dialogs
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.appcompat.content.res.AppCompatResources; // Used to load the green and red border drawables

import com.bumptech.glide.Glide; // Used to load pictures from Cloudinary URLs
import com.google.firebase.auth.FirebaseAuth; // Used to get the current user for saving history
import com.google.firebase.firestore.FirebaseFirestore; // Used to load words and save history
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single Firestore document
import com.ilay.englishkingdom.Models.CategoryType; // Used to filter only WORDS type categories
import com.ilay.englishkingdom.Models.Stage; // Holds the data for one stage (2 words + word bank)
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.text.SimpleDateFormat; // Used to format the game date and time for history
import java.util.ArrayList; // Used for word lists
import java.util.Collections; // Used to shuffle words so stages are random
import java.util.Date; // Used to get current date and time
import java.util.HashSet; // Used to track correctly matched words
import java.util.List; // The List interface
import java.util.Locale; // Used for time formatting
import java.util.Random; // Used to pick random words for each stage
import java.util.Set; // Used to track previously used words across stages
import java.util.stream.Collectors; // Used to build the word bank list

public class WordMatchActivity extends AppCompatActivity {

    // ==================== CONSTANTS ====================

    private static final int MAX_LIVES = 3; // Player gets 3 wrong attempts before game over

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Used to get current user for saving game history

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow - asks for confirmation before leaving
    private TextView tvTimer; // Timer counting up from 0:00:000
    private TextView tvLoading; // Loading text while words are fetched from Firestore
    private TextView word1Text; // Shows the matched word on top of image 1 when correct
    private TextView word2Text; // Shows the matched word on top of image 2 when correct
    private TextView infoTv; // Tells user which word is selected or asks them to pick one
    private TextView livesTv; // Shows remaining lives e.g. "Lives: 2/3"
    private ImageView word1ImageView; // The first picture
    private ImageView word2ImageView; // The second picture
    private LinearLayout wordListContainer; // Container for the word bank below the images
    private LinearLayout layout1; // The first image row - tapping matches selected word to image 1
    private LinearLayout layout2; // The second image row - tapping matches selected word to image 2
    private LinearLayout wordMatchLayout; // The whole game area - hidden while loading

    // ==================== GAME DATA ====================

    private Stage stage1Words; // Data for stage 1 - 2 words + word bank
    private Stage stage2Words; // Data for stage 2
    private Stage stage3Words; // Data for stage 3
    private int currentStage = 1; // Which stage we're on right now (1, 2 or 3)
    private String selectedWord = null; // The word the user tapped in the word bank
    private TextView selectedTv = null; // The TextView the user tapped - used to reset its color
    private boolean inTransition = false; // true = waiting after right/wrong answer, ignore taps
    private HashSet<String> correct = new HashSet<>(); // Words correctly matched in current stage
    private List<TextView> wordTextViews = new ArrayList<>(); // All word bank TextViews - used to reset colors
    private int lives = MAX_LIVES; // How many wrong attempts are left

    // ==================== TIMER ====================

    private Handler timerHandler = new Handler(Looper.getMainLooper()); // Runs on main thread
    private long startTime = 0; // System time when the timer started in milliseconds
    private long elapsedTime = 0; // How many milliseconds have passed
    private boolean timerRunning = false; // true = timer is ticking

    // This runnable updates the timer display every 10 milliseconds
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime; // Calculate elapsed time
            tvTimer.setText(formatTime(elapsedTime)); // Update display
            timerHandler.postDelayed(this, 10); // Run again in 10ms
        }
    };

    // ==================== GAME START TIME ====================

    private String gameStartDate = ""; // Saved when timer starts e.g. "28/03/2026"
    private String gameStartTime = ""; // Saved when timer starts e.g. "17:45"

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_match);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Connect each variable to its XML view
        tvBack = findViewById(R.id.tvBack);
        tvTimer = findViewById(R.id.tvTimer);
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
        livesTv = findViewById(R.id.tvLives);

        // Hide game area and show loading while words are fetched
        tvLoading.setVisibility(View.VISIBLE);
        wordMatchLayout.setVisibility(View.GONE);

        // Back button - ask for confirmation so the user doesn't lose progress by accident
        tvBack.setOnClickListener(v -> showBackConfirmation());

        loadWords(); // Start loading words from Firestore

        // ==================== IMAGE CLICK LISTENERS ====================

        layout1.setOnClickListener(v -> {
            if (inTransition) return; // Ignore taps during transition delay
            if (!isWordSelected()) return; // Ignore if no word is selected

            Stage stage = getCurrentStageObject();
            if (stage.isFirstWordSuccess()) return; // Already matched - ignore tap

            if (selectedWord.equalsIgnoreCase(stage.getFirstWord().first)) {
                // Correct match for image 1!
                correct.add(stage.getFirstWord().first.toLowerCase()); // Mark as correctly matched
                inTransition = true; // Block taps during the 2 second success animation
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green)); // Green border
                word1Text.setText(stage.getFirstWord().first); // Show the word on the image
                resetSelectedWord(); // Deselect the word from the word bank
                stage.setFirstWordSuccess(true); // Mark image 1 as done

                if (stage.isSecondWordSuccess()) {
                    // Both images matched - move to next stage after 2 seconds
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                        nextStage();
                    }, 2000);
                } else {
                    // Still need to match image 2 - just unblock taps after 2 seconds
                    new Handler().postDelayed(() -> inTransition = false, 2000);
                }
            } else {
                // Wrong match for image 1
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red)); // Red border
                inTransition = true;
                lives--; // Lose a life
                livesTv.setText(getLivesText()); // Update lives display
                resetSelectedWord();

                new Handler().postDelayed(() -> {
                    layout1.setBackground(null); // Remove red border after 0.5 seconds
                    inTransition = false;
                }, 500);

                if (lives == 0) {
                    gameOver(false); // No lives left - game over
                } else {
                    Toast.makeText(this, "Not quite! " + lives + " " +
                            (lives == 1 ? "life" : "lives") + " left 💪", Toast.LENGTH_SHORT).show();
                }
            }
        });

        layout2.setOnClickListener(v -> {
            if (inTransition) return;
            if (!isWordSelected()) return;

            Stage stage = getCurrentStageObject();
            if (stage.isSecondWordSuccess()) return; // Already matched - ignore tap

            if (selectedWord.equalsIgnoreCase(stage.getSecondWord().first)) {
                // Correct match for image 2!
                correct.add(stage.getSecondWord().first.toLowerCase());
                inTransition = true;
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green));
                word2Text.setText(stage.getSecondWord().first);
                resetSelectedWord();
                stage.setSecondWordSuccess(true);

                if (stage.isFirstWordSuccess()) {
                    // Both images matched - move to next stage after 2 seconds
                    new Handler().postDelayed(() -> {
                        inTransition = false;
                        nextStage();
                    }, 2000);
                } else {
                    new Handler().postDelayed(() -> inTransition = false, 2000);
                }
            } else {
                // Wrong match for image 2
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red));
                inTransition = true;
                lives--;
                livesTv.setText(getLivesText());
                resetSelectedWord();

                new Handler().postDelayed(() -> {
                    layout2.setBackground(null);
                    inTransition = false;
                }, 500);

                if (lives == 0) {
                    gameOver(false);
                } else {
                    Toast.makeText(this, "Not quite! " + lives + " " +
                            (lives == 1 ? "life" : "lives") + " left 💪", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // Always stop the timer when activity is destroyed to prevent memory leaks
    }

    // ==================== BACK BUTTON ====================

    private void showBackConfirmation() {
        // Asks the user if they really want to leave - prevents accidental exits mid game
        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("If you go back now your progress will be lost. Are you sure?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    stopTimer(); // Stop the timer before leaving
                    finish(); // Close the activity
                })
                .setNegativeButton("Keep Playing", null) // User changed their mind - stay
                .show();
    }

    // ==================== TIMER METHODS ====================

    private void startTimer() {
        // Save the start date and time for game history
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date();
        gameStartDate = dateFormat.format(now); // e.g. "28/03/2026"
        gameStartTime = timeFormat.format(now); // e.g. "17:45"

        startTime = System.currentTimeMillis(); // Save when the timer started
        timerRunning = true;
        timerHandler.post(timerRunnable); // Start ticking
    }

    private void stopTimer() {
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable); // Cancel any pending timer updates
    }

    private String formatTime(long millis) {
        // Converts milliseconds into "0:00:000" format - same as Word Search and Trivia
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);
    }

    // ==================== LIVES HELPER ====================

    private String getLivesText() {
        // Returns e.g. "Lives: 2/3" - called every time lives change
        return String.format("Lives: %d/%d", lives, MAX_LIVES);
    }

    // ==================== GAME OVER / WIN ====================

    private void gameOver(boolean won) {
        stopTimer(); // Stop the timer as soon as the game ends
        saveGameHistory(won); // Save this game to history before showing the dialog
        inTransition = true; // Disable all interactions while dialog is showing

        String title;
        String message;

        if (won) {
            // User completed all 3 stages without running out of lives
            title = "You Won! 🎉";
            message = "Amazing! You matched all the words correctly!\n\n" +
                    "Your time: " + formatTime(elapsedTime) + "\n" +
                    "Lives remaining: " + lives + "/" + MAX_LIVES;
        } else {
            // User made 3 mistakes and ran out of lives
            title = "Game Over! 💔";
            message = "You ran out of lives this time, but don't give up!\n\n" +
                    "You made it to stage " + currentStage + " out of 3.\n" +
                    "Your time: " + formatTime(elapsedTime) + "\n\n" +
                    "Keep practicing and try again!";
        }

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Play Again", (dialog, which) -> resetGame()) // Start fresh
                .setNegativeButton("Exit", (dialog, which) -> finish()) // Go back to Practice screen
                .setCancelable(false) // User must tap a button
                .show();
    }

    // ==================== RESET SELECTED WORD ====================

    private void resetSelectedWord() {
        // Clears the selection and resets all word bank text colors
        selectedWord = null;
        selectedTv = null;
        infoTv.setText("Pick a word from the word bank below"); // Reset info label

        for (TextView tv : wordTextViews) {
            // Already matched words stay green, others go back to white
            if (correct.contains(tv.getText().toString().toLowerCase())) {
                tv.setTextColor(Color.GREEN);
            } else {
                tv.setTextColor(Color.WHITE);
            }
        }
    }

    // ==================== GET CURRENT STAGE ====================

    private Stage getCurrentStageObject() {
        // Returns the Stage object for whichever stage we're currently on
        switch (currentStage) {
            case 2: return stage2Words;
            case 3: return stage3Words;
            default: return stage1Words;
        }
    }

    // ==================== LOAD WORDS ====================

    private void loadWords() {
        // Loads all words from WORDS type categories only - same approach as Word Search
        db.collection("categories")
                .whereEqualTo("categoryType", CategoryType.WORDS.name())
                .get()
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {categories.size()};

                    if (categoriesLeft[0] == 0) {
                        tvLoading.setText("No words found! Please add some words first.");
                        return;
                    }

                    // Each pair is (English word, Cloudinary image URL)
                    List<Pair<String, String>> allWordsList = new ArrayList<>();

                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        String english = wordDoc.getString("wordEnglish");
                                        String image = wordDoc.getString("image");
                                        // Only add single words (no spaces) that have an image
                                        if (english != null && !english.isEmpty()
                                                && !english.contains(" ")
                                                && image != null && !image.isEmpty()) {
                                            allWordsList.add(new Pair<>(english.toLowerCase(), image));
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        startGame(allWordsList); // All loaded - start the game
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    // ==================== START GAME ====================

    private void startGame(List<Pair<String, String>> allWordsList) {
        if (allWordsList.size() < 6) {
            // Need at least 6 words - 2 correct + 4 distractors per stage
            Toast.makeText(this, "Not enough words to play. Please add more words first!", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        livesTv.setText(getLivesText()); // Show initial lives
        tvLoading.setVisibility(View.GONE); // Hide loading text
        wordMatchLayout.setVisibility(View.VISIBLE); // Show game area

        // Build all 3 stages - each stage uses different words
        Set<String> previouslyUsed = new HashSet<>(); // Track used words so stages don't repeat
        stage1Words = buildStage(allWordsList, previouslyUsed);
        stage2Words = buildStage(allWordsList, previouslyUsed);
        stage3Words = buildStage(allWordsList, previouslyUsed);

        currentStage = 1; // Always start from stage 1
        startTimer(); // Start the timer now that game is ready
        startStage(); // Show stage 1
    }

    // ==================== NEXT STAGE ====================

    private void nextStage() {
        if (currentStage == 3) {
            gameOver(true); // All 3 stages done - user wins!
            return;
        }

        correct.clear(); // Clear matched words from previous stage
        currentStage++; // Move to next stage
        layout1.setBackground(null); // Remove green border from previous stage
        layout2.setBackground(null);
        startStage(); // Show the new stage
    }

    // ==================== START STAGE ====================

    private void startStage() {
        // Resets the image rows and loads the pictures and word bank for the current stage
        word1Text.setText(""); // Clear previous word text on image 1
        word2Text.setText(""); // Clear previous word text on image 2
        infoTv.setText("Pick a word from the word bank below"); // Reset info label

        Stage stage = getCurrentStageObject(); // Get the data for this stage

        // Load both pictures from their Cloudinary URLs using Glide
        Glide.with(this).load(stage.getFirstWord().second).into(word1ImageView);
        Glide.with(this).load(stage.getSecondWord().second).into(word2ImageView);

        buildWordList(stage); // Build the word bank below
    }

    // ==================== BUILD STAGE ====================

    private Stage buildStage(List<Pair<String, String>> allWordsList, Set<String> previouslyUsed) {
        // Picks 2 unique words for the images and 4 distractors for the word bank
        List<Pair<String, String>> tempCopy = new ArrayList<>(allWordsList);
        Random rnd = new Random();

        // Pick first word - make sure it wasn't used in a previous stage
        Pair<String, String> firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        int attempts = 20; // Max attempts to find a non-duplicate
        while (attempts > 0 && previouslyUsed.contains(firstWord.first)) {
            attempts--;
            if (tempCopy.isEmpty()) return null;
            firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        }
        if (attempts == 0) return null;

        // Pick second word - also make sure it's unique
        Pair<String, String> secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        attempts = 20;
        while (attempts > 0 && previouslyUsed.contains(secondWord.first)) {
            attempts--;
            if (tempCopy.isEmpty()) return null;
            secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        }

        // Mark both words as used so the next stage picks different ones
        previouslyUsed.add(firstWord.first);
        previouslyUsed.add(secondWord.first);

        // Pick 4 random distractor words from the remaining pool
        Collections.shuffle(tempCopy);
        List<Pair<String, String>> stageWords = tempCopy.stream()
                .limit(4)
                .collect(Collectors.toList());

        // Add the 2 correct words into the mix and shuffle
        stageWords.add(firstWord);
        stageWords.add(secondWord);
        Collections.shuffle(stageWords);

        return new Stage(stageWords, firstWord, secondWord);
    }

    // ==================== BUILD WORD BANK ====================

    private void buildWordList(Stage stage) {
        // Creates a TextView for each word in the word bank and adds them to the container
        List<String> words = stage.getStageWordStorage()
                .stream().map(p -> p.first).collect(Collectors.toList());

        wordTextViews.clear(); // Clear old references
        wordListContainer.removeAllViews(); // Remove old views

        // Header label
        TextView header = new TextView(this);
        header.setText("Word Bank:");
        header.setTextColor(0xFFFFD700); // Gold
        header.setTextSize(14);
        header.setPadding(8, 8, 8, 8);
        wordListContainer.addView(header);

        // Show 3 words per row to save space
        LinearLayout currentRow = null;

        for (int i = 0; i < words.size(); i++) {
            if (i % 3 == 0) { // Start a new row every 3 words
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                wordListContainer.addView(currentRow);
            }

            TextView tv = createWordBankTextView(words, i);
            currentRow.addView(tv);
            wordTextViews.add(tv);
        }
    }

    // ==================== WORD BANK TEXT VIEW ====================

    @NonNull
    private TextView createWordBankTextView(List<String> words, int i) {
        // Creates a single word TextView for the word bank with a click listener
        String word = words.get(i);

        TextView tv = new TextView(this);
        tv.setText(word.toUpperCase()); // Show uppercase so it matches the overall style
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setPadding(8, 4, 8, 4);

        tv.setOnClickListener(v -> {
            if (correct.contains(word)) return; // Already matched - can't select again
            if (inTransition) return; // During transition delay - ignore taps

            if (word.equals(selectedWord)) {
                // Tapping the already selected word deselects it
                selectedWord = null;
                tv.setTextColor(Color.WHITE);
                infoTv.setText("Pick a word from the word bank below");
                return;
            }

            // Select this word - highlight it yellow and update info label
            selectedWord = word;
            selectedTv = tv;

            // Reset all other word colors
            for (TextView other : wordTextViews) {
                if (other != tv) {
                    // Already matched words stay green, others go white
                    if (correct.contains(other.getText().toString().toLowerCase())) {
                        other.setTextColor(Color.GREEN);
                    } else {
                        other.setTextColor(Color.WHITE);
                    }
                }
            }

            tv.setTextColor(Color.YELLOW); // Highlight selected word in yellow
            infoTv.setText("Now tap the image that matches: " + word.toUpperCase());
        });

        // Equal width for all 3 words in a row
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(params);
        return tv;
    }

    // ==================== IS WORD SELECTED ====================

    private boolean isWordSelected() {
        return selectedWord != null; // Returns true if the user has tapped a word in the bank
    }

    // ==================== SAVE GAME HISTORY ====================

    private void saveGameHistory(boolean won) {
        // Save this game to Firestore so it appears in the history screen
        // Only save for logged in users - guests have no history
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();
        historyEntry.put("type", "WORDMATCH"); // So history screen knows which game this is
        historyEntry.put("date", gameStartDate); // e.g. "28/03/2026"
        historyEntry.put("time", gameStartTime); // e.g. "17:45"
        historyEntry.put("result", won ? "Won 🎉" : "Lost 💔"); // Won or lost
        historyEntry.put("stage", "Stage " + currentStage + "/3"); // How far they got
        historyEntry.put("livesLeft", lives + "/" + MAX_LIVES); // Lives remaining
        historyEntry.put("duration", formatTime(elapsedTime)); // How long the game took
        historyEntry.put("timestamp", System.currentTimeMillis()); // For sorting by date

        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry); // Silent save - no toast needed
    }

    // ==================== RESET GAME ====================

    private void resetGame() {
        // Resets everything and starts a fresh game from stage 1
        lives = MAX_LIVES; // Restore all lives
        currentStage = 1; // Back to stage 1
        correct.clear(); // Clear matched words
        inTransition = false; // Re-enable interactions
        selectedWord = null;
        selectedTv = null;
        elapsedTime = 0; // Reset elapsed time
        tvTimer.setText("0:00:000"); // Reset timer display
        livesTv.setText(getLivesText()); // Reset lives display
        layout1.setBackground(null); // Remove any leftover borders
        layout2.setBackground(null);

        // Reload words and build fresh stages
        tvLoading.setVisibility(View.VISIBLE);
        wordMatchLayout.setVisibility(View.GONE);
        loadWords();
    }
}
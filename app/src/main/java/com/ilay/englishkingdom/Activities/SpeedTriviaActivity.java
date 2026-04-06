package com.ilay.englishkingdom.Activities;

import android.graphics.Color; // Used to color buttons green or red after answering
import android.os.Bundle; // Used when creating the activity
import android.os.Handler; // Used for the countdown timer and the 0.5s auto-advance delay
import android.os.Looper; // Used with Handler to run on the main thread
import android.view.View; // Used to show and hide UI elements
import android.widget.Button; // Used for the 3 answer buttons
import android.widget.TextView; // Used for the timer, score and question
import android.widget.Toast; // Used to show short messages

import androidx.appcompat.app.AlertDialog; // Used to show the results dialog
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to load words and save best score
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single Firestore document
import com.ilay.englishkingdom.Models.CategoryType; // Used to filter out LETTERS categories
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.text.SimpleDateFormat; // Used to format the game date and time for history
import java.util.ArrayList; // Used to store the word list
import java.util.Collections; // Used to shuffle words so questions are random
import java.util.Date; // Used to get the current date and time
import java.util.List; // The List interface
import java.util.Locale; // Used for string formatting

public class SpeedTriviaActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow
    private TextView tvScore; // Shows current score e.g. "Score: 7"
    private TextView tvTimer; // Shows seconds remaining e.g. "60"
    private TextView tvQuestion; // Shows the English word to translate
    private Button btnAnswer1; // First answer choice
    private Button btnAnswer2; // Second answer choice
    private Button btnAnswer3; // Third answer choice
    private TextView tvLoading; // Loading text while words are being fetched

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Used to get current user for saving best score

    // ==================== GAME DATA ====================

    private List<Word> allWords = new ArrayList<>(); // All words loaded from Firestore
    private List<Word> remainingWords = new ArrayList<>();
    // This list starts as a copy of allWords shuffled once
// We take one word at a time from the front of this list
// When the list is empty we reshuffle allWords and refill it
// This guarantees no word repeats until every single word has been shown at least once
    private int score = 0; // How many correct answers so far this game
    private String correctAnswer = ""; // The correct Hebrew answer for the current question
    private boolean gameRunning = false; // true = game is in progress, false = game is over
    private boolean waitingForNext = false; // true = waiting for the 0.5s delay before next question

    // ==================== COUNTDOWN TIMER ====================

    private Handler countdownHandler = new Handler(Looper.getMainLooper()); // Runs on main thread
    private int secondsLeft = 60; // Starts at 60 and counts down to 0

    // This runnable ticks every 1 second to update the countdown display
    private Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            secondsLeft--; // Subtract 1 second

            tvTimer.setText(String.valueOf(secondsLeft)); // Update the display

            // Turn the timer red when below 10 seconds to create urgency
            if (secondsLeft <= 10) {
                tvTimer.setTextColor(Color.parseColor("#C62828")); // Red
            }

            if (secondsLeft <= 0) {
                // Time is up - end the game
                gameRunning = false;
                endGame(); // Show results
            } else {
                // Still time left - schedule the next tick in 1 second
                countdownHandler.postDelayed(this, 1000);
            }
        }
    };

    // ==================== AUTO-ADVANCE HANDLER ====================

    // This handler is used to wait 0.5 seconds after an answer before showing the next question
    // During the 0.5 seconds the button stays colored green/red so the user can see if they were right
    private Handler nextQuestionHandler = new Handler(Looper.getMainLooper());

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_trivia);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvBack = findViewById(R.id.tvBack);
        tvScore = findViewById(R.id.tvScore);
        tvTimer = findViewById(R.id.tvTimer);
        tvQuestion = findViewById(R.id.tvQuestion);
        btnAnswer1 = findViewById(R.id.btnAnswer1);
        btnAnswer2 = findViewById(R.id.btnAnswer2);
        btnAnswer3 = findViewById(R.id.btnAnswer3);
        tvLoading = findViewById(R.id.tvLoading);

        tvBack.setOnClickListener(v -> {
            // Stop everything when leaving so nothing runs in the background
            countdownHandler.removeCallbacks(countdownRunnable);
            nextQuestionHandler.removeCallbacksAndMessages(null);
            finish();
        });

        loadWords(); // Load words from Firestore then start the game
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Always clean up handlers when the activity is destroyed
        // Otherwise they keep running and cause memory leaks
        countdownHandler.removeCallbacks(countdownRunnable);
        nextQuestionHandler.removeCallbacksAndMessages(null);
    }

    // ==================== LOAD WORDS ====================

    private void loadWords() {
        // Load all categories except LETTERS - same as Classic Trivia
        db.collection("categories").get()
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {0};

                    // Count valid categories (skip LETTERS)
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;
                        categoriesLeft[0]++;
                    }

                    if (categoriesLeft[0] == 0) {
                        tvLoading.setText("No words found! Please add some words first.");
                        return;
                    }

                    // Load words from each valid category
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;

                        String categoryId = categoryDoc.getId();

                        db.collection("categories").document(categoryId)
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        Word word = wordDoc.toObject(Word.class);
                                        word.setIdFS(wordDoc.getId());
                                        // Only add words with both English and Hebrew text
                                        if (word.getWordEnglish() != null
                                                && word.getWordHebrew() != null
                                                && !word.getWordEnglish().isEmpty()
                                                && !word.getWordHebrew().isEmpty()) {
                                            allWords.add(word);
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        startGame(); // All loaded - start the game
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    // ==================== GAME START ====================

    private void startGame() {
        if (allWords.size() < 3) {
            tvLoading.setText("Not enough words! Please add at least 3 words first.");
            return;
        }

        // Fill remainingWords with all words shuffled
        // Questions will be taken from this list one by one
        // ensuring no repeats until all words have been shown
        remainingWords = new ArrayList<>(allWords);
        Collections.shuffle(remainingWords);

        // Hide loading and show the game elements
        tvLoading.setVisibility(View.GONE);
        tvQuestion.setVisibility(View.VISIBLE);
        btnAnswer1.setVisibility(View.VISIBLE);
        btnAnswer2.setVisibility(View.VISIBLE);
        btnAnswer3.setVisibility(View.VISIBLE);

        gameRunning = true;

        // Start the 60 second countdown
        countdownHandler.postDelayed(countdownRunnable, 1000);

        showNextQuestion(); // Show the first question
    }

    // ==================== SHOW QUESTION ====================

    private void showNextQuestion() {
        // Don't show a new question if the game is over
        if (!gameRunning) return;

        waitingForNext = false; // We're no longer waiting - show the question now

        // If remainingWords is empty it means we showed every word at least once
        // Reshuffle allWords and refill remainingWords for another round
        // This way words never repeat until all words have been shown
        if (remainingWords.isEmpty()) {
            remainingWords = new ArrayList<>(allWords); // Copy all words into remaining
            Collections.shuffle(remainingWords); // Shuffle so order is random each round
        }

        // Take the first word from the remaining list and remove it
        // This ensures we don't show this word again until all others have been shown
        Word word = remainingWords.remove(0); // remove(0) takes and removes the first item
        correctAnswer = word.getWordHebrew(); // Save the correct Hebrew answer

        // Show the English word
        tvQuestion.setText(word.getWordEnglish());

        resetButtons(); // Reset colors and re-enable buttons

        // Build 3 answer choices: 1 correct + 2 random wrong ones
        List<String> answers = buildAnswers(word);
        btnAnswer1.setText(answers.get(0));
        btnAnswer2.setText(answers.get(1));
        btnAnswer3.setText(answers.get(2));
    }

    // ==================== BUILD ANSWERS ====================

    private List<String> buildAnswers(Word correctWord) {
        // Creates a list of 3 answers: 1 correct + 2 random wrong ones
        List<String> answers = new ArrayList<>();
        answers.add(correctWord.getWordHebrew()); // Add correct answer first

        // Build a pool of wrong answers from all other words
        List<String> wrongPool = new ArrayList<>();
        for (Word w : allWords) {
            if (!w.getWordHebrew().equals(correctWord.getWordHebrew())) {
                wrongPool.add(w.getWordHebrew());
            }
        }

        Collections.shuffle(wrongPool); // Shuffle wrong answers

        // Add 2 wrong answers
        int wrongCount = Math.min(2, wrongPool.size());
        for (int i = 0; i < wrongCount; i++) {
            answers.add(wrongPool.get(i));
        }

        Collections.shuffle(answers); // Shuffle all 3 so correct isn't always first
        return answers;
    }

    // ==================== RESET BUTTONS ====================

    private void resetButtons() {
        // Reset all buttons back to dark blue and re-enable them
        int defaultColor = Color.parseColor("#1A237E");
        btnAnswer1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer1.setEnabled(true);
        btnAnswer2.setEnabled(true);
        btnAnswer3.setEnabled(true);

        // Set click listeners
        btnAnswer1.setOnClickListener(v -> checkAnswer(btnAnswer1));
        btnAnswer2.setOnClickListener(v -> checkAnswer(btnAnswer2));
        btnAnswer3.setOnClickListener(v -> checkAnswer(btnAnswer3));
    }

    // ==================== CHECK ANSWER ====================

    private void checkAnswer(Button tappedButton) {
        // Ignore taps if game is over or we're already waiting for next question
        if (!gameRunning || waitingForNext) return;

        waitingForNext = true; // Block further taps until next question appears

        String tappedAnswer = tappedButton.getText().toString();

        // Disable all buttons so user can't tap again during the 0.5s delay
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        if (tappedAnswer.equals(correctAnswer)) {
            // Correct! Turn the tapped button green and add to score
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32"))); // Green
            score++;
            tvScore.setText("Score: " + score); // Update score display
        } else {
            // Wrong! Turn tapped button red and highlight the correct answer in green
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828"))); // Red

            // Find and highlight the correct answer button
            if (btnAnswer1.getText().toString().equals(correctAnswer)) {
                btnAnswer1.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            } else if (btnAnswer2.getText().toString().equals(correctAnswer)) {
                btnAnswer2.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            } else {
                btnAnswer3.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            }
        }

        // Wait 0.5 seconds so the user can see if they were right or wrong
        // then automatically advance to the next question
        nextQuestionHandler.postDelayed(() -> {
            if (gameRunning) { // Only advance if game is still running
                showNextQuestion();
            }
        }, 500); // 500 milliseconds = 0.5 seconds
    }

    // ==================== END GAME ====================

    private void endGame() {
        // Stop all handlers to prevent anything from running after game ends
        countdownHandler.removeCallbacks(countdownRunnable);
        nextQuestionHandler.removeCallbacksAndMessages(null);

        // Disable all buttons so user can't answer after time is up
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        saveBestScore(); // Save best score if this game beat the previous best
        saveGameHistory(); // Save this game to history

        // Show the results dialog
        new AlertDialog.Builder(this)
                .setTitle("Time's up! ⏱")
                .setMessage("You answered " + score + " questions correctly!\n\n" +
                        getResultMessage()) // Show an appropriate message based on score
                .setPositiveButton("Play Again", (dialog, which) -> resetGame()) // Play again
                .setNegativeButton("Exit", (dialog, which) -> finish()) // Go back
                .setCancelable(false) // User must tap a button
                .show();
    }

    private String getResultMessage() {
        // Returns an appropriate message based on how well the user did
        if (score >= 20) return "Incredible! You're a Legend! 🔥";
        if (score >= 15) return "Amazing speed! 👑";
        if (score >= 10) return "Great job! Keep practicing! ⭐";
        if (score >= 5) return "Not bad! Try again to beat your score!";
        return "Keep studying and try again! 📚";
    }

    // ==================== SAVE BEST SCORE ====================

    private void saveBestScore() {
        // Save best score to Firestore if this game was better than the previous best
        // We only save for logged in users - guests have no history
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    // Default to 0 so any real score is automatically better
                    long currentBestScore = 0;
                    if (document.exists() && document.getLong("speedTriviaBestScore") != null) {
                        currentBestScore = document.getLong("speedTriviaBestScore");
                    }

                    // Only update if this game scored higher
                    if (score > currentBestScore) {
                        db.collection("users").document(userId)
                                .update("speedTriviaBestScore", score)
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "New best score! 🏆", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ==================== SAVE GAME HISTORY ====================

    private void saveGameHistory() {
        // Save this game to Firestore so the user can see it in the history screen
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Get the current date and time
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date();

        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();
        historyEntry.put("type", "SPEEDTRIVIA"); // So we know this is a Speed Trivia game
        historyEntry.put("date", dateFormat.format(now));
        historyEntry.put("time", timeFormat.format(now));
        historyEntry.put("score", String.valueOf(score)); // How many correct answers
        historyEntry.put("duration", "60 seconds"); // Speed mode is always 60 seconds
        historyEntry.put("timestamp", System.currentTimeMillis()); // For sorting

        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry); // Silent save - no toast needed
    }

    // ==================== RESET GAME ====================

    private void resetGame() {
        score = 0;
        secondsLeft = 60;
        waitingForNext = false;
        tvScore.setText("Score: 0");
        tvTimer.setText("60");
        tvTimer.setTextColor(Color.parseColor("#FFD700"));

        // Refill and reshuffle remainingWords for the fresh game
        remainingWords = new ArrayList<>(allWords);
        Collections.shuffle(remainingWords);

        gameRunning = true;

        countdownHandler.postDelayed(countdownRunnable, 1000);

        showNextQuestion();
    }
}
package com.ilay.englishkingdom.Activities;

import android.graphics.Color; // Used to color buttons green or red
import android.os.Bundle; // Used when creating the activity
import android.os.Handler; // Used to run the timer every millisecond
import android.os.Looper; // Used with Handler to run on the main thread
import android.view.View; // Used to show and hide UI elements
import android.widget.Button; // Used for the answer buttons and next button
import android.widget.TextView; // Used for question text, score and timer
import android.widget.Toast; // Used to show short messages

import androidx.appcompat.app.AlertDialog; // Used to show the results popup at the end
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to load words and save best stats
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single document
import com.ilay.englishkingdom.Models.CategoryType; // Used to filter out LETTERS categories
import com.ilay.englishkingdom.Models.Word; // Our Word data model
import com.ilay.englishkingdom.R; // Used to reference XML resources

import java.util.ArrayList; // Used to store the word list
import java.util.Collections; // Used to shuffle lists randomly
import java.util.List; // The List interface
import java.util.Locale; // Used for string formatting

public class TriviaActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow
    private TextView tvQuestionCount; // Shows "Question 3/10"
    private TextView tvScore; // Shows current score
    private TextView tvQuestion; // Shows the English word to translate
    private TextView tvTimer; // Shows elapsed time e.g. "0:45:230"
    private Button btnAnswer1; // First answer choice
    private Button btnAnswer2; // Second answer choice
    private Button btnAnswer3; // Third answer choice
    private Button btnNext; // Next question button
    private TextView tvLoading; // Loading text shown while fetching words

    // ==================== GAME DATA ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Used to get current user for saving stats
    private List<Word> allWords = new ArrayList<>(); // All words loaded from Firestore
    private List<Word> questionWords = new ArrayList<>(); // 10 random words for this game
    private int currentQuestion = 0; // Index of the current question (0-9)
    private int score = 0; // How many correct answers so far
    private String correctAnswer = ""; // The correct Hebrew answer for the current question

    // ==================== TIMER ====================

    private Handler timerHandler = new Handler(Looper.getMainLooper()); // Runs timer on main thread
    private long startTime = 0; // When the timer started in milliseconds
    private long elapsedTime = 0; // How many milliseconds have passed
    private boolean timerRunning = false; // true = timer is currently running

    // This runnable runs every 10ms to update the timer display
    // A Runnable is just a block of code that can be scheduled to run later
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // Calculate how much time has passed since the timer started
            elapsedTime = System.currentTimeMillis() - startTime;
            tvTimer.setText(formatTime(elapsedTime)); // Update the timer display
            // Schedule this same runnable to run again in 10ms
            timerHandler.postDelayed(this, 10);
        }
    };

    // ==================== CONSTANTS ====================

    private static final int TOTAL_QUESTIONS = 10; // Total questions per game

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trivia);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvBack = findViewById(R.id.tvBack);
        tvQuestionCount = findViewById(R.id.tvQuestionCount);
        tvScore = findViewById(R.id.tvScore);
        tvQuestion = findViewById(R.id.tvQuestion);
        tvTimer = findViewById(R.id.tvTimer);
        btnAnswer1 = findViewById(R.id.btnAnswer1);
        btnAnswer2 = findViewById(R.id.btnAnswer2);
        btnAnswer3 = findViewById(R.id.btnAnswer3);
        btnNext = findViewById(R.id.btnNext);
        tvLoading = findViewById(R.id.tvLoading);

        tvBack.setOnClickListener(v -> {
            stopTimer(); // Stop the timer when going back
            finish();
        });

        // Next button moves to the next question
        btnNext.setOnClickListener(v -> {
            currentQuestion++; // Move to next question
            if (currentQuestion < questionWords.size()) {
                showQuestion(); // Show next question
            } else {
                stopTimer(); // All questions done - stop the timer
                showResults(); // Show results popup
            }
        });

        loadWords(); // Load all words from Firestore then start the game
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // Always stop the timer when activity is destroyed to avoid memory leaks
    }

    // ==================== TIMER METHODS ====================

    private void startTimer() {
        // Starts the timer from 0
        startTime = System.currentTimeMillis(); // Save the current time
        timerRunning = true;
        timerHandler.post(timerRunnable); // Start running the timer runnable
    }

    private void stopTimer() {
        // Stops the timer
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable); // Stop the timer runnable from running again
    }

    private String formatTime(long millis) {
        // Converts milliseconds into a readable "0:00:000" format
        // e.g. 75230ms → "1:15:230"
        long minutes = millis / 60000; // 1 minute = 60000ms
        long seconds = (millis % 60000) / 1000; // Remaining seconds
        long ms = millis % 1000; // Remaining milliseconds
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);
    }

    // ==================== LOAD WORDS ====================

    private void loadWords() {
        // Load ALL categories except LETTERS from Firestore
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
                                        // Only add words that have both English and Hebrew
                                        if (word.getWordEnglish() != null
                                                && word.getWordHebrew() != null
                                                && !word.getWordEnglish().isEmpty()
                                                && !word.getWordHebrew().isEmpty()) {
                                            allWords.add(word);
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        startGame(); // All categories loaded - start the game
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    // ==================== GAME LOGIC ====================

    private void startGame() {
        // Need at least 3 words to have 3 answer choices
        if (allWords.size() < 3) {
            tvLoading.setText("Not enough words! Please add at least 3 words first.");
            return;
        }

        // Shuffle words so every game is different
        Collections.shuffle(allWords);

        // Pick the first 10 words (or less if there aren't 10)
        int count = Math.min(TOTAL_QUESTIONS, allWords.size());
        questionWords = new ArrayList<>(allWords.subList(0, count));

        // Hide loading, show game elements
        tvLoading.setVisibility(View.GONE);
        tvQuestion.setVisibility(View.VISIBLE);
        btnAnswer1.setVisibility(View.VISIBLE);
        btnAnswer2.setVisibility(View.VISIBLE);
        btnAnswer3.setVisibility(View.VISIBLE);

        startTimer(); // Start the timer when the game begins
        showQuestion(); // Show the first question
    }

    private void showQuestion() {
        Word word = questionWords.get(currentQuestion);
        correctAnswer = word.getWordHebrew(); // Save the correct answer

        // Update question counter
        tvQuestionCount.setText("Question " + (currentQuestion + 1) + "/" + questionWords.size());

        // Show the English word
        tvQuestion.setText(word.getWordEnglish());

        resetButtons(); // Reset button colors and enable them

        // Build 3 answer choices
        List<String> answers = buildAnswers(word);
        btnAnswer1.setText(answers.get(0));
        btnAnswer2.setText(answers.get(1));
        btnAnswer3.setText(answers.get(2));

        btnNext.setVisibility(View.GONE); // Hide Next until user answers
    }

    private List<String> buildAnswers(Word correctWord) {
        // Creates a list of 3 answers: 1 correct + 2 random wrong ones
        List<String> answers = new ArrayList<>();
        answers.add(correctWord.getWordHebrew()); // Add correct answer

        // Build pool of wrong answers
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

    private void resetButtons() {
        // Reset buttons back to default blue and re-enable them
        int defaultColor = Color.parseColor("#1A237E");
        btnAnswer1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer1.setEnabled(true);
        btnAnswer2.setEnabled(true);
        btnAnswer3.setEnabled(true);
        btnAnswer1.setOnClickListener(v -> checkAnswer(btnAnswer1));
        btnAnswer2.setOnClickListener(v -> checkAnswer(btnAnswer2));
        btnAnswer3.setOnClickListener(v -> checkAnswer(btnAnswer3));
    }

    private void checkAnswer(Button tappedButton) {
        String tappedAnswer = tappedButton.getText().toString();

        // Disable all buttons so user can't tap again
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        if (tappedAnswer.equals(correctAnswer)) {
            // Correct - turn button green
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            score++;
            tvScore.setText("Score: " + score);
        } else {
            // Wrong - turn tapped button red, show correct answer in green
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));

            // Find and highlight the correct answer
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

        btnNext.setVisibility(View.VISIBLE); // Show Next button
    }

    // ==================== RESULTS ====================

    private void showResults() {
        // Save best score and best time to Firestore
        saveBestStats();

        String message;
        if (score == questionWords.size()) {
            message = "🔥 Perfect score! You're a Legend!";
        } else if (score >= questionWords.size() * 0.7) {
            message = "⭐ Great job! Keep it up!";
        } else if (score >= questionWords.size() * 0.5) {
            message = "👍 Not bad! Keep practicing!";
        } else {
            message = "📚 Keep studying! You'll get better!";
        }

        new AlertDialog.Builder(this)
                .setTitle("Game Over!")
                .setMessage("Your score: " + score + "/" + questionWords.size()
                        + "\nYour time: " + formatTime(elapsedTime)
                        + "\n\n" + message)
                .setPositiveButton("Play Again", (dialog, which) -> resetGame())
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void saveBestStats() {
        // Only save stats if a user is logged in
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Read current best stats from Firestore to compare
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    // Read current best score - default 0 if not set yet
                    long currentBestScore = 0;
                    if (document.exists() && document.getLong("triviaBestScore") != null) {
                        currentBestScore = document.getLong("triviaBestScore");
                    }

                    // Read current best time - default very large number if not set yet
                    // We use Long.MAX_VALUE so any real time will be better
                    long currentBestTime = Long.MAX_VALUE;
                    if (document.exists() && document.getLong("triviaBestTime") != null) {
                        currentBestTime = document.getLong("triviaBestTime");
                    }

                    // Only update if this game was better
                    // Better score = higher number
                    // Better time = lower number (finished faster)
                    boolean newBestScore = score > currentBestScore;
                    boolean newBestTime = elapsedTime < currentBestTime;

                    if (newBestScore || newBestTime) {
                        // Build update with whichever stats improved
                        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();

                        if (newBestScore) {
                            updates.put("triviaBestScore", score); // Save new best score
                        }
                        if (newBestTime) {
                            // Save time as both milliseconds (for comparison) and formatted string (for display)
                            updates.put("triviaBestTime", elapsedTime); // Raw milliseconds for comparison
                            updates.put("triviaBestTimeFormatted", formatTime(elapsedTime)); // "0:45:230" for display
                        }

                        db.collection("users").document(userId).update(updates)
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "New best! 🏆", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void resetGame() {
        // Reset everything and start a fresh game
        currentQuestion = 0;
        score = 0;
        elapsedTime = 0;
        tvScore.setText("Score: 0");
        tvTimer.setText("0:00:000");
        Collections.shuffle(allWords);
        int count = Math.min(TOTAL_QUESTIONS, allWords.size());
        questionWords = new ArrayList<>(allWords.subList(0, count));
        startTimer(); // Restart the timer
        showQuestion();
    }
}
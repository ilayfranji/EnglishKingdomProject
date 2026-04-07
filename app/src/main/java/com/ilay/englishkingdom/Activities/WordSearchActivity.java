package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open GameHistoryActivity
import android.graphics.Paint; // Used to add strikethrough effect to found words in the list
import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used to show and hide UI elements
import android.widget.LinearLayout; // Used for the word list container and each row of words
import android.widget.TextView; // Used for back button, timer, found counter, and word list items
import android.widget.Toast; // Used to show messages to the user

import androidx.appcompat.app.AlertDialog; // Used to show the win dialog at the end
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to load words and save stats
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single Firestore document
import com.ilay.englishkingdom.Models.CategoryType; // Used to filter only WORDS type categories
import com.ilay.englishkingdom.R; // Used to reference XML resources
import com.ilay.englishkingdom.Views.WordSearchGridView; // Our custom grid view

import java.text.SimpleDateFormat; // Used to format the current date and time for game history
import java.util.ArrayList; // Used for word lists
import java.util.Collections; // Used to shuffle words so grid is different every game
import java.util.Date; // Used to get the current date and time
import java.util.HashMap; // Used to store each word's position in the grid
import java.util.List; // The List interface
import java.util.Locale; // Used for time and date formatting
import java.util.Random; // Used to place random filler letters in empty cells

public class WordSearchActivity extends AppCompatActivity
        implements WordSearchGridView.OnWordSelectedListener {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow to go back to PracticeActivity
    private TextView tvFoundCount; // Shows "Found: 3/8" so user knows their progress
    private TextView tvLoading; // Shows "Loading..." while words are being fetched from Firestore
    private TextView tvTimer; // Shows the elapsed time e.g. "1:23:456"
    private WordSearchGridView gridView; // Our custom view that draws the grid and handles touch
    private LinearLayout wordListContainer; // The container below the grid that holds all word TextViews

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Used to get the current user for saving best time and history

    // ==================== GAME DATA ====================

    private static final int GRID_SIZE = 12; // The grid is always 12x12 cells
    private static final int MAX_WORDS = 12; // Maximum number of words to show in the grid
    private char[][] grid = new char[GRID_SIZE][GRID_SIZE]; // The 2D array of letters - grid[row][col]
    private List<String> wordsToFind = new ArrayList<>(); // All words successfully placed in the grid
    private List<String> foundWords = new ArrayList<>(); // Words the user has found so far
    private List<TextView> wordTextViews = new ArrayList<>(); // One TextView per word in the list below grid
    // Key = the word string, Value = int array [startRow, startCol, endRow, endCol]
    // We save each word's position so we can mark its cells green when it's found
    private HashMap<String, int[]> wordPositions = new HashMap<>();

    // ==================== GAME START TIME ====================

    // We save the date and time when the game started so we can show it in the history
    private String gameStartDate = ""; // e.g. "28/03/2026"
    private String gameStartTime = ""; // e.g. "17:45"

    // ==================== TIMER ====================

    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // Handler runs code on the main thread - we need this because only the main thread can update UI

    private long startTime = 0; // The system time in milliseconds when the timer started
    private long elapsedTime = 0; // How many milliseconds have passed since the timer started
    private boolean timerRunning = false; // true = timer is currently ticking

    // Runnable = a block of code that can be scheduled to run later
    // This one updates the timer display every 10 milliseconds
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime; // Calculate elapsed time
            tvTimer.setText(formatTime(elapsedTime)); // Update the timer text on screen
            timerHandler.postDelayed(this, 10); // Run again in 10ms to keep the timer ticking
        }
    };

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_search);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvBack = findViewById(R.id.tvBack);
        tvFoundCount = findViewById(R.id.tvFoundCount);
        tvLoading = findViewById(R.id.tvLoading);
        tvTimer = findViewById(R.id.tvTimer);
        gridView = findViewById(R.id.wordSearchGrid);
        wordListContainer = findViewById(R.id.wordListContainer);

        tvBack.setOnClickListener(v -> {
            stopTimer(); // Stop the timer when leaving so it doesn't keep running in background
            finish();
        });

        // Register this activity as the listener for when user selects letters
        gridView.setOnWordSelectedListener(this);

        loadWords(); // Start loading words from Firestore
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // Always stop the timer when activity is destroyed to prevent memory leaks
    }

    // ==================== TIMER METHODS ====================

    private void startTimer() {
        // Save the current date and time when the game starts
        // This will be saved to the game history later
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date(); // Get the current date and time
        gameStartDate = dateFormat.format(now); // e.g. "28/03/2026"
        gameStartTime = timeFormat.format(now); // e.g. "17:45"

        startTime = System.currentTimeMillis(); // Save when the timer started
        timerRunning = true;
        timerHandler.post(timerRunnable); // Start the ticking
    }

    private void stopTimer() {
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable); // Cancel any pending timer updates
    }

    private String formatTime(long millis) {
        // Converts milliseconds into "0:00:000" format
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);
    }

    // ==================== LOAD WORDS ====================

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

                    List<String> allWordsList = new ArrayList<>();

                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        String english = wordDoc.getString("wordEnglish");
                                        // Only add single words that fit in the grid
                                        if (english != null
                                                && !english.isEmpty()
                                                && !english.contains(" ")
                                                && english.length() <= GRID_SIZE) {
                                            allWordsList.add(english.toLowerCase());
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        buildGrid(allWordsList); // All categories loaded - build grid
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    // ==================== GRID BUILDING ====================

    private void buildGrid(List<String> allWords) {
        // אם אין מילים אז מציגים שגיאה
        if (allWords.isEmpty()) {
            tvLoading.setText("No words found! Please add some words first.");
            return;
        }

        Collections.shuffle(allWords); // Shuffle so a different set appears each game

        // Initialize every cell with empty space
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = ' ';
            }
        }

        // FIX FOR 11 WORDS BUG:
        // The old code took the first 12 words and tried to place them
        // but some words couldn't fit so we ended up with fewer than 12
        // The new code keeps trying words from the full shuffled list
        // until we successfully place exactly 12 words or run out of words to try
        // This guarantees we always show the maximum possible number of words
        int wordsPlaced = 0; // How many words we have successfully placed so far
        for (String word : allWords) {
            if (wordsPlaced >= MAX_WORDS) break; // Stop once we have 12 words placed
            if (placeWord(word)) {
                wordsToFind.add(word); // Only add to the find list if it was actually placed
                wordsPlaced++; // Count this as a successfully placed word
            }
        }

        fillEmptyCells(); // Fill remaining empty cells with random letters

        // Hide loading and show the grid
        tvLoading.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        gridView.setGrid(grid, GRID_SIZE);

        buildWordList(); // Build the word list below the grid

        tvFoundCount.setText("Found: 0/" + wordsToFind.size());

        startTimer(); // Start the timer now that the game is ready
    }

    private boolean placeWord(String word) {
        // Tries to place a word in the grid horizontally or vertically
        // Returns true if successful, false if no valid position found
        int[][] directions = {
                {0, 1},  // Right →
                {1, 0},  // Down ↓
        };

        List<int[]> dirList = new ArrayList<>();
        for (int[] d : directions) dirList.add(d);
        Collections.shuffle(dirList); // Shuffle so direction is random

        Random random = new Random();

        for (int[] dir : dirList) {
            for (int attempt = 0; attempt < 50; attempt++) {
                int startRow = random.nextInt(GRID_SIZE);
                int startCol = random.nextInt(GRID_SIZE);

                if (canPlace(word, startRow, startCol, dir[0], dir[1])) {
                    // Place the word letter by letter
                    int r = startRow;
                    int c = startCol;
                    for (char letter : word.toCharArray()) {
                        grid[r][c] = letter;
                        r += dir[0];
                        c += dir[1];
                    }

                    // Save start and end position so we can mark it green when found
                    int endRow = startRow + dir[0] * (word.length() - 1);
                    int endCol = startCol + dir[1] * (word.length() - 1);
                    wordPositions.put(word, new int[]{startRow, startCol, endRow, endCol});
                    return true;
                }
            }
        }
        return false; // Couldn't place this word anywhere
    }

    private boolean canPlace(String word, int startRow, int startCol, int rowDir, int colDir) {
        // Checks if a word fits at the given position without going out of bounds
        // or conflicting with a different letter that's already placed
        int r = startRow;
        int c = startCol;

        for (char letter : word.toCharArray()) {
            if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) return false; // Out of bounds
            if (grid[r][c] != ' ' && grid[r][c] != letter) return false; // Conflicts with existing letter
            r += rowDir;
            c += colDir;
        }
        return true;
    }

    private void fillEmptyCells() {
        // Fill all remaining empty cells with random letters to hide the words
        Random random = new Random();
        String letters = "abcdefghijklmnopqrstuvwxyz";

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] == ' ') {
                    grid[r][c] = letters.charAt(random.nextInt(letters.length()));
                }
            }
        }
    }

    // ==================== WORD LIST ====================

    private void buildWordList() {
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

        for (int i = 0; i < wordsToFind.size(); i++) {
            if (i % 3 == 0) { // Start a new row every 3 words
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                wordListContainer.addView(currentRow);
            }

            String word = wordsToFind.get(i);

            TextView tv = new TextView(this);
            tv.setText(word.toUpperCase()); // Show uppercase to match the grid
            tv.setTextColor(0xFFFFFFFF); // White
            tv.setTextSize(13);
            tv.setPadding(8, 4, 8, 4);

            // Equal width for all 3 words in a row using weight
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(params);

            currentRow.addView(tv);
            wordTextViews.add(tv); // Save reference so we can strike through when found
        }
    }

    // ==================== WORD SELECTION ====================

    @Override
    public void onWordSelected(String selectedWord) {
        // Called by WordSearchGridView when the user lifts their finger after dragging
        if (wordsToFind.contains(selectedWord) && !foundWords.contains(selectedWord)) {
            wordFound(selectedWord); // Match found!
        } else {
            // The selected letters don't match any hidden word
            // Only show the toast if the user selected more than one letter
            // so we don't show "Word not found" for every single tap
            if (selectedWord.length() > 1) {
                Toast.makeText(this, "Word not found! Try again 🔍", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void wordFound(String word) {
        foundWords.add(word); // Add to found list

        // Strike through the word in the list below the grid
        for (int i = 0; i < wordsToFind.size(); i++) {
            if (wordsToFind.get(i).equals(word)) {
                TextView tv = wordTextViews.get(i);
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); // Strikethrough
                tv.setTextColor(0xFF7986CB); // Change to grey-blue to show it's been found
                break;
            }
        }

        // Tell the grid view to mark this word's cells green
        int[] pos = wordPositions.get(word);
        if (pos != null) {
            gridView.markWordAsFound(pos[0], pos[1], pos[2], pos[3]);
        }

        tvFoundCount.setText("Found: " + foundWords.size() + "/" + wordsToFind.size());
        Toast.makeText(this, "Found: " + word.toUpperCase() + " ✅", Toast.LENGTH_SHORT).show();

        // Check if all words have been found
        if (foundWords.size() == wordsToFind.size()) {
            stopTimer(); // Game over - stop the timer
            showWinDialog();
        }
    }

    // ==================== WIN DIALOG ====================

    private void showWinDialog() {
        saveBestTime(); // Save best time if this game was faster
        saveGameHistory(); // Save this game to the history

        new AlertDialog.Builder(this)
                .setTitle("You found all the words!")
                .setMessage("Amazing! You found all " + wordsToFind.size() + " words!"
                        + "\nYour time: " + formatTime(elapsedTime))
                .setPositiveButton("Play Again", (dialog, which) -> resetGame())
                .setNegativeButton("Exit", (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    // ==================== SAVE BEST TIME ====================

    private void saveBestTime() {
        if (mAuth.getCurrentUser() == null) return; // Guest - skip

        String userId = mAuth.getCurrentUser().getUid();

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    long currentBestTime = Long.MAX_VALUE; // Default to max so any time is better
                    if (document.exists() && document.getLong("wordSearchBestTime") != null) {
                        currentBestTime = document.getLong("wordSearchBestTime");
                    }

                    if (elapsedTime < currentBestTime) {
                        // This game was faster - save as new best time
                        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
                        updates.put("wordSearchBestTime", elapsedTime); // Raw ms for comparison
                        updates.put("wordSearchBestTimeFormatted", formatTime(elapsedTime)); // For display

                        db.collection("users").document(userId).update(updates)
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "New best time!", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ==================== SAVE GAME HISTORY ====================

    private void saveGameHistory() {
        // Save this game to the history so the user can see it later
        // We only save for logged in users - guests have no history
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Build the game history entry
        // Each entry has the game type, date, time, result and duration
        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();
        historyEntry.put("type", "WORDSEARCH"); // So we know which game this was
        historyEntry.put("date", gameStartDate); // e.g. "28/03/2026"
        historyEntry.put("time", gameStartTime); // e.g. "17:45"
        historyEntry.put("wordsFound", foundWords.size() + "/" + wordsToFind.size()); // e.g. "8/11"
        historyEntry.put("duration", formatTime(elapsedTime)); // e.g. "1:23:456"
        historyEntry.put("timestamp", System.currentTimeMillis()); // Raw number for sorting by date

        // Save under users/[userId]/gameHistory/ with an auto-generated document ID
        // add() creates a new document with a random ID each time
        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry); // No success/failure listener needed - history saving is silent
    }

    // ==================== RESET GAME ====================

    private void resetGame() {
        // Reset everything and start a fresh game
        wordsToFind.clear();
        foundWords.clear();
        wordPositions.clear();
        wordTextViews.clear();
        elapsedTime = 0;
        tvTimer.setText("0:00:000");
        grid = new char[GRID_SIZE][GRID_SIZE];
        tvLoading.setVisibility(View.VISIBLE);
        gridView.setVisibility(View.GONE);
        wordListContainer.removeAllViews();
        loadWords(); // Reload words and build a fresh grid
    }
}
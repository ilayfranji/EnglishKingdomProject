package com.ilay.englishkingdom.Activities;

import android.graphics.Paint; // Used to add strikethrough effect to found words in the list
import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used to show and hide UI elements
import android.widget.LinearLayout; // Used for the word list container and each row of words
import android.widget.TextView; // Used for back button, timer, found counter, and word list items
import android.widget.Toast; // Used to show "Word found!" message

import androidx.appcompat.app.AlertDialog; // Used to show the win dialog at the end
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.google.firebase.auth.FirebaseAuth; // Used to get the current logged in user
import com.google.firebase.firestore.FirebaseFirestore; // Used to load words and save best time
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single Firestore document
import com.ilay.englishkingdom.Models.CategoryType; // Used to filter only WORDS type categories
import com.ilay.englishkingdom.R; // Used to reference XML resources
import com.ilay.englishkingdom.Views.WordSearchGridView; // Our custom grid view that draws and handles touch

import java.util.ArrayList; // Used for word lists
import java.util.Collections; // Used to shuffle words so grid is different every game
import java.util.HashMap; // Used to store each word's position in the grid
import java.util.List; // The List interface
import java.util.Locale; // Used for time formatting
import java.util.Random; // Used to place random filler letters in empty cells

public class WordSearchActivity extends AppCompatActivity
        implements WordSearchGridView.OnWordSelectedListener {
    // AppCompatActivity = this is a screen
    // implements OnWordSelectedListener = this class handles the event when user selects letters
    // When user lifts finger, WordSearchGridView calls onWordSelected() on this class

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow to go back to PracticeActivity
    private TextView tvFoundCount; // Shows "Found: 3/8" so user knows their progress
    private TextView tvLoading; // Shows "Loading..." while words are being fetched from Firestore
    private TextView tvTimer; // Shows the elapsed time e.g. "1:23:456"
    private WordSearchGridView gridView; // Our custom view that draws the grid and handles touch
    private LinearLayout wordListContainer; // The container below the grid that holds all word TextViews

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our connection to the Firestore database
    private FirebaseAuth mAuth; // Used to get the current user for saving best time

    // ==================== GAME DATA ====================

    private static final int GRID_SIZE = 12; // The grid is always 12x12 cells
    private char[][] grid = new char[GRID_SIZE][GRID_SIZE]; // The 2D array of letters - grid[row][col]
    private List<String> wordsToFind = new ArrayList<>(); // All words hidden in the grid
    private List<String> foundWords = new ArrayList<>(); // Words the user has found so far
    private List<TextView> wordTextViews = new ArrayList<>(); // One TextView per word in the list below grid
    // Key = the word string, Value = int array [startRow, startCol, endRow, endCol]
    // We save each word's position so we can mark its cells green when it's found
    private HashMap<String, int[]> wordPositions = new HashMap<>();

    // ==================== TIMER ====================

    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    // Handler runs code on the main thread - we need this because only the main thread can update UI
    // Looper.getMainLooper() makes sure it runs on the main thread not a background thread

    private long startTime = 0; // The system time in milliseconds when the timer started
    private long elapsedTime = 0; // How many milliseconds have passed since the timer started
    private boolean timerRunning = false; // true = timer is currently ticking

    // Runnable = a block of code that can be scheduled to run later
    // This one updates the timer display every 10 milliseconds
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            // Calculate how much time has passed since we started
            elapsedTime = System.currentTimeMillis() - startTime;
            tvTimer.setText(formatTime(elapsedTime)); // Update the timer text on screen
            // Schedule this same block to run again in 10ms - this creates the ticking effect
            timerHandler.postDelayed(this, 10);
        }
    };

    // ==================== LIFECYCLE ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Always call super first - required by Android
        setContentView(R.layout.activity_word_search); // Connect this Java file to activity_word_search.xml

        // Initialize Firebase connections
        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        // Connect each Java variable to its XML view using the ID we gave in XML
        tvBack = findViewById(R.id.tvBack);
        tvFoundCount = findViewById(R.id.tvFoundCount);
        tvLoading = findViewById(R.id.tvLoading);
        tvTimer = findViewById(R.id.tvTimer);
        gridView = findViewById(R.id.wordSearchGrid);
        wordListContainer = findViewById(R.id.wordListContainer);

        // Back arrow - stop timer and go back to PracticeActivity
        tvBack.setOnClickListener(v -> {
            stopTimer(); // Always stop the timer when leaving so it doesn't keep running in background
            finish(); // Close this activity
        });

        // Register this activity as the listener for when user selects letters
        // When user lifts finger, gridView will call our onWordSelected() method
        gridView.setOnWordSelectedListener(this);

        // Start loading words from Firestore - grid will be built after loading finishes
        loadWords();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy(); // Always call super first
        stopTimer(); // Stop the timer when activity is destroyed to prevent memory leaks
        // Memory leak = timer keeps running and holding a reference to this activity even after it's gone
    }

    // ==================== TIMER METHODS ====================

    private void startTimer() {
        // Starts the timer from 0
        startTime = System.currentTimeMillis(); // Save the current system time as our reference point
        timerRunning = true; // Mark timer as running
        timerHandler.post(timerRunnable); // Start running the timer runnable immediately
    }

    private void stopTimer() {
        // Stops the timer from ticking
        timerRunning = false; // Mark timer as not running
        timerHandler.removeCallbacks(timerRunnable); // Cancel any pending timer updates
    }

    private String formatTime(long millis) {
        // Converts a millisecond count into a readable "0:00:000" format
        // e.g. 75230ms = 1 minute, 15 seconds, 230 milliseconds = "1:15:230"
        long minutes = millis / 60000; // 1 minute = 60,000 milliseconds
        long seconds = (millis % 60000) / 1000; // Remaining seconds after removing full minutes
        long ms = millis % 1000; // Remaining milliseconds after removing full seconds
        // %d = integer, %02d = at least 2 digits padded with 0, %03d = at least 3 digits
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);
    }

    // ==================== LOAD WORDS ====================

    private void loadWords() {
        // Loads all words from WORDS type categories only
        // We skip LETTERS and SENTENCES because single letters and long sentences
        // don't work well in a word search grid
        db.collection("categories")
                .whereEqualTo("categoryType", CategoryType.WORDS.name()) // Only get WORDS categories
                .get() // One time fetch - we don't need real time updates here
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {categories.size()}; // Array so we can modify inside lambda
                    // We use int[] instead of int because Java lambdas can't modify regular local variables

                    if (categoriesLeft[0] == 0) {
                        // No WORDS categories found - tell the user
                        tvLoading.setText("No words found! Please add some words first.");
                        return;
                    }

                    List<String> allWordsList = new ArrayList<>(); // Collects all English words from all categories

                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        // Load the words sub-collection for this category
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        String english = wordDoc.getString("wordEnglish");
                                        // Only add words that:
                                        // - Are not null or empty
                                        // - Have no spaces (multi-word phrases don't work in word search)
                                        // - Are short enough to fit in our 12x12 grid
                                        if (english != null
                                                && !english.isEmpty()
                                                && !english.contains(" ")
                                                && english.length() <= GRID_SIZE) {
                                            allWordsList.add(english.toLowerCase()); // Store as lowercase
                                        }
                                    }

                                    categoriesLeft[0]--; // We finished loading this category

                                    if (categoriesLeft[0] == 0) {
                                        // All categories loaded - now build the grid
                                        buildGrid(allWordsList);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again."));
    }

    // ==================== GRID BUILDING ====================

    private void buildGrid(List<String> allWords) {
        // Takes the full list of words and builds the 12x12 letter grid
        if (allWords.isEmpty()) {
            tvLoading.setText("No words found! Please add some words first.");
            return;
        }

        Collections.shuffle(allWords); // Shuffle so a different set of words appears each game

        // Limit to maximum 12 words so the grid doesn't get too crowded
        // We take the first 12 after shuffling so it's always a random 12
        if (allWords.size() > 12) {
            allWords = allWords.subList(0, 12); // Keep only the first 12 words
        }

        // Initialize every cell in the grid with a space character (empty)
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = ' '; // Empty space - will be filled by word letters or random filler
            }
        }

        // Try to place each word in the grid one by one
        // Words that don't fit anywhere are simply skipped
        for (String word : allWords) {
            if (placeWord(word)) {
                wordsToFind.add(word); // Only add to find list if word was actually placed
            }
        }

        // Fill all remaining empty cells with random letters
        // This hides the words and makes the grid look full
        fillEmptyCells();

        // Hide loading text and show the grid
        tvLoading.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        gridView.setGrid(grid, GRID_SIZE); // Give the grid data to our custom view so it can draw it

        // Build the word list below the grid
        buildWordList();

        // Show initial found counter
        tvFoundCount.setText("Found: 0/" + wordsToFind.size());

        // Start the timer now that the grid is ready and user can start playing
        startTimer();
    }

    private boolean placeWord(String word) {
        // Tries to place a word somewhere in the grid
        // Returns true if placement succeeded, false if no valid position was found
        // Only horizontal and vertical directions are used - no diagonals

        // All 4 possible directions - horizontal and vertical only
        // Each int[] is {rowDirection, colDirection}
        // rowDir: -1=up, 0=same row, 1=down
        // colDir: -1=left, 0=same column, 1=right
        int[][] directions = {
                {0, 1},  // Right → only
                {1, 0},  // Down ↓ only
        };

        // Put directions in a list so we can shuffle them
        // Shuffling means each word gets placed in a random direction
        List<int[]> dirList = new ArrayList<>();
        for (int[] d : directions) dirList.add(d);
        Collections.shuffle(dirList);

        Random random = new Random();

        // For each direction try 50 random starting positions
        // If none work move to the next direction
        for (int[] dir : dirList) {
            for (int attempt = 0; attempt < 50; attempt++) {
                // Pick a random starting cell
                int startRow = random.nextInt(GRID_SIZE);
                int startCol = random.nextInt(GRID_SIZE);

                // Check if the word fits starting from this position in this direction
                if (canPlace(word, startRow, startCol, dir[0], dir[1])) {
                    // It fits! Place each letter into the grid
                    int r = startRow;
                    int c = startCol;
                    for (char letter : word.toCharArray()) {
                        grid[r][c] = letter; // Write the letter into the grid cell
                        r += dir[0]; // Move to next row position
                        c += dir[1]; // Move to next column position
                    }

                    // Calculate where the word ends so we can save its position
                    int endRow = startRow + dir[0] * (word.length() - 1);
                    int endCol = startCol + dir[1] * (word.length() - 1);

                    // Save the word's start and end so we can mark it green when found
                    wordPositions.put(word, new int[]{startRow, startCol, endRow, endCol});
                    return true; // Word placed successfully
                }
            }
        }
        return false; // Tried all directions and positions - couldn't place this word
    }

    private boolean canPlace(String word, int startRow, int startCol, int rowDir, int colDir) {
        // Checks if a word can be placed starting at startRow,startCol in direction rowDir,colDir
        // Returns false if word goes out of bounds or conflicts with a different existing letter
        int r = startRow;
        int c = startCol;

        for (char letter : word.toCharArray()) {
            // Check grid bounds - word must stay within the 12x12 grid
            if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) return false;

            // Check for conflicts - cell must be empty OR already have the same letter
            // Same letter is ok because two words can share a cell if they have the same letter there
            if (grid[r][c] != ' ' && grid[r][c] != letter) return false;

            r += rowDir; // Move to next position
            c += colDir;
        }
        return true; // No issues - word can be placed here
    }

    private void fillEmptyCells() {
        // Fills all remaining empty cells with random letters
        // This hides the words so the user actually has to search for them
        Random random = new Random();
        String letters = "abcdefghijklmnopqrstuvwxyz"; // Pool of letters to pick from

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] == ' ') { // Only fill cells that are still empty
                    grid[r][c] = letters.charAt(random.nextInt(letters.length())); // Pick a random letter
                }
            }
        }
    }

    // ==================== WORD LIST ====================

    private void buildWordList() {
        // Creates a TextView for each hidden word and adds them below the grid
        // When a word is found its TextView gets a strikethrough effect
        wordTextViews.clear(); // Clear any old references
        wordListContainer.removeAllViews(); // Remove any old views from the container

        // Add a header label above the word list
        TextView header = new TextView(this);
        header.setText("Find these words:"); // Header text
        header.setTextColor(0xFFFFD700); // Gold color - 0xFF = fully opaque, then RGB hex
        header.setTextSize(14); // Font size in sp
        header.setPadding(8, 8, 8, 8); // Padding around the text
        wordListContainer.addView(header); // Add to the container

        // We show 3 words per row to save vertical space
        LinearLayout currentRow = null; // Holds the current horizontal row we're filling

        for (int i = 0; i < wordsToFind.size(); i++) {

            // Start a new row every 3 words
            // % is modulo - gives the remainder of division - 0%3=0, 3%3=0, 6%3=0
            if (i % 3 == 0) {
                currentRow = new LinearLayout(this); // Create a new horizontal row
                currentRow.setOrientation(LinearLayout.HORIZONTAL); // Arrange children side by side
                wordListContainer.addView(currentRow); // Add the row to the main container
            }

            String word = wordsToFind.get(i); // Get the word for this position

            // Create a TextView for this word
            TextView tv = new TextView(this);
            tv.setText(word.toUpperCase()); // Show uppercase so it matches the grid letters
            tv.setTextColor(0xFFFFFFFF); // White color
            tv.setTextSize(13); // Slightly smaller than the header
            tv.setPadding(8, 4, 8, 4); // Small padding around each word

            // layout_weight=1 makes all 3 words in a row share equal width automatically
            // layout_width=0 is required when using weight - means "let weight decide the width"
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, // Width = 0, weight system decides the width
                    LinearLayout.LayoutParams.WRAP_CONTENT, // Height = wrap content
                    1f); // Weight = 1, all 3 words get equal space
            tv.setLayoutParams(params);

            currentRow.addView(tv); // Add word TextView to the current row
            wordTextViews.add(tv); // Save reference so we can strike through it when found
        }
    }

    // ==================== WORD SELECTION ====================

    @Override
    public void onWordSelected(String selectedWord) {
        // Called by WordSearchGridView when the user lifts their finger after dragging
        // selectedWord is the string of letters the user selected, already in lowercase

        // Check if the selected letters match any hidden word
        // We only check forward direction - reversed words are not supported
        if (wordsToFind.contains(selectedWord) && !foundWords.contains(selectedWord)) {
            wordFound(selectedWord); // Match found!
        }
    }

    private void wordFound(String word) {
        // Called when user successfully finds a word
        foundWords.add(word); // Add to our found list

        // Find this word's TextView in the list and add strikethrough
        for (int i = 0; i < wordsToFind.size(); i++) {
            if (wordsToFind.get(i).equals(word)) {
                TextView tv = wordTextViews.get(i);
                // STRIKE_THRU_TEXT_FLAG adds a line through the middle of the text
                // | is bitwise OR - adds the flag without removing any existing flags
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                tv.setTextColor(0xFF7986CB); // Change to grey-blue to show it's been found
                break; // Found the right TextView - stop looping
            }
        }

        // Tell the grid view to mark this word's cells as found (turn them green)
        int[] pos = wordPositions.get(word); // Get [startRow, startCol, endRow, endCol]
        if (pos != null) {
            gridView.markWordAsFound(pos[0], pos[1], pos[2], pos[3]); // Mark cells green
        }

        // Update the found counter
        tvFoundCount.setText("Found: " + foundWords.size() + "/" + wordsToFind.size());

        // Show a short congratulations message
        Toast.makeText(this, "✅ Found: " + word.toUpperCase(), Toast.LENGTH_SHORT).show();

        // Check if all words have been found
        if (foundWords.size() == wordsToFind.size()) {
            stopTimer(); // Stop the timer - game is over
            showWinDialog(); // Show congratulations popup
        }
    }

    // ==================== WIN DIALOG ====================

    private void showWinDialog() {
        saveBestTime(); // Save best time to Firestore before showing the dialog

        new AlertDialog.Builder(this)
                .setTitle("🎉 You found all the words!")
                .setMessage("Amazing! You found all " + wordsToFind.size() + " words!"
                        + "\nYour time: " + formatTime(elapsedTime)) // Show finishing time
                .setPositiveButton("Play Again", (dialog, which) -> resetGame()) // Start new game
                .setNegativeButton("Exit", (dialog, which) -> finish()) // Go back to PracticeActivity
                .setCancelable(false) // User must tap a button - can't dismiss by tapping outside
                .show();
    }

    // ==================== SAVE BEST TIME ====================

    private void saveBestTime() {
        // Saves best time to Firestore only if this game was faster than previous best
        if (mAuth.getCurrentUser() == null) return; // Not logged in - skip

        String userId = mAuth.getCurrentUser().getUid();

        // Read current best time from Firestore to compare
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    // Default to Long.MAX_VALUE so ANY real game time is automatically better
                    // Long.MAX_VALUE is the largest possible long number
                    long currentBestTime = Long.MAX_VALUE;
                    if (document.exists() && document.getLong("wordSearchBestTime") != null) {
                        currentBestTime = document.getLong("wordSearchBestTime"); // Read saved best time
                    }

                    // Only update if this time is better (lower = faster = better)
                    if (elapsedTime < currentBestTime) {
                        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
                        updates.put("wordSearchBestTime", elapsedTime); // Raw ms for future comparison
                        updates.put("wordSearchBestTimeFormatted", formatTime(elapsedTime)); // For display

                        db.collection("users").document(userId).update(updates)
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "New best time! 🏆", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    // ==================== RESET GAME ====================

    private void resetGame() {
        // Resets everything and starts a completely fresh game
        wordsToFind.clear(); // Clear the list of words to find
        foundWords.clear(); // Clear the list of found words
        wordPositions.clear(); // Clear saved word positions
        wordTextViews.clear(); // Clear saved TextView references
        elapsedTime = 0; // Reset elapsed time to 0
        tvTimer.setText("0:00:000"); // Reset timer display
        grid = new char[GRID_SIZE][GRID_SIZE]; // Create a fresh empty grid
        tvLoading.setVisibility(View.VISIBLE); // Show loading text again
        gridView.setVisibility(View.GONE); // Hide the grid while loading
        wordListContainer.removeAllViews(); // Clear the word list
        loadWords(); // Load words from Firestore and build a new grid
    }
}
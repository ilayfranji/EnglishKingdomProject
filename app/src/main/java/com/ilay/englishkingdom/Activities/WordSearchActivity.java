package com.ilay.englishkingdom.Activities;

import android.content.Intent;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.R;
import com.ilay.englishkingdom.Views.WordSearchGridView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class WordSearchActivity extends AppCompatActivity
        implements WordSearchGridView.OnWordSelectedListener {

    private TextView tvBack;
    private TextView tvFoundCount;
    private TextView tvLoading;
    private TextView tvTimer;
    private WordSearchGridView gridView;
    private LinearLayout wordListContainer;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private static final int GRID_SIZE = 12;
    private static final int MAX_WORDS = 12;
    private char[][] grid = new char[GRID_SIZE][GRID_SIZE];
    private List<String> wordsToFind = new ArrayList<>();
    private List<String> foundWords = new ArrayList<>();
    private List<TextView> wordTextViews = new ArrayList<>();
    private HashMap<String, int[]> wordPositions = new HashMap<>();


    private String gameStartDate = "";
    private String gameStartTime = "";


    private android.os.Handler timerHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    private long startTime = 0;
    private long elapsedTime = 0;
    private boolean timerRunning = false;

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime; // Calculate elapsed time
            tvTimer.setText(formatTime(elapsedTime)); // Update the timer text on screen
            timerHandler.postDelayed(this, 10); // Run again in 10ms to keep the timer ticking
        }
    };


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

        tvBack.setOnClickListener(v -> showBackConfirmation());

        // Register this activity as the listener for when user selects letters
        gridView.setOnWordSelectedListener(this);

        loadWords(); // Start loading words from Firestore
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // Always stop the timer when activity is destroyed to prevent memory leaks
    }


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

    private void loadWords() {
        // טעינת כל המילים מסוג מילה בלבד ממאגר הנתונים
        db.collection("categories")
                .whereEqualTo("categoryType", CategoryType.WORDS.name())
                .get()
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {categories.size()};

                    if (categoriesLeft[0] == 0) {
                        tvLoading.setText("No words found! Please add some words first");
                        return;
                    }

                    List<String> allWordsList = new ArrayList<>();

                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        String english = wordDoc.getString("wordEnglish");
                                        // רק מילים שהאורך שלהן קטן מאורך הרשת
                                        if (english != null
                                                && !english.isEmpty()
                                                && !english.contains(" ")
                                                && english.length() <= GRID_SIZE) {
                                            allWordsList.add(english.toLowerCase());
                                        }
                                    }

                                    categoriesLeft[0]--;

                                    if (categoriesLeft[0] == 0) {
                                        buildGrid(allWordsList); // נטענו כל המילים אפשר עכשיו לבנות את הרשת
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading words. Please try again"));
    }


    private void buildGrid(List<String> allWords) {
        // אם אין מילים אז מציגים שגיאה
        if (allWords.isEmpty()) {
            tvLoading.setText("No words found! Please add some words first");
            return;
        }

        Collections.shuffle(allWords);

        // אתחול כל התאים עם תווים ריקים
        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                grid[r][c] = ' ';
            }
        }

        int wordsPlaced = 0; // ספירת המילים שהצלחנו לשים בתפזורת
        for (String word : allWords) {
            if (wordsPlaced >= MAX_WORDS) break; // ברגע שמיקמנו 12 מילים נעצור
            if (placeWord(word)) {// אם הצלחנו למקם את המילה
                wordsToFind.add(word); // שומרים אותה ברשימת המילים שיהיו בתפזורת
                wordsPlaced++; // מעדכנים את מספר המילים שהצלחנו לשים בתפזורת
            }
        }

        fillEmptyCells(); // קריאה למתודה


        tvLoading.setVisibility(View.GONE);
        gridView.setVisibility(View.VISIBLE);
        gridView.setGrid(grid, GRID_SIZE);// בניית הרשת הויזואלית

        buildWordList(); // בניית מחסן מילים

        tvFoundCount.setText("Found: 0/" + wordsToFind.size());

        startTimer(); // הפעלת הטיימר
    }

    private boolean placeWord(String word) {
        //מנסה לשים מילה ברשת, מחזירה אמת אם אפשר שקר אם אי אפשר
        int[][] directions = {
                {0, 1},  // ימין
                {1, 0},  // למטה
        };

        List<int[]> dirList = new ArrayList<>();
        for (int[] d : directions) dirList.add(d);// העתקת רשימת הכיוונים
        Collections.shuffle(dirList);

        Random random = new Random();

        for (int[] dir : dirList) {// בחירת כיוון, אם לא ניתן למקם בכיוון הזה נמקם בכיוון השני
            for (int attempt = 0; attempt < 50; attempt++) {
                int startRow = random.nextInt(GRID_SIZE);
                int startCol = random.nextInt(GRID_SIZE);

                if (canPlace(word, startRow, startCol, dir[0], dir[1])) {//קורא לפונק עזר ובודק אם ניתן לשים את המילה
                    // מיקום המילה אות אחרי אות
                    int r = startRow;
                    int c = startCol;
                    for (char letter : word.toCharArray()) {
                        grid[r][c] = letter;//שם את האות הנוכחית במשבצת
                        r += dir[0];// מתקדם לשורה הבאה אם הכיוון הוא למטה
                        c += dir[1];//מתקדם לעמודה הבאה אם הכיוון הוא ימינה
                    }

                    // שמירת מיקום ההתחלה והסיום
                    int endRow = startRow + dir[0] * (word.length() - 1);
                    int endCol = startCol + dir[1] * (word.length() - 1);
                    wordPositions.put(word, new int[]{startRow, startCol, endRow, endCol});
                    return true;//הצלחנו למקם את המילה
                }
            }
        }
        return false; // לא הצלחנו למקם את המילה
    }

    private boolean canPlace(String word, int startRow, int startCol, int rowDir, int colDir) {
        //בדיקה אם מילה יכולה להיכנס במיקום מסויים שנתנו לה על הרשת
        //מבליח לצאת מגבולות הרשת או לעלות על מילה אחרת
        int r = startRow;
        int c = startCol;

        for (char letter : word.toCharArray()) {
            if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) return false; // יצאנו מגבולות הרשת
            if (grid[r][c] != ' ' && grid[r][c] != letter) return false; // עלינו על מילה אחרת
            r += rowDir;//נתקדם שורה
            c += colDir;//נתקדם עמודה
        }
        return true;// אם לא החזרנו false עד כאן המילה מתאימה במיקומים ששלחנו
    }

    private void fillEmptyCells() {
        // מילוי תאים ריקים עם אותיות רנדומליות
        Random random = new Random();
        String letters = "abcdefghijklmnopqrstuvwxyz";

        for (int r = 0; r < GRID_SIZE; r++) {
            for (int c = 0; c < GRID_SIZE; c++) {
                if (grid[r][c] == ' ') {// אם יש תא שאין בו אות
                    grid[r][c] = letters.charAt(random.nextInt(letters.length()));//להכניס אות רנדומלית
                }
            }
        }
    }


    private void buildWordList() {
        wordTextViews.clear();
        wordListContainer.removeAllViews();

        // יצירת המחסן
        TextView header = new TextView(this);
        header.setText("Find these words:");
        header.setTextColor(0xFFFFD700); // צבע זהב
        header.setTextSize(14);
        header.setPadding(8, 8, 8, 8);
        wordListContainer.addView(header);

        // מציגים 3 מילים בשורה
        LinearLayout currentRow = null;

        for (int i = 0; i < wordsToFind.size(); i++) {
            if (i % 3 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                wordListContainer.addView(currentRow);
            }

            String word = wordsToFind.get(i);

            TextView tv = new TextView(this);
            tv.setText(word.toUpperCase()); // באותיות גדולות
            tv.setTextColor(0xFFFFFFFF); // צבע לבן
            tv.setTextSize(13);
            tv.setPadding(8, 4, 8, 4);

            // מיקום שווה לכל מילה
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tv.setLayoutParams(params);

            currentRow.addView(tv);
            wordTextViews.add(tv); //שמירת רכיבי הטקסט של מחסן המילים ככה נוכל לשנות אותם כשנמצא אותם
        }
    }


    @Override
    public void onWordSelected(String selectedWord) {
        // נקרא על ידי הגריד וויו כשהמשתמש מרים אצבע לאחר סימון
        if (wordsToFind.contains(selectedWord) && !foundWords.contains(selectedWord)) {
            wordFound(selectedWord); // מילה נמצאה
        } else {
            //אם המילה לא נמצאה, והיא לפחות עם 2 אותיות נציג הודעה
            if (selectedWord.length() > 1) {
                Toast.makeText(this, "Word not found! Try again", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void wordFound(String word) {
        foundWords.add(word); // הוספה לרשימת המילים שנמצאו

        // סימון המילה במחסן המילים עם קו עליה
        for (int i = 0; i < wordsToFind.size(); i++) {
            if (wordsToFind.get(i).equals(word)) {
                TextView tv = wordTextViews.get(i);
                tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG); // מחיקת המילה על ידי קו
                tv.setTextColor(0xFF7986CB); // לשנות צבע
                break;
            }
        }

        // סימון המילה על הרשת בצבע ירוק
        int[] pos = wordPositions.get(word);
        if (pos != null) {
            gridView.markWordAsFound(pos[0], pos[1], pos[2], pos[3]);
        }

        tvFoundCount.setText("Found: " + foundWords.size() + "/" + wordsToFind.size());
        Toast.makeText(this, "Found: " + word.toUpperCase() + " !", Toast.LENGTH_SHORT).show();

        // אם כל המילים נמצאו
        if (foundWords.size() == wordsToFind.size()) {
            stopTimer(); // עצירת הטיימר וסיום המשחק
            showWinDialog();
        }
    }


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

    private void showBackConfirmation() {
        // Pause the timer while the dialog is open
        // so the user's time doesn't keep running while they decide
        stopTimer();

        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("If you go back now your progress will be lost. Are you sure?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    // User confirmed they want to leave - close the screen
                    finish();
                })
                .setNegativeButton("Keep Playing", (dialog, which) -> {
                    // User wants to keep playing - restart the timer from where it stopped
                    // Adjusting startTime keeps elapsedTime accurate
                    startTime = System.currentTimeMillis() - elapsedTime;
                    timerRunning = true;
                    timerHandler.post(timerRunnable);
                })
                .setCancelable(false)
                .show();
    }
}
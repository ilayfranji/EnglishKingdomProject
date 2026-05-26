package com.ilay.englishkingdom.Activities;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.Models.Word;
import com.ilay.englishkingdom.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TriviaActivity extends AppCompatActivity {


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


    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Used to get current user for saving stats
    private List<Word> allWords = new ArrayList<>(); // All words loaded from Firestore
    private List<Word> questionWords = new ArrayList<>(); // 10 random words for this game
    private int currentQuestion = 0; // Index of the current question (0-9)
    private int score = 0; // How many correct answers so far
    private String correctAnswer = ""; // The correct Hebrew answer for the current question


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

        tvBack.setOnClickListener(v -> showBackConfirmation());// כאשר לוחצים על הטקסט וויו "חזור" קורא למתודה showBackConfirmation()

        //  לחיצה על כפתור ה"Next"
        btnNext.setOnClickListener(v -> {
            currentQuestion++; // מגדיל ב-1 את מספר השאלה ומעביר לשאלה הבאה
            if (currentQuestion < questionWords.size()) {
                showQuestion(); // אם עדיין מספר השאלה שמוצג קטן מעשר נציג את השאלה
            } else { // אם הגענו לעשר שאלות
                stopTimer(); // עוצרים את הטיימר
                showResults(); // מציגים את תוצאות המשחק בדיאלוג
            }
        });

        loadWords(); // מביאים את כל המילים שיש באפליקציה מהפייר סטור לא מביאים אותיות
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // עוצרים תמיד את הטיימר כשהפעילות נהרסת כדי למנוע דליפות זיכרון
    }

    private void startTimer() {
        // Starts the timer from 0
        startTime = System.currentTimeMillis(); // Save the current time
        timerRunning = true;
        timerHandler.post(timerRunnable); // מתחיל את הטיימר על ידי הפעולה run()
    }

    private void stopTimer() {
        // Stops the timer
        timerRunning = false;
        timerHandler.removeCallbacks(timerRunnable); // עוצר את הטיימר ואת פעולות הפובט דילייד העתידיות שלו
    }

    private String formatTime(long millis) {
        // Converts milliseconds into a readable "0:00:000" format
        // e.g. 75230ms → "1:15:230"
        long minutes = millis / 60000; // 1 minute = 60000ms
        long seconds = (millis % 60000) / 1000; // Remaining seconds
        long ms = millis % 1000; // Remaining milliseconds
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);//מאפשר להציג את פורמט הטיימר ומתאים את המספרים בהתאם למיקום המכשיר בעולם
    }


    private void loadWords() {
        // Load ALL categories except LETTERS from Firestore
        db.collection("categories").get()
                .addOnSuccessListener(categories -> {
                    int[] categoriesLeft = {0};

                    // Count valid categories (skip LETTERS)
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;//בודק אם הקטגוריה היא אותיות, אם כן הוא מדלג עליה
                        categoriesLeft[0]++;// מונה את הקטגוריות שנשארו ועברו את התנאי
                    }

                    if (categoriesLeft[0] == 0) {// אם אין לנו שום קטגוריה באפליקציה - אין מילים ולכן מוצגת הודעה טוסט להוסיף מילים קודם
                        tvLoading.setText("No words found! Please add some words first");
                        return;
                    }

                    // Load words from each valid category
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;

                        String categoryId = categoryDoc.getId();// המזהה הייחודי של כל קטגוריה, העזרת זה אפשר לבצע פעולות על קטגוריה מסויימת

                        db.collection("categories").document(categoryId)
                                .collection("words").get()
                                .addOnSuccessListener(words -> {// אם הצלחנו נעבור על כל המילים שבקטגוריה הזאת
                                    for (QueryDocumentSnapshot wordDoc : words) {
                                        Word word = wordDoc.toObject(Word.class);// העברת כל הערכים שיש במאגר הנתונים לאובייקט "מילה" עם נתונים מתאימים
                                        word.setIdFS(wordDoc.getId());// השמת המזהה הייחודי של המילה באובייקט ה"מילה" שיצרנו
                                        // מוסיפים רק מילים שיש להן את במאגר הנתונים מילה באנגלית ומילה בעברית
                                        if (word.getWordEnglish() != null
                                                && word.getWordHebrew() != null
                                                && !word.getWordEnglish().isEmpty()
                                                && !word.getWordHebrew().isEmpty()) {
                                            allWords.add(word);// מוסיפים לרשימת המילים את המילה לאחר שעברה את התנאי
                                        }
                                    }

                                    categoriesLeft[0]--;// כמה קטגוריות נשארו לי שעדיין לא נטענו כל המילים שלהן. בכל פעם שסיימנו סיבוב של הלולאה מורידים את מספר הקטגוריות באחד

                                    if (categoriesLeft[0] == 0) {
                                        startGame(); // כל הקטגוריות והמילים והעלו - אפשר להתחיל את המשחק
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->// אם לא הצלחנו נציג הודעה למשתמש שהייתה תקלה
                        tvLoading.setText("Error loading words. Please try again"));
    }


    private void startGame() {
        // צריכים לפחות 3 מילים כדי להתחיל את המשחק
        if (allWords.size() < 3) {
            tvLoading.setText("Not enough words! Please add at least 3 words first");
            return;
        }

        // מערבב את הרשימה של כל המילים בטריוויה
        Collections.shuffle(allWords);

        int count = Math.min(TOTAL_QUESTIONS, allWords.size());// מספר השאלות שיופיעו במשחק טריוויה, לוקח ת המספר הקטן יותר (מבין מספר המילים ברשימה או 10)
        questionWords = new ArrayList<>(allWords.subList(0, count));// יוצר מערך חדש בתוך questionWords של השאלות לפי המשתנה count

        // Hide loading, show game elements
        tvLoading.setVisibility(View.GONE);// מעלים את טקסט ה"Loading..."
        tvQuestion.setVisibility(View.VISIBLE);//מציג את השאלה
        btnAnswer1.setVisibility(View.VISIBLE);// מציג תשובה 1
        btnAnswer2.setVisibility(View.VISIBLE);// מציג תשובה 2
        btnAnswer3.setVisibility(View.VISIBLE);//מציג תשובה 3

        startTimer(); // מפעילים את הטיימר
        showQuestion(); // מציגים שאלה
    }

    private void showQuestion() {
        Word word = questionWords.get(currentQuestion);// הולך לרשימת השאלות, לוקח משם את השאלה הנוכחית ושם את זה בword
        correctAnswer = word.getWordHebrew(); // שומרים את התשובה בעברית של המילה

        // מעדכנים את הטקסט וויו לפי מספר השאלה הנוכחית
        tvQuestionCount.setText("Question " + (currentQuestion + 1) + "/" + questionWords.size());

        // הצגת השאלה (המילה באנגלית)
        tvQuestion.setText(word.getWordEnglish());

        resetButtons(); // איפוס כפתורים

        // בונה 3 אפשרויות תשובה
        List<String> answers = buildAnswers(word);// בונה רשימה של תשובות לפי המתודה buildAnswers
        btnAnswer1.setText(answers.get(0));
        btnAnswer2.setText(answers.get(1));
        btnAnswer3.setText(answers.get(2));

        btnNext.setVisibility(View.GONE); // להחביא את כפתור "NEXT"
    }

    private List<String> buildAnswers(Word correctWord) {
        //יוצר רשימה של 3 תשובות, אחת מהן נכונה ו2 מהן שגויות
        List<String> answers = new ArrayList<>();
        answers.add(correctWord.getWordHebrew()); // מוסיפים את התשובה הנכונה לרשימה

        // Build pool of wrong answers
        List<String> wrongPool = new ArrayList<>(); // יוצרים רשימה זמנית של תשובות שגויות
        for (Word w : allWords) {//עוברים על כל המילים במאגר הנתונים
            if (!w.getWordHebrew().equals(correctWord.getWordHebrew())) {// בודקים אם המילה לא זהה לתשובה הכונה
                wrongPool.add(w.getWordHebrew());// אם כן, מוסיפים לרשימת התשובות השגויות
            }
        }

        Collections.shuffle(wrongPool); // מערבבים את רשימת התשובות השגויות

        int wrongCount = Math.min(2, wrongPool.size());// בודקים אם יש יותר ברשימת התשובות השגויות מאשר 2 תשובות. אם יש פחות מ2 תשובות כלומר תשובה אחת ברשימה תופיע רק תשוב נוספת אחת ולא 2
        for (int i = 0; i < wrongCount; i++) {// עוברים על רשימת התשובות השגויות
            answers.add(wrongPool.get(i));// מוסיפים 2 או 1 תשובות לפי הMath.min למעלה
        }

        Collections.shuffle(answers); // ערבוב רשימת התשובות והחזרתה
        return answers;
    }

    private void resetButtons() {
        // מחזיר את הכפתורים לצבע המקורי שלהם, ומאפשר ללחוץ עליהם שוב
        int defaultColor = Color.parseColor("#1A237E");
        btnAnswer1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer1.setEnabled(true);
        btnAnswer2.setEnabled(true);
        btnAnswer3.setEnabled(true);
        //הפעלת המתודה checkAnswer עבור לחיצה על אחת מהתשובות
        btnAnswer1.setOnClickListener(v -> checkAnswer(btnAnswer1));
        btnAnswer2.setOnClickListener(v -> checkAnswer(btnAnswer2));
        btnAnswer3.setOnClickListener(v -> checkAnswer(btnAnswer3));
    }

    private void checkAnswer(Button tappedButton) {
        String tappedAnswer = tappedButton.getText().toString();// שומרים את התשובה

        // הופך את הכפתורים לבלתי ניתנים ללחיצה
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        if (tappedAnswer.equals(correctAnswer)) {
            // אם התשובה זהה לתשובה הנכונה נהפוך את צבע הכפתור לירוק
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            score++; // נגדיל את התוצאה ב1
            tvScore.setText("Score: " + score);// נעדכן את הטקסט וויו
        } else {
            // אם התשובה שגויה נצבע אותה באדום
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));

            // נחפש את התשובה הנכונה ונצבע אותה בירוק
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

        btnNext.setVisibility(View.VISIBLE); // נציג את כפתור "NEXT"
    }


    private void showResults() {
        saveBestStats(); // קריאה לפעולה saveBestStats()
        saveGameHistory(); // קריאה לפעולה saveGameHistory()

        String message;
        if (score == questionWords.size()) {
            message = "Perfect score! You're a Legend!";
        } else if (score >= questionWords.size() * 0.7) {
            message = "Great job! Keep it up!";
        } else if (score >= questionWords.size() * 0.5) {
            message = "Not bad! Keep practicing!";
        } else {
            message = "Keep studying! You'll get better!";
        }

        new AlertDialog.Builder(this)// בונים אלרט דיאלוג עם כל נתוני המשחק של השחקן
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
        // שומרים נתוני משחק רק אם המשתמש מחובר
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // Read current best stats from Firestore to compare
        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    // התוצאה הטובה ביותר בברירת מחדל היא 0
                    long currentBestScore = 0;
                    if (document.exists() && document.getLong("triviaBestScore") != null) {//אם המסמך של המשתמש קיים וכבר יש בתוכו שדה triviaBestScore, שולפים אותו ומעדכנים את המשתנה בשיא האמיתי שלו מהמאגר
                        currentBestScore = document.getLong("triviaBestScore");
                    }

                    // הצבת המספר הגדול ביותר כך שכל תוצאה של זמן תהיה קטנה ממנו
                    long currentBestTime = Long.MAX_VALUE;
                    if (document.exists() && document.getLong("triviaBestTime") != null) {
                        currentBestTime = document.getLong("triviaBestTime");// שליפת הזמן הנמוך ביותר שלקח למשתמש לענות על הטריוויה, והשמה של הזמן הזה

                    }

                    boolean newBestScore = score > currentBestScore; // בודקים אם התוצאה גדולה מהתוצאה ששמורה במאגר הנתונים
                    boolean newBestTime = elapsedTime < currentBestTime;//בודקים אם הזמן קטן מהזמן ששמור במאגר הנתונים

                    if (newBestScore || newBestTime) {
                        // בונים האש מאפ ריק (כמו ארגז)
                        java.util.HashMap<String, Object> updates = new java.util.HashMap<>();

                        if (newBestScore) {// אם יש לנו שיא חדש נכניס אותו להאש מאפ
                            updates.put("triviaBestScore", score);
                        }
                        if (newBestTime) {// אם יש לנו שיא חדש נכניס אותו להאש מאפ, פעם אחת מפורמט ופעם אחת לא מפורמט
                            updates.put("triviaBestTime", elapsedTime);
                            updates.put("triviaBestTimeFormatted", formatTime(elapsedTime));
                        }

                        db.collection("users").document(userId).update(updates)// מביאים לפייר בייס את השיאים החדשים
                                .addOnSuccessListener(v ->
                                        Toast.makeText(this, "New best!", Toast.LENGTH_SHORT).show());// הצגת הודעה על שיא חדש
                    }
                });
    }

    private void resetGame() {
        // מאפסים הכל ומתחילים משחק חדש
        currentQuestion = 0;
        score = 0;
        elapsedTime = 0;
        tvScore.setText("Score: 0");
        tvTimer.setText("0:00:000");
        Collections.shuffle(allWords);
        int count = Math.min(TOTAL_QUESTIONS, allWords.size());// כמות השאלות שיהיו
        questionWords = new ArrayList<>(allWords.subList(0, count));// מייצרים שוב רשימת שאלות
        startTimer(); // מתחילים את הטיימר מחדש
        showQuestion();
    }


    private void saveGameHistory() {
        // אם אין משתמש רשום אז לא שומרים לו את היסטורית המשחקים
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();

        // לקיחת הזמן הנוכחי והתאריך הנוכחי בסוף המשחק
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date();
        String date = dateFormat.format(now); // התאריך הנוכחי בפורמט של תאריך
        String time = timeFormat.format(now); // הזמן הנוכחי בפורמט של זמן

        //בניית האש מאפ (ארגז) ריק שלתוכו יוכנסו כל
        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();
        historyEntry.put("type", "TRIVIA"); // הכנסת סוג המשחק להאש מאפ
        historyEntry.put("date", date); // הכנסת התאריך להאש מאפ
        historyEntry.put("time", time); // הכנסת השעה והדקה (הזמן) שהמשחק הסתיים בו להאש מאפ
        historyEntry.put("score", score + "/" + questionWords.size()); // הכנסת התוצאה (מתוך 10) להאש מאפ
        historyEntry.put("duration", formatTime(elapsedTime)); // הכנסת הזמן (המפורמט) שלקח לשחק את המשחק להאש מאפ
        historyEntry.put("timestamp", System.currentTimeMillis()); // הכנסת הזמן (לא המפורמט) שלקח לשחק את המשחק להאש מאפ

        // נשמר במאגר הנתונים אצל משתמש - ואז בגיים היסטורי
        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry); // הוספה למאגר הנתונים (שמירה)
    }

    private void showBackConfirmation() {
        // עוצרים את הטיימר
        stopTimer();

        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("If you go back now your progress will be lost. Are you sure?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    // בטוח שרוצה לצאת ?
                    finish();
                })
                .setNegativeButton("Keep Playing", (dialog, which) -> {
                    // החסרת הזמן שנוצל לפני שנפתח הידאלוג ובכך לא התבזבז זמן
                    startTime = System.currentTimeMillis() - elapsedTime;
                    timerRunning = true;
                    timerHandler.post(timerRunnable);
                })
                .setCancelable(false) //אי אפשר ללחוץ מחוץ לדיאלוג
                .show();
    }
}
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SpeedTriviaActivity extends AppCompatActivity {

    private TextView tvBack;
    private TextView tvScore;
    private TextView tvTimer;
    private TextView tvQuestion;
    private Button btnAnswer1;
    private Button btnAnswer2;
    private Button btnAnswer3;
    private TextView tvLoading;

    private FirebaseFirestore db; // חיבור למאגר הנתונים
    private FirebaseAuth mAuth; // לצורך שמירת תוצאות המשחק למשתמש


    private List<Word> allWords = new ArrayList<>(); //כל המילים הרלוונטיות שיהיו במשחק
    private List<Word> remainingWords = new ArrayList<>();// יוצרים רשימה ריקה (המילים שלא השתמשנו בהם במשחק עוד) ברגע שהרשימה מתרוקנת המילים יעורבבו שוב ויחזרו על עצמן
    private int score = 0;
    private String correctAnswer = ""; // התשובה הנכונה בעברית
    private boolean gameRunning = false; // true = המשחק בפעולה, false = המשחק נגמר
    private boolean waitingForNext = false; // true = מחכה 0.5 שניות לפני השאלה הבאה

    private Handler countdownHandler = new Handler(Looper.getMainLooper()); // מטפל בהרצת השעון אחורה
    private int secondsLeft = 60;

    private Runnable countdownRunnable = new Runnable() {// אובייקט המכיל את המשימה של השעון (חישוב הזמן ועדכון המסך) שנועד להרצה חוזרת בלופ
        @Override
        public void run() {
            secondsLeft--; // מוריד בשניייה את הזמן שנותר

            tvTimer.setText(String.valueOf(secondsLeft)); //מעדכן את תצוגת המסך

            // עשר שניות ומטה השעון נצבע באדום
            if (secondsLeft <= 10) {
                tvTimer.setTextColor(Color.parseColor("#C62828"));
            }

            if (secondsLeft <= 0) {
                // נגמר המשחק קורא למתודה endGame()
                gameRunning = false;
                endGame();
            } else {
                // אם נשאר עוד זמן קורא שוב לפעולה run() לאחר שנייה
                countdownHandler.postDelayed(this, 1000);
            }
        }
    };

    private Handler nextQuestionHandler = new Handler(Looper.getMainLooper());// קורא לRunnable שיפעל ויעבור לשאלה הבאה רק לאחר חצי שנייה

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

        tvBack.setOnClickListener(v -> showBackConfirmation());

        loadWords(); //טוען את המילים ממאגר הנתונים
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //מונע דליפת זיכרון
        countdownHandler.removeCallbacks(countdownRunnable);// מבטל את הרצת השעון לאחור בסגירת המסך
        nextQuestionHandler.removeCallbacksAndMessages(null);// מבטל את הפעולה שיש להאנדלר לחכות חצי שנייה בין כל שאלה
    }


    private void loadWords() {
        // מביאים את כל המילים והמשפטים שיש במאגר הנתונים (לא מביאים אותיות)
        db.collection("categories").get()
                .addOnSuccessListener(categories -> {// הצלחנו להביא את הקטגוריות
                    int[] categoriesLeft = {0};// כמה קטגוריות רלוונטיות נשארו

                    //עוברים על כל הקטגוריות ובודקים מי שונה מnull ואם היא שווה גם לאותיות אנחנו מתקדמים הלאה ומשאירים אותה למעבר הבא
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;
                        categoriesLeft[0]++;// אם הקטגוריה עברה את התנאי היא מתווספת לרשימת הקטגוריות הרלוונטיות
                    }

                    if (categoriesLeft[0] == 0) {// אם אין קטגוריות רלוונטיות מציגים הודעה שאין מספיק מילים
                        tvLoading.setText("No words found! Please add some words first");
                        return;
                    }

                    //עוברים על כל הקטגוריות ובודקים מי שונה מnull ואם היא שווה גם לאותיות אנחנו מתקדמים הלאה ומשאירים אותה למעבר הבא
                    for (QueryDocumentSnapshot categoryDoc : categories) {
                        String type = categoryDoc.getString("categoryType");
                        if (type != null && type.equals(CategoryType.LETTERS.name())) continue;

                        String categoryId = categoryDoc.getId();// שומרים את המזהה הייחודי של הקטגוריה שעברה את התנאי

                        db.collection("categories").document(categoryId)
                                .collection("words").get()// לוקחים את כל המילים שיש בקטגוריה הזאת
                                .addOnSuccessListener(words -> {
                                    for (QueryDocumentSnapshot wordDoc : words) {// עוברים על כל המילים שבקטגוריה
                                        Word word = wordDoc.toObject(Word.class);// יוצרים אובייקט "מילה"
                                        word.setIdFS(wordDoc.getId());//שומרים את המזהה הייחודי של המילה
                                        if (word.getWordEnglish() != null// מוסיפים רק מילים שיש להם גם מילה באנגלית וגם פירוש בעברית
                                                && word.getWordHebrew() != null
                                                && !word.getWordEnglish().isEmpty()
                                                && !word.getWordHebrew().isEmpty()) {
                                            allWords.add(word);// מוסיפים את המילה לרשימת כל המילים
                                        }
                                    }

                                    categoriesLeft[0]--;// מקטינים את מספר הקטגוריות הרלוונטיות באחד

                                    if (categoriesLeft[0] == 0) {
                                        startGame(); // כשנשארו 0 קטגוריות רלוונטיות מתחילים את המשחק, קוראים למתודה startGame()
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->// אם לא הצלחנו להביא את הקטגוריות
                        tvLoading.setText("Error loading words. Please try again"));
    }

    private void startGame() {
        if (allWords.size() < 3) {// אם יש לנו פחות משלוש מילים רלוונטיות אז מציגים הודעה שאין מספיק מילים וסוגרים את המשחק
            tvLoading.setText("Not enough words! Please add at least 3 words first");
            return;
        }

        //רשימת המילים שנותרו זהה תחילה לרשימת המילים הרלוונטיות
        // מערבבים את רשימת המילים שנותרו
        remainingWords = new ArrayList<>(allWords);
        Collections.shuffle(remainingWords);

        // מציגים את השאלה ואת התשובות במשחק ומסתירים את loading...
        tvLoading.setVisibility(View.GONE);
        tvQuestion.setVisibility(View.VISIBLE);
        btnAnswer1.setVisibility(View.VISIBLE);
        btnAnswer2.setVisibility(View.VISIBLE);
        btnAnswer3.setVisibility(View.VISIBLE);

        gameRunning = true;// משחק התחיל

        // התחלת הטיימר וקריאה לפעולה הזאת כל שנייה
        countdownHandler.postDelayed(countdownRunnable, 1000);

        showNextQuestion(); // קריאה למתודה showNextQuestion() מציגה לנו שאלה
    }

    private void showNextQuestion() {
        // אם המשחק נגמר אל תציג שאלה חדשה
        if (!gameRunning) return;

        waitingForNext = false; // אם אנחנו לא מחכים לשאלה הבאה ועבור 0.5 שניות כבר

        //אם רשימת המילים הרלוונטיות שנשארו נגמרה אנחנו משווים אותה שוב לרשימת המילים הרלוונטיות המקורית
        // ומערבבים את רשימת המילים הרלוונטיות שנשארה שוב
        if (remainingWords.isEmpty()) {
            remainingWords = new ArrayList<>(allWords);
            Collections.shuffle(remainingWords);
        }

        // שומרים את המילה הראשונה שמופיע ברשימת המילים הרלוונטיות שנשארו
        // ומוציאים אותה מהרשימה של המילים הרלוונטיות שנשארו
        Word word = remainingWords.remove(0);
        correctAnswer = word.getWordHebrew(); // שומרים את התשובה, (המילה הנכונה בעברית)

        // מציגים את השאלה
        tvQuestion.setText(word.getWordEnglish());

        resetButtons(); // מאפסים כפתורים על ידי המתודה resetButtons()

        //מקבלים רשימה של 3 תשובות מהמתודה buildAnswers()
        List<String> answers = buildAnswers(word);
        btnAnswer1.setText(answers.get(0));//שמים את התשובה על כל כפתור
        btnAnswer2.setText(answers.get(1));
        btnAnswer3.setText(answers.get(2));
    }

    private List<String> buildAnswers(Word correctWord) {
        // יוצר רשימה של 3 תשובות, אחת נכונה ו2 שגויות
        List<String> answers = new ArrayList<>();
        answers.add(correctWord.getWordHebrew()); // מוסיפים קודם כל את התשובה הנכונה

        // בונים רשימה של תשובות שגויות
        List<String> wrongPool = new ArrayList<>();
        for (Word w : allWords) {// עוברים על כל המילים הרלוונטיות
            if (!w.getWordHebrew().equals(correctWord.getWordHebrew())) {// בודקים המילה מתוך הרשימה שונה מהתשובה הנכונה
                wrongPool.add(w.getWordHebrew());// אם כן מוסיפים אותה לרשימת התשובות השגויות
            }
        }

        Collections.shuffle(wrongPool); // מערבבים את רשימת התשובות השגויות

        // מוסיפים רק 2 תשובות שגויות (אבל אם יש פחות מ2 תשובות שגויות תוצג רק תשובה אחת שגויה,
        // אין מצב של אפס תשובות שגויות בזכות הבדיקה שהצגנו למעלה במתודה startGame() )
        int wrongCount = Math.min(2, wrongPool.size());
        for (int i = 0; i < wrongCount; i++) {// מוסיפים לרשימת התשובות מספר תשובות שגויות לפי wrongCount (או 2 או 1)
            answers.add(wrongPool.get(i));
        }

        Collections.shuffle(answers); // מערבבים את רשימת התשובות ומחזירים את הרשימה (מגיעה למתודה showNextWuestion() )
        return answers;
    }


    private void resetButtons() {
        // מאפס את כל הכפתורים (מחזיר אותם לצבע אפור ומאפשר עליהם לחיצה)
        int defaultColor = Color.parseColor("#1A237E");
        btnAnswer1.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer2.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer3.setBackgroundTintList(android.content.res.ColorStateList.valueOf(defaultColor));
        btnAnswer1.setEnabled(true);
        btnAnswer2.setEnabled(true);
        btnAnswer3.setEnabled(true);

        // מגדירים מאזינים לכפתורי תשובה שאם לוחצים על כל אחד מהם המתודה checkAnswer() פועלת
        btnAnswer1.setOnClickListener(v -> checkAnswer(btnAnswer1));
        btnAnswer2.setOnClickListener(v -> checkAnswer(btnAnswer2));
        btnAnswer3.setOnClickListener(v -> checkAnswer(btnAnswer3));
    }

    private void checkAnswer(Button tappedButton) {
        // אם המשחק הסתיים או שאנחנו מחכים לשאלה הבאה וכפתור נלחץ לא קורה דבר
        if (!gameRunning || waitingForNext) return;

        waitingForNext = true; // ממתינים לשאלה הבאה

        String tappedAnswer = tappedButton.getText().toString();// שומר את התשובה שנבחרה

        // אי אפשר ללחוץ על כל שאר הכפתורים במשך 0.5 השניות הבאות
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        if (tappedAnswer.equals(correctAnswer)) {
            // אם המשתמש צדק הכפתור נצבע בירוק
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            score++;// מגדילים את התוצאה שלו באחד
            tvScore.setText("Score: " + score); // ומעדכנים את התוצאה שמוצגת על המסך
        } else {
            // אם המשתמש טעה הכפתור נצבע באדום
            tappedButton.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C62828")));

            //רק אם המשתמש טעה אנחנו נחפש בנוסף את התשובה הנכונה ונצבע אותה בירוק
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

        //מפעילים את ההאנדלר שיפעל בעוד 0.5 שניות
        nextQuestionHandler.postDelayed(() -> {
            if (gameRunning) { // מתקדם לשאלה הבאה רק אם המשחק עדיין פועל
                showNextQuestion();
            }
        }, 500);
    }


    private void endGame() {
        // עוצרים את כל ההאנדלרים ככה שלא יפעלו ומונעים מהם פעולות עתידיות לא רצויות
        //מונעים דליפת זיכרון
        countdownHandler.removeCallbacks(countdownRunnable);
        nextQuestionHandler.removeCallbacksAndMessages(null);

        // מבטל את האופציה ללחוץ על כל הכפתורים
        btnAnswer1.setEnabled(false);
        btnAnswer2.setEnabled(false);
        btnAnswer3.setEnabled(false);

        saveBestScore(); // קורא למתודה saveBestScore() שומרת את התוצאה הטובה ביותר
        saveGameHistory(); //  קוראים למתודה saveGameHistory() שנתוני המשחק ישמרו

        // מציג את דיאלוג סיום המשחק
        new AlertDialog.Builder(this)
                .setTitle("Time's up!")
                .setMessage("You answered " + score + " questions correctly!\n\n" +
                        getResultMessage()) //מציג הודעה מותאמת לפי המתודה getResultMessage()
                .setPositiveButton("Play Again", (dialog, which) -> resetGame()) // לשחק שוב
                .setNegativeButton("Exit", (dialog, which) -> finish()) // חזרה למסך התרגול (סגירה של המסך)
                .setCancelable(false) // אי אפשר ללחוץ מחוץ לדיאלוג
                .show();
    }

    private String getResultMessage() {
        if (score >= 20) return "Incredible! You're a Legend!";
        if (score >= 15) return "Amazing speed!";
        if (score >= 10) return "Great job! Keep practicing!";
        if (score >= 5) return "Not bad! Try again to beat your score!";
        return "Keep studying and try again!";
    }


    private void saveBestScore() {
        // אם המשתמש לא רשום תוצאות לא נשמרות
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();// מזהה ייחודי של המשתמש

        db.collection("users").document(userId).get()// לקיחת המשתמש מהמאגר
                .addOnSuccessListener(document -> {
                    // התוצאה הטובה ביותר כברירת מחדל היא 0
                    long currentBestScore = 0;
                    if (document.exists() && document.getLong("speedTriviaBestScore") != null) {// בודק אם קיים שיא בטריוויה מהירה
                        currentBestScore = document.getLong("speedTriviaBestScore");// אם קיים שיא, נשים אותו בשיא הטוב ביותר
                    }

                    // בודקים אם תוצאת המשחק עכשיו גבוהה מהשיא שיש למשתמש
                    if (score > currentBestScore) {
                        db.collection("users").document(userId)
                                .update("speedTriviaBestScore", score)// אם כן אנחנו נעדכן את השיא במשתמש שלו במאגר הנתונים
                                .addOnSuccessListener(v ->// לאחר העגכון נציג הודעה מתאימה
                                        Toast.makeText(this, "New best score!", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void saveGameHistory() {
        // בודקים אם המשתמש רשום כי רק למשתמשים רשומים נשמרים נתוני משחק
        if (mAuth.getCurrentUser() == null) return;

        String userId = mAuth.getCurrentUser().getUid();// לוקחים את המזהה הייחודי של המשתמש

        // שומרים את הזמן והתאריך של סיום המשחק
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());
        Date now = new Date();// שמירת הזמן הנוכחי
        String date = dateFormat.format(now); // התאריך הנוכחי בפורמט של תאריך
        String time = timeFormat.format(now); // הזמן הנוכחי בפורמט של זמן

        // בניית האש מאפ (כמו ארגז) חדש שלתוכו יכנסו נתוני המשחק
        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();
        historyEntry.put("type", "SPEEDTRIVIA"); // סוג המשחק (טריוויה מהירה)
        historyEntry.put("date", date);// התאריך הנוכחי (מפורמט)
        historyEntry.put("time", time);// הזמן הנוכחי (מפורמט)
        historyEntry.put("score", String.valueOf(score)); // מספר התשובות הנכונות
        historyEntry.put("duration", "60 seconds"); // הזמן שלקח (60 שניות תמיד)
        historyEntry.put("timestamp", System.currentTimeMillis()); //הזמן הנוכחי (לא מפורמט)

        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry); // הכנסה של ההאש מאפ לנתונים של המשתמש במאגר הנתונים
    }


    private void resetGame() {
        //מאפסים הכל (תוצאה, צבע כפתורים, טיימר, את המשתנה שמחכה לשאלה הבאה)
        score = 0;
        secondsLeft = 60;
        waitingForNext = false;// לא מחכים לשאלה הבאה
        tvScore.setText("Score: 0");
        tvTimer.setText("60");
        tvTimer.setTextColor(Color.parseColor("#FFD700"));

        // ממלאים מחדש את רשימת המילים הרלוונטיות שנשארו
        remainingWords = new ArrayList<>(allWords);
        Collections.shuffle(remainingWords);// מערבבים מחדש את רשימת המילים הרלוונטיות שנשארו

        gameRunning = true;// המשחק החל

        // מפעילים את ההאנדלר שלנו שקורא לראנאבל כל שנייה ומעדכן את הטיימר
        countdownHandler.postDelayed(countdownRunnable, 1000);

        showNextQuestion();// קוראים למתודה שמראה את השאלה הבאה
    }

    private void showBackConfirmation() {
        // עוצרים את כל ההאנדלרים והראנאבל ואת כל הפעולות העתידיות שלהם כדי שהזמן לא ימשיך לרדתם למטה
        // וששאלות הבאות לא יוצגו כשעובר 0.5 שניות
        countdownHandler.removeCallbacks(countdownRunnable);
        nextQuestionHandler.removeCallbacksAndMessages(null);

        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("If you go back now your progress will be lost. Are you sure?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    // רוצה לצאת, המשחק הפסיק וסוגרים את המסך
                    gameRunning = false;
                    finish();// סגירת המסך מעבר אוטומטי למסך התרגול
                })
                .setNegativeButton("Keep Playing", (dialog, which) -> {
                    // רוצה להישאר, בודק אם המשחק עדיין רץ אם כן אנחנו מחזירים את הטיימר לעבוד
                    if (gameRunning) {
                        countdownHandler.postDelayed(countdownRunnable, 1000);
                    }
                })
                .setCancelable(false)// לא ניתן ללחוץ מחוץ לדיאלוג
                .show();
    }
}
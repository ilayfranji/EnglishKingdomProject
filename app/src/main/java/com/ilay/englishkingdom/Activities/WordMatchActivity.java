package com.ilay.englishkingdom.Activities;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Pair;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.Models.CategoryType;
import com.ilay.englishkingdom.Models.Stage;
import com.ilay.englishkingdom.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class WordMatchActivity extends AppCompatActivity {

    private static final int MAX_LIVES = 3; // משתנה קבוע, יש 3 לבבות

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;

    private TextView tvBack;
    private TextView tvTimer;
    private TextView tvLoading;
    private TextView word1Text;
    private TextView word2Text;
    private TextView infoTv;
    private TextView livesTv;
    private ImageView word1ImageView;
    private ImageView word2ImageView;
    private LinearLayout wordListContainer;
    private LinearLayout layout1;
    private LinearLayout layout2;
    private LinearLayout wordMatchLayout;

    private Stage stage1Words;
    private Stage stage2Words;
    private Stage stage3Words;
    private int currentStage = 1;
    private String selectedWord = null;
    private TextView selectedTv = null;// שמירת הtext view של המילה שנבחרה
    private boolean inTransition = false; // האם אנחנו בהמתנה כלשהי (המתנה במעבר שלב או אחרי התאמה)
    private HashSet<String> correct = new HashSet<>(); // המילים שהותאמו נכון
    private List<TextView> wordTextViews = new ArrayList<>(); //רשימת מחסן המילים של שלב נוכחי
    private int lives = MAX_LIVES;

    private Handler timerHandler = new Handler(Looper.getMainLooper()); // מריץ את הטיימר במרווחי זמן קבועים של 10 מילי שניות (מריץ במסך הראשי כדי לא לגרום לקריסות)
    private long startTime = 0;
    private long elapsedTime = 0; // הזמן שעבר מהרגע שהטיימר הופעל
    private boolean timerRunning = false; // האם הטיימר רץ ברגע זה

    private Runnable timerRunnable = new Runnable() {// אובייקט המכיל את המשימה של השעון (חישוב הזמן ועדכון המסך) שנועד להרצה חוזרת בלופ
        @Override
        public void run() {
            elapsedTime = System.currentTimeMillis() - startTime; // מחשב את הזמן שהטיימר עבר מהרגע שהוא התחיל את הספירה
            tvTimer.setText(formatTime(elapsedTime)); // מעדכן את התצוגה של הטיימר במסך
            timerHandler.postDelayed(this, 10); //קריאה שוב להאנדלר שבעזרתו הטיימר יעשה שוב את הפעולה לאחר 10 מילי שניות
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_word_match);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

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

        // מציגים הודעת Loading... ולא מציגים את המשחק בזמן הבאת נתונים מFireStore
        tvLoading.setVisibility(View.VISIBLE);
        wordMatchLayout.setVisibility(View.GONE);

        // קורא למתודה showBackConfirmation()
        tvBack.setOnClickListener(v -> showBackConfirmation());

        loadWords(); // קריאה למתודה


        layout1.setOnClickListener(v -> {// לחיצה על התמונה העליונה
            if (inTransition) return; // חוסם לחיצות על המסך בזמן המתנה
            if (!isWordSelected()) return; // אם לא לחצנו על מילה קודם מתעלמים מלחיצה על תמונה

            Stage stage = getCurrentStageObject(); // שמירת השלב לאחר שקיבלנו אותו מהמתודה getCurrentStageObject()
            if (stage.isFirstWordSuccess()) return; // אם התאמנו את הזוג הראשון, לחיצה על התמונה שלו לא תשפיע

            if (selectedWord.equalsIgnoreCase(stage.getFirstWord().first)) {// בדיקה אם המילה שסומנה במחסן המילים זהה (לא משנה אותיות קטנות וגדולות)
                // למילה המתאימה לתמונה שנלחצה (האיבר הראשון בזוג)
                // אם התנאי מתקיים, צדקנו בהתאמת זוג מספר אחד (העליון)
                correct.add(stage.getFirstWord().first.toLowerCase()); // הוספה להאש סט של המילים שהצלחנו להתאים
                inTransition = true; // מחסום לחיצות על המסך
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green)); // מסגרת ירוקה לתמונה
                word1Text.setText(stage.getFirstWord().first); // השמת המילה ליד התמונה
                resetSelectedWord(); // קריאה למתודה resetSelectedWord()
                stage.setFirstWordSuccess(true); // הצלחנו להתאים את המילה הראשונה

                if (stage.isSecondWordSuccess()) {
                    // בדיקה אם החלק השני כבר הושלם
                    new Handler().postDelayed(() -> {// מייצרים שעון נוסף שימתין שנייה אחת לפני שהוא מריץ את הקוד שבתוכו
                        inTransition = false;// נגמר מחסום הלחיצה
                        nextStage();// קריאה לnextStage()
                    }, 1000);
                } else {// אם החלק השני עדיין לא הושלם
                    new Handler().postDelayed(() -> inTransition = false, 1000);// מייצרים שעון נוסף שימתין שנייה אחת לפני שהוא מריץ את הקוד שבתוכו
                }
            } else { // אם לא הצליח להתאים את המילה לתמונה שבחלק הראשון וטעה
                layout1.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red)); //מסגרת אדומה לתמונה
                inTransition = true;// מחסום לחיצה
                lives--; // לב אחד יורד
                livesTv.setText(getLivesText()); // עדכון התצוגה של הלבבות על המסך
                resetSelectedWord();// קריאה למתודה

                new Handler().postDelayed(() -> {// מייצרים שעון נוסף שימתין חצי שנייה לפני שהוא מריץ את הקוד שבתוכו
                    layout1.setBackground(null); // מוריד את המסגרת האדומה
                    inTransition = false;// מוריד את מחסום הלחיצה
                }, 500);

                if (lives == 0) {// אם נגמרו הפסילות
                    gameOver(false); // קריאה למתודה gameOver עם הפרמטר שהמשתמש הפסיד
                } else {// אם לא נגמרו הפסילות
                    Toast.makeText(this, "Not quite! " + lives + " " +
                            (lives == 1 ? "life" : "lives") + " left", Toast.LENGTH_SHORT).show();// הודעה מתאימה עם כמות החיים שנותרו לשחקן
                }
            }
        });

        layout2.setOnClickListener(v -> {
            if (inTransition) return; // חוסם לחיצות על המסך בזמן המתנה
            if (!isWordSelected()) return; // אם לא לחצנו על מילה קודם מתעלמים מלחיצה על תמונה

            Stage stage = getCurrentStageObject();// שמירת השלב לאחר שקיבלנו אותו מהמתודה getCurrentStageObject()
            if (stage.isSecondWordSuccess()) return; // אם התאמנו את הזוג השני, לחיצה על התמונה שלו לא תשפיע

            if (selectedWord.equalsIgnoreCase(stage.getSecondWord().first)) {// בדיקה אם המילה שסומנה במחסן המילים זהה (לא משנה אותיות קטנות וגדולות)
                // למילה המתאימה לתמונה שנלחצה (האיבר הראשון בזוג)
                // אם התנאי מתקיים, צדקנו בהתאמת זוג מספר שתיים (התחתון)
                correct.add(stage.getSecondWord().first.toLowerCase());// הוספה להאש סט של המילים שהצלחנו להתאים
                inTransition = true;// מחסום לחיצות על המסך
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_green));// מסגרת ירוקה לתמונה
                word2Text.setText(stage.getSecondWord().first);// השמת המילה ליד התמונה
                resetSelectedWord();// קריאה למתודה resetSelectedWord()
                stage.setSecondWordSuccess(true);// הצלחנו להתאים את המילה השנייה

                if (stage.isFirstWordSuccess()) {
                    // בדיקה אם החלק הראשון כבר הושלם
                    new Handler().postDelayed(() -> {// מייצרים שעון נוסף שימתין שנייה אחת לפני שהוא מריץ את הקוד שבתוכו
                        inTransition = false;// נגמר מחסום הלחיצה
                        nextStage();// קריאה למתודה
                    }, 1000);
                } else {// אם החלק הראשון עדיין לא הושלם
                    new Handler().postDelayed(() -> inTransition = false, 1000);// מייצרים שעון נוסף שימתין שנייה אחת לפני שהוא מריץ את הקוד שבתוכו
                }
            } else {// אם לא הצליח להתאים את ההמילה לתמונה שבחלק השני וטעה
                layout2.setBackground(AppCompatResources.getDrawable(this, R.drawable.border_red));// מסגרת אדומה לתמונה בחלק השני
                inTransition = true;// מחסום לחיצה
                lives--;// הורדת מספר הלבבות
                livesTv.setText(getLivesText());// עדכון התצוגה של הלבבות על המסך
                resetSelectedWord();// קריאה למתודה

                new Handler().postDelayed(() -> {// מייצרים שעון נוסף שימתין חצי שנייה לפני שהוא מריץ את הקוד שבתוכו
                    layout2.setBackground(null);// מוריד את המסגרת האדומה
                    inTransition = false;// מוריד את מחסום הלחיצה
                }, 500);

                if (lives == 0) {// אם נגמרו הפסילות
                    gameOver(false);// קריאה למתודה gameOver עם הפרמטר שהמשתמש הפסיד
                } else {// אם לא נגמרו הפסילות
                    Toast.makeText(this, "Not quite! " + lives + " " +
                            (lives == 1 ? "life" : "lives") + " left", Toast.LENGTH_SHORT).show();// הודעה מתאימה עם כמות החיים שנותרו לשחקן
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopTimer(); // כשסוגרים את המסך נעצור את הטיימר כדי למנוע דליפת זיכרון
    }

    private void showBackConfirmation() {
        // דיאלוג "האם אתה בטוח שאתה רוצה להפסיק את המשחק?"
        new AlertDialog.Builder(this)
                .setTitle("Leave Game?")
                .setMessage("If you go back now your progress will be lost. Are you sure?")
                .setPositiveButton("Leave", (dialog, which) -> {// לחיצה על Leave
                    stopTimer(); // קורא למתודה stopTimer()
                    finish(); // סגירת המסך
                })
                .setNegativeButton("Keep Playing", null) // לחיצה על Keep Playing סוגר את הדיאלוג
                .show();
    }


    private void startTimer() {
        // מתחיל את הטיימר מ0
        startTime = System.currentTimeMillis(); // שמירת הזמן הנוכחי
        timerRunning = true;// הטיימר רץ
        timerHandler.post(timerRunnable); // מתחיל את הטיימר על ידי הפעולה run()
    }

    private void stopTimer() {
        timerRunning = false;// הטיימר נעצר
        timerHandler.removeCallbacks(timerRunnable); // עצירת הטיימר וכל פעולת הרצה עתידית שלו
    }

    private String formatTime(long millis) {
        // שליפת הדקות, השניות והמילי שניות של הזמן שהשחקן משחק במשחק
        long minutes = millis / 60000;
        long seconds = (millis % 60000) / 1000;
        long ms = millis % 1000;
        return String.format(Locale.getDefault(), "%d:%02d:%03d", minutes, seconds, ms);// הצגת זמן המשחק בפורמט של דקות, שניות ומילי שניות
    }


    private String getLivesText() {
        // מחזיר את ההודעה שמוצגת בראש המסך, מספר לבבות שנותרו מתוך הכמות הכוללת
        return String.format("Lives: %d/%d", lives, MAX_LIVES);
    }


    private void gameOver(boolean won) {// משחק נגמר, מקבלת את תוצאת המשחק (ניצח/ הפסיד)
        stopTimer(); // עצירת הטיימר
        saveGameHistory(won); // קריאה למתודה saveGameHistory() עם המידע האם המשתמש ניצח או הפסיד
        inTransition = true; // מחסום לחיצות הופעל

        String title;
        String message;

        if (won) {// אם המשתמש ניצח
            title = "You Won!";
            message = "Amazing! You matched all the words correctly!\n\n" +
                    "Your time: " + formatTime(elapsedTime) + "\n" +
                    "Lives remaining: " + lives + "/" + MAX_LIVES;

        } else {// אם המשתמש הפסיד
            title = "Game Over!";
            message = "You ran out of lives this time, but don't give up!\n\n" +
                    "You made it to stage " + currentStage + " out of 3.\n" +
                    "Your time: " + formatTime(elapsedTime) + "\n\n" +
                    "Keep practicing and try again!";
        }

        new AlertDialog.Builder(this)// יצירת דיאלוג סיום המשחק לפי הניצחון או ההפסד של המשתמש
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Play Again", (dialog, which) -> resetGame()) // קריאה למתודה resetGame(), מתחילים משחק מחדש
                .setNegativeButton("Exit", (dialog, which) -> finish()) // סוגר את המסך וחוזרים למסך התרגול
                .setCancelable(false) // אי אפשר לסגור את הדיאלוג עד שלא לוחצים על כפתור בדיאלוג
                .show();
    }

    private void resetSelectedWord() {
        // איפוס המילה שנבחרה
        selectedWord = null;
        selectedTv = null;
        infoTv.setText("Pick a word from the word bank below"); // איפוס ההוראות

        for (TextView tv : wordTextViews) {
            // עוברים על כל המילים במחסן המילים
            if (correct.contains(tv.getText().toString().toLowerCase())) {
                tv.setTextColor(Color.GREEN);// אם אחת מהמילים במחסן המילים נמצאת במילים שצדקנו בהן נסמן אותה בירוק
            } else {
                tv.setTextColor(Color.WHITE);// אחרת נשאיר אותה בלבן
            }
        }
    }

    private Stage getCurrentStageObject() {
        // מחזיר את השלב עצמו לפי מספר השלב שהמשתמש נמצא בו
        switch (currentStage) {
            case 2: return stage2Words;
            case 3: return stage3Words;
            default: return stage1Words;
        }
    }


    private void loadWords() {// העלאת המילים למשחק רק מטיפוס (WORD)
        db.collection("categories")
                .whereEqualTo("categoryType", CategoryType.WORDS.name())
                .get()
                .addOnSuccessListener(categories -> {// מאזין הצלחה להבאת כל המילים ממאגר הנתונים
                    int[] categoriesLeft = {categories.size()};// יוצר מערך בגודל 1 ובתא כתוב את מספר הקטגוריות הרלוונטיות

                    if (categoriesLeft[0] == 0) {// כשנגמרו הקטגוריות במערך מציגים הודעה
                        tvLoading.setText("No words found! Please add some words first.");
                        return;
                    }

                    // יוצרים רשימה של זוגות של מילים, בכל זוג יש מילה ותמונה שמתאימה למילה
                    List<Pair<String, String>> allWordsList = new ArrayList<>();

                    for (QueryDocumentSnapshot categoryDoc : categories) {// מעבר על כל הקטגוריות
                        db.collection("categories").document(categoryDoc.getId())
                                .collection("words").get()
                                .addOnSuccessListener(words -> {// מאזין להצלחה
                                    for (QueryDocumentSnapshot wordDoc : words) {// עובר על כל המילים בקטגוריה שעברנו עליה בלולאה החיצונית
                                        String english = wordDoc.getString("wordEnglish");// שמירת המילה באנגלית
                                        String image = wordDoc.getString("image");// שמירת התמונה של המילה
                                        // אם יש מילה והיא לא מכילה רווחים ויש תמונה למילה
                                        if (english != null && !english.isEmpty()
                                                && !english.contains(" ")
                                                && image != null && !image.isEmpty()) {
                                            allWordsList.add(new Pair<>(english.toLowerCase(), image));// אם עמד בתנאים נוסיף אותו לרשימת הזוגות
                                        }
                                    }

                                    categoriesLeft[0]--;// בסיום המעבר על כל המילים בקטגוריה הרלוונטית נוריד את הערך בתא באחד

                                    if (categoriesLeft[0] == 0) {// כשנגמרו כל הקטגוריות
                                        startGame(allWordsList); // נקרא למתודה startGame עם רשימת המילים שנמצאו מתאימות למשחק
                                    }
                                });
                    }
                })
                .addOnFailureListener(e ->// מאזין לכישלון
                        tvLoading.setText("Error loading words. Please try again."));
    }


    private void startGame(List<Pair<String, String>> allWordsList) {// התחלת משחק, מקבל את רשימת המילים
        if (allWordsList.size() < 6) {// אם יש לנו פחות מ6 מילים לא נוכל לשחק
            Toast.makeText(this, "Not enough words to play. Please add more words first!", Toast.LENGTH_LONG).show();
            finish();// סגירת המסך
            return;
        }

        livesTv.setText(getLivesText()); // הצגת מספר הלבבות
        tvLoading.setVisibility(View.GONE); // מחיקת Loadinig...
        wordMatchLayout.setVisibility(View.VISIBLE); // הצגת המשחק

        // בניית 3 שלבים
        Set<String> previouslyUsed = new HashSet<>(); // יצירת האש סט שבו ימצאו כל המילים שהשתמשנו כבר
        stage1Words = buildStage(allWordsList, previouslyUsed);// בניית שלב, בכל שלב נעביר את כל המילים הרלוונטיות ואת המילים שהשתמשנו בהם כבר
        stage2Words = buildStage(allWordsList, previouslyUsed);
        stage3Words = buildStage(allWordsList, previouslyUsed);

        currentStage = 1; // מתחילים משלב 1
        startTimer(); // הפעלת השעון
        startStage(); // הצגת השלב
    }

    private void nextStage() {// מעבר שלב
        if (currentStage == 3) {// אם אנחנו בשלב 3
            gameOver(true); // קריאה למתודה gameOver עם המידע שהשחקן ניצח
            return;
        }

        correct.clear(); // מחיקת המילים שהצלחנו בהאש סט
        currentStage++; // קידום שלב
        layout1.setBackground(null); // מחיקת מסגרות מסביב לתמונות
        layout2.setBackground(null);
        startStage(); // התחלת השלב
    }


    private void startStage() {
        // Resets the image rows and loads the pictures and word bank for the current stage
        word1Text.setText(""); // מחיקת המילה שמופיעה ליד התמונה
        word2Text.setText("");
        infoTv.setText("Pick a word from the word bank below"); // איפוס שורת ההוראות

        Stage stage = getCurrentStageObject(); // קבלת מספר השלב הנוכחי מהפעולה getCurrentStageObject()

        // שימוש בספריית גלייד לטעינת תמונות
        Glide.with(this).load(stage.getFirstWord().second).into(word1ImageView);
        Glide.with(this).load(stage.getSecondWord().second).into(word2ImageView);

        buildWordList(stage); // בניית מחסן המילים, מקבל את השלב הנוכחי
    }


    private Stage buildStage(List<Pair<String, String>> allWordsList, Set<String> previouslyUsed) {// מקבלת את רשימת המילים הרלוונטיות למשחק ואת רשימת המילים שהשתמשנו בהם כבר
        List<Pair<String, String>> tempCopy = new ArrayList<>(allWordsList);// יצירת עותק של רשימת המילים הרלוונטיות למשחק
        Random rnd = new Random();

        Pair<String, String> firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));// שליפת זוג רנדומלי מהעותק של רשימת המילים הרלוונטיות
        int attempts = 20; // מקסימום של 20 נסיונות
        while (attempts > 0 && previouslyUsed.contains(firstWord.first)) {// לולאה רצה כל עוד יש מספיק נסיונות וכל עוד המילה הרנדומלית שנשלפה נמצאת במילים שהיו במשחק
            attempts--;// הקטנת מספר הנסיונות
            if (tempCopy.isEmpty()) return null;// אם רשימת עותק המילים ריקה נחזיר null
            firstWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));// שליפת זוג רנדומלי מהעותק של רשימת המילים הרלוונטיות שוב
        }

        // לאחר היציאה מהלולאה נבדוק למה יצאנו


        if (attempts == 0) return null;// אם יצאנו כי נגמרו הניסיונות נחזיר null

        // אם לא החזרנו עדיין null, קיים זוג שהצלחנו לשמור שלא הופיע עוד במשחק

        //עכשיו אותה לוגיקה גם לזוג השני
        Pair<String, String> secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        attempts = 20;
        while (attempts > 0 && previouslyUsed.contains(secondWord.first)) {
            attempts--;
            if (tempCopy.isEmpty()) return null;
            secondWord = tempCopy.remove(rnd.nextInt(tempCopy.size()));
        }

        // אחרי שמצאנו 2 זוגות מתאימים שלא היו במשחק, נוסיף את המילים של הזוגות לרשימת המילים שהיו במשחק כדי שלא יופיעו שוב בשלבים הבאים
        previouslyUsed.add(firstWord.first);
        previouslyUsed.add(secondWord.first);

        //ערבוב רשימת המילים (המילים שמופיעות במשחק כבר נשלפו ממנה)
        Collections.shuffle(tempCopy);
        List<Pair<String, String>> stageWords = tempCopy.stream()// יוצרים רשימה של המילים שיופיעו בשלב, הרשימה הופכת לזרם
                .limit(4)// מכניסים לשם 4 מילים שגויות
                .collect(Collectors.toList());// הוספת המילים לזרם והפיכה לרשימה

        // מוסיפים את 2 המילים הנכונות גם
        stageWords.add(firstWord);
        stageWords.add(secondWord);
        Collections.shuffle(stageWords);// ערבוב הרשימה

        return new Stage(stageWords, firstWord, secondWord);// מחזירים שלב, בשלב יש את המילים שבשלבף, את הזוג הראשון והזוג השני
    }

    private void buildWordList(Stage stage) {// יוצר את מחסן המילים, מקבל שלב
        // יוצרים רשימה שמקבלת את מאגר המילים שמופיעות בשלב הספציפי
        List<String> words = stage.getStageWordStorage()
                .stream().map(p -> p.first).collect(Collectors.toList());// עבור כל הזוגות שעוברים בזרם,
                //לוקחים רק את האיבר הראשון בזוג ומוסיפים אותו לזרם words, לבסוף מעבירים את הזרם לרשימה

        wordTextViews.clear(); // מחיקת הרשימה של הטקסט וויו של השלב הקודם
        wordListContainer.removeAllViews(); // מוחק את מחסן המילים הקודם

        // יצירת העיצוב של מחסן המילים
        TextView header = new TextView(this);// יצירת רכיב טקסט וויו חדש
        header.setText("Word Bank:");
        header.setTextColor(0xFFFFD700); // צבע זהב
        header.setTextSize(14);
        header.setPadding(8, 8, 8, 8);// מרווח מהקצוות
        wordListContainer.addView(header);// הלבשת רכיב הטקסט וויו החדש על הלייאווט שקיים

        // השורה הנוכחית שבה מוצגות המילים
        LinearLayout currentRow = null;

        for (int i = 0; i < words.size(); i++) {// עוברים על כל המילים שבמחסן המילים
            if (i % 3 == 0) { // מתחילים שורה חדשה אחרי שיש 3 מילים בשורה
                currentRow = new LinearLayout(this);// יוצרים לינאר לייאווט שהוא השורה הנוכחית
                currentRow.setOrientation(LinearLayout.HORIZONTAL);// אופקי
                wordListContainer.addView(currentRow);// מוסיפים את השורה ללייאווט
            }

            TextView tv = createWordBankTextView(words, i);// קריאה למתודה שיוצרת טקסט וויו למילה
            currentRow.addView(tv);//הוספת המילה בטקסט וויו לשורה
            wordTextViews.add(tv);// הוספת המילה שצורפה לרשימת המילים שיש במחסן המילים
        }
    }


    @NonNull
    private TextView createWordBankTextView(List<String> words, int i) {
        // יוצר טקסט וויו של מילה במחסן מילים ומטפל גם באירועי לחיצה עליה
        String word = words.get(i);// שמירת המילה

        TextView tv = new TextView(this);// יוצרים טקסט וויו חדש
        tv.setText(word.toUpperCase());
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(13);
        tv.setPadding(8, 4, 8, 4);

        tv.setOnClickListener(v -> {
            if (correct.contains(word)) return; // אם כבר מסומנת כנכון
            if (inTransition) return; // אם אנחנו לוחצים על המילה בזמן חסימת הלחיצות

            if (word.equals(selectedWord)) {
                // אם לחצנו שוב על אותה מילה שעכשיו היא במצב לחיצה (צבע צהוב)
                selectedWord = null;// המילה שלחוצה ברגע זה כבר לא לחוצה
                tv.setTextColor(Color.WHITE);
                infoTv.setText("Pick a word from the word bank below");// איפוס ההוראות
                return;
            }

            // בחרנו מילה
            selectedWord = word;// שמירת המילה
            selectedTv = tv;// שמירת הטקסט וויו של המילה

            // עוברים על כל המילים
            for (TextView other : wordTextViews) {
                if (other != tv) {// אם המילה לא המילה שלחצנו עליה
                    if (correct.contains(other.getText().toString().toLowerCase())) {// בדוק אם היא מילה שכבר צדקנו בהתאמה שלה
                        other.setTextColor(Color.GREEN);// ירוק
                    } else {
                        other.setTextColor(Color.WHITE);//לבן
                    }
                }
            }

            tv.setTextColor(Color.YELLOW); // שים למילה שבחרנו צבע צהוב
            infoTv.setText("Now tap the image that matches: " + word.toUpperCase());// שינוי ההוראות
        });

        // קובעים פרמטרים לסידור המילים במחסן המילים
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        //width=0 אומר לא לקבוע רוחב קבוע למילה
        //weight=1f אומר שאנדוראיד יקח את 3 המילים ויקבע רוחב שווה בשווה לשלושתן
        tv.setLayoutParams(params);// הלבשת ההגדרות של הלייאווט
        return tv;
    }

    private boolean isWordSelected() {
        return selectedWord != null; //בודק האם יש מילה שנלחצה
    }


    private void saveGameHistory(boolean won) {
        if (mAuth.getCurrentUser() == null) return;// בדיקה אם המשתמש מחובר

        String userId = mAuth.getCurrentUser().getUid();// שמירת המזהה הייחודי שלו

        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        java.text.SimpleDateFormat timeFormat = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        java.util.Date now = new java.util.Date(); // שמירת רגע סיום המשחק
        String currentDate = dateFormat.format(now);// שמירת התאריך של סיום המשחק (מפורמט)
        String currentTime = timeFormat.format(now);//שמירת השעה של סיום המשחק (מפורמט)

        java.util.HashMap<String, Object> historyEntry = new java.util.HashMap<>();// יצירת האש מאפ עם תוצאות המשחק
        historyEntry.put("type", "WORDMATCH");
        historyEntry.put("date", currentDate);
        historyEntry.put("time", currentTime);
        historyEntry.put("result", won ? "Won" : "Lost");
        historyEntry.put("stage", "Stage " + currentStage + "/3");
        historyEntry.put("livesLeft", lives + "/" + MAX_LIVES);
        historyEntry.put("duration", formatTime(elapsedTime));
        historyEntry.put("timestamp", System.currentTimeMillis());// שמירת הזמן הנוכחי של סיום המשחק (לא מפורמט)

        db.collection("users").document(userId)
                .collection("gameHistory")
                .add(historyEntry);// שמירת הנתונים במאגר הנתונים, הכנסת ההאש מאפ

        if (won) {// אם המשתמש ניצח
            saveBestWordMatchStats(userId);// קריאה למתודה
        }
    }

    private void saveBestWordMatchStats(String userId) {
        //בודק שיאים ומעדכן אם יש שיא חדש
        db.collection("users").document(userId).get()// הבאת המשתמש
                .addOnSuccessListener(document -> {// אם הצלחנו

                    // ברירת מחדל (המשתמש הגיע לשלב 0)
                    long currentBestStage = 0;
                    if (document.exists() && document.getLong("wordMatchBestStage") != null) {// אם יש שדה של הטוב ביותר שאליו הגיע
                        currentBestStage = document.getLong("wordMatchBestStage");// שמור את השדה הזה במקום ברירת המחדל
                    }

                    //ברירת מחדל (הערך הכי גדול שיש, הזמן המקסימלי)
                    long currentBestTime = Long.MAX_VALUE;
                    if (document.exists() && document.getLong("wordMatchBestTimeMs") != null) {// אם יש שדה של הטוב ביותר שאליו הגיע
                        currentBestTime = document.getLong("wordMatchBestTimeMs");// שמור את השדה הזה במקום ברירת המחדל
                    }

                    //ברירת מחדל (המשתמש עם 0 לבבות)
                    long currentBestLives = 0;
                    if (document.exists() && document.getLong("wordMatchBestLives") != null) {// אם יש שדה של הטוב ביותר שאליו הגיע
                        currentBestLives = document.getLong("wordMatchBestLives");// שמור את השדה הזה במקום ברירת המחדל
                    }

                    //בניית האש מאפ שאליו נכניס את השיאים החדשים
                    java.util.HashMap<String, Object> updates = new java.util.HashMap<>();
                    boolean shouldUpdate = false;// דגל אם צריך לעדכן

                    //אם המשתמש עקף את שיא השלבים שהגיע אליו
                    if (currentStage > currentBestStage) {
                        updates.put("wordMatchBestStage", (long) currentStage);// הוספת השיא החדש להאש מאפ
                        shouldUpdate = true;// עדכון הדגל, צריך לשנות את השיאים
                    }

                    //אם המשתמש סיים בזמן נמוך מהשיא שלו
                    if (elapsedTime < currentBestTime) {
                        updates.put("wordMatchBestTimeMs", elapsedTime); // הוספת השיא החדש להאש מאפ (לא מפורמט)
                        updates.put("wordMatchBestTimeFormatted", formatTime(elapsedTime)); // הוספת השיא החדש להאש מאפ (מפורמט)
                        shouldUpdate = true;// עדכון הדגל, צריך לשנות את השיאים
                    }

                    //אם המשתמש עקף את שיא הלבבות שהיו לו
                    if (lives > currentBestLives) {
                        updates.put("wordMatchBestLives", (long) lives);// הוספת השיא החדש להאש מאפ
                        shouldUpdate = true;// עדכון הדגל, צריך לשנות את השיאים
                    }

                    //בדיקה אם צריך לשנות שיאים במאגר הנתונים
                    //אם כן
                    if (shouldUpdate) {
                        db.collection("users").document(userId).update(updates)// מוסיפים את ההאש מאפ למאגר הנתונים
                                .addOnSuccessListener(v ->// אם התווסף בהצלחה נשלח הודעת טוסט, שיא חדש
                                        Toast.makeText(this, "New best!", Toast.LENGTH_SHORT).show());
                    }
                });
    }

    private void resetGame() {
        // איפוס המשחק
        lives = MAX_LIVES; // החזרת הלבבות למקסימום לבבות
        currentStage = 1; // חזרה לשלב 1
        correct.clear(); // מנקים את ההאש סט ככה שיהיה ריק
        inTransition = false; // מורידים את מחסום הלחיצות
        selectedWord = null;// אין מילה שנבחרה עדיין
        selectedTv = null;// אין טקסט וויו של מילה שנשמרה
        elapsedTime = 0; // איפוס זמן המשחק
        tvTimer.setText("0:00:000");
        livesTv.setText(getLivesText());
        layout1.setBackground(null); // מחיקת המסגרות מסביב לתמונות
        layout2.setBackground(null);

        // טעינת מילים מחדש
        tvLoading.setVisibility(View.VISIBLE);
        wordMatchLayout.setVisibility(View.GONE);
        loadWords();// קריאה למתודה
    }
}
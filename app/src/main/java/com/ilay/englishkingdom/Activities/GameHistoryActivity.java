package com.ilay.englishkingdom.Activities;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.ilay.englishkingdom.R;

public class GameHistoryActivity extends AppCompatActivity {
    private TextView tvBack;
    private TextView tvLoading;
    private ScrollView scrollView;
    private LinearLayout triviaContainer;
    private LinearLayout speedTriviaContainer;
    private LinearLayout wordSearchContainer;
    private LinearLayout wordMatchContainer;

    private FirebaseFirestore db;
    private FirebaseAuth mAuth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game_history);

        db = FirebaseFirestore.getInstance();
        mAuth = FirebaseAuth.getInstance();

        tvBack = findViewById(R.id.tvBack);
        tvLoading = findViewById(R.id.tvLoading);
        scrollView = findViewById(R.id.scrollView);
        triviaContainer = findViewById(R.id.triviaContainer);
        speedTriviaContainer = findViewById(R.id.speedTriviaContainer);
        wordSearchContainer = findViewById(R.id.wordSearchContainer);
        wordMatchContainer = findViewById(R.id.wordMatchContainer);

        tvBack.setOnClickListener(v -> finish());// לחיצה על כפתור החזור סוגר את המסך

        // בדיקה אם המשתמש מחובר, אם איכשהו אורח הצליח להיכנס מוציאים אותו ישר
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        loadHistory(); // טוען את כל נתוני ההיסטוריה מפייר סטור, קורא למתודה
    }

    private void loadHistory() {
        String userId = mAuth.getCurrentUser().getUid();// המזהה הייחודי של המשתמש

        //מביאים את כל תוצאות המשחקים של המשתמש הזה ששמורים במאגר הנתונים,
        // שולף אותם בסדר מהחדש לישן ככה שהמשחקים החדשים יופיעו קודם
        db.collection("users").document(userId)
                .collection("gameHistory")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {// אם הצלחנו לשלוף
                    tvLoading.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);// מפעילים את הגלילה במסך

                    //השמת דגלים לכל אחד מהמשחקים, אם נמצא משחק נשנה את הדגל שלו
                    boolean hasTrivia = false;
                    boolean hasSpeedTrivia = false;
                    boolean hasWordSearch = false;
                    boolean hasWordMatch = false;

                    // עוברים על כל היסטורית המשחקים של המשתמש ובודקים אם קיימים אצלו משחקי עבר
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type = doc.getString("type"); // מקבל את סוג המשחק ושומר אותו

                        if ("TRIVIA".equals(type)) {
                            // אם המשחק הוא טריוויה קוראים למתודה addTriviaCard
                            // ויוצרים קארד וויו למשחק הזה שמכיל את הנתונים למטה
                            addTriviaCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("score"),
                                    doc.getString("duration")
                            );
                            hasTrivia = true;// מצאנו משחק טריוויה

                        } else if ("SPEEDTRIVIA".equals(type)) {
                            // אם המשחק הוא טריוויה מהירה קוראים למתודה addTriviaCard
                            // ויוצרים קארד וויו למשחק הזה שמכיל את הנתונים למטה
                            addSpeedTriviaCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("score")
                                    // אין צורך לשלוח את משך הזמן של המשחק כי הוא תמיד 60 שניות
                            );
                            hasSpeedTrivia = true;// מצאנו משחק טריוויה מהירה

                        } else if ("WORDSEARCH".equals(type)) {
                            // אם המשחק הוא תפזורת קוראים למתודה addTriviaCard
                            // ויוצרים קארד וויו למשחק הזה שמכיל את הנתונים למטה
                            addWordSearchCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("wordsFound"),
                                    doc.getString("duration")
                            );
                            hasWordSearch = true;// מצאנו משחק תפזורת

                        }else if ("WORDMATCH".equals(type)) {
                            // אם המשחק הוא התאמת מילה לתמונה קוראים למתודה addTriviaCard
                            // ויוצרים קארד וויו למשחק הזה שמכיל את הנתונים למטה
                            addWordMatchCard(
                                        doc.getString("date"),
                                        doc.getString("time"),
                                        doc.getString("result"),
                                        doc.getString("stage"),
                                        doc.getString("livesLeft"),
                                        doc.getString("duration")
                                );
                                hasWordMatch = true;// מצאנו משחק התאמת מילה לתמונה
                            }

                    }

                    //אם לא קיימת היסטורית משחקים של המשתמש במשחק מסויים נציג הודעה מתאימה
                    if (!hasTrivia) {
                        addEmptyMessage(triviaContainer, "No classic trivia games played yet");
                    }
                    if (!hasSpeedTrivia) {
                        addEmptyMessage(speedTriviaContainer, "No speed trivia games played yet");
                    }
                    if (!hasWordSearch) {
                        addEmptyMessage(wordSearchContainer, "No word search games played yet");
                    }
                    if (!hasWordMatch) {
                        addEmptyMessage(wordMatchContainer, "No word match games played yet");
                    }
                })
                .addOnFailureListener(e ->// אם לא הצלחנו להביא את היסטוריית המשחקים ממאגר הנתונים נציג הודעת שגיאה
                        tvLoading.setText("Error loading history. Please try again"));
    }


    private void addEmptyMessage(LinearLayout container, String message) {
        TextView empty = new TextView(this);//יוצרים טקסט וויו חדש במסך
        empty.setText(message);// שמים בטקסט וויו את ההודעה ששלחנו למתודה
        empty.setTextColor(0xFFB0BEC5); // צבע אפור לכיתוב בהודעה
        empty.setTextSize(13);
        empty.setPadding(8, 8, 8, 8);// רווח מקצוות תיבת הטקסט
        container.addView(empty);// הכנסת ההודעה ללינאר לייאווט שנשלח למתודה
    }


    private void addTriviaCard(String date, String time, String score, String duration) {
        CardView card = createCard(); // יוצרים קארד וויו

        LinearLayout inner = createInnerLayout(); // יוצרים לינאר לייאווט שאותו נשים בתוך הקארד וויו

        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));// מכניסים את הטקסט לתוך הליניאר לייאווט שיצרנו, טקסט קטן ואפור

        // מכניסים שוב את הטקסט אך הפעם גדול וזהב
        inner.addView(createBoldGoldText("Score: " + score));

        // שוב מכניסים טקסט קטן ואפור
        inner.addView(createSmallText("⏱ Time: " + duration));

        card.addView(inner);// מכניסים את הליניאר לייאווט שיצרנו לקארד וויו
        triviaContainer.addView(card); // מוסיפים את הקארד וויו לליניאר לייאווט של הטריוויה שיצרנו
    }


    private void addSpeedTriviaCard(String date, String time, String score) {
        // יוצרים קארד וויו
        CardView card = createCard();

        LinearLayout inner = createInnerLayout();// יוצרים לינאר לייאווט שאותו נשים בתוך הקארד וויו

        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));// מכניסים את הטקסט לתוך הליניאר לייאווט שיצרנו, טקסט קטן ואפור

        // מכניסים שוב את הטקסט אך הפעם גדול וזהב
        inner.addView(createBoldGoldText("Correct answers: " + score));

        // שוב מכניסים טקסט קטן ואפור
        inner.addView(createSmallText("⏱ Duration: 60 seconds"));

        card.addView(inner);// מכניסים את הליניאר לייאווט שיצרנו לקארד וויו
        speedTriviaContainer.addView(card); // מוסיפים את הקארד וויו לליניאר לייאווט של הטריוויה המהירה שיצרנו
    }

    private void addWordSearchCard(String date, String time, String wordsFound, String duration) {
        CardView card = createCard();

        LinearLayout inner = createInnerLayout();

        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));

        inner.addView(createBoldGoldText("Words found: " + wordsFound));

        inner.addView(createSmallText("⏱ Time: " + duration));

        card.addView(inner);
        wordSearchContainer.addView(card);
    }

    private void addWordMatchCard(String date, String time, String result,
                                  String stage, String livesLeft, String duration) {
        CardView card = createCard();
        LinearLayout inner = createInnerLayout();

        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));
        inner.addView(createBoldGoldText(result));
        inner.addView(createSmallText("📍 Reached: " + stage));
        inner.addView(createSmallText("❤️ Lives left: " + livesLeft));
        inner.addView(createSmallText("⏱ Time: " + duration));

        card.addView(inner);
        wordMatchContainer.addView(card);
    }



    //פעולות עזר לעיצוב הקארד וויו שבונים לכל משחק

    private CardView createCard() {
        CardView card = new CardView(this);// יצירת קארד וויו במסך היסטוריית המשחקים
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(// אורך ורוחב הכרטיס כדי שלא יהרס כשהוא נמצא בתוך הליניאר לייאווט
                LinearLayout.LayoutParams.MATCH_PARENT,// תופס את כל רוחב הליניאר לייאווט
                LinearLayout.LayoutParams.WRAP_CONTENT);// מתרחב ומתכווץ אנכית בהתאם למה שכתוב בתוכו
        params.setMargins(0, 0, 0, 12); // מרווח של 12 פיקסלים בין כל קארד וויו
        card.setLayoutParams(params);// השמת הפרמטרים שיצרנו בשם (params) בתוך הקארד וויו
        card.setRadius(12); // פינות מעוגלות
        card.setCardBackgroundColor(0xFF1A237E); // צבע כחול כהה
        card.setCardElevation(4); // צל קטן לקארד וויו
        return card;
    }

    private LinearLayout createInnerLayout() {
        LinearLayout inner = new LinearLayout(this);// יצירת לינאר לייאווט חדש במסך היסטוריית המשחקים
        inner.setOrientation(LinearLayout.VERTICAL);// הגדרה שהרכיבים בו יהיו מסודרים אנכית אחד מתחת לשני
        inner.setPadding(16, 12, 16, 12);// המרחק בין הדפנות הפנימיות של הלייאווט לבין הקארד וויו שיהיה בתוכו
        return inner;
    }

    private TextView createSmallText(String text) {
        //מקבך טקסט והופך אותו לטקסט וויו חדש לפי עיצוב מסויים
        TextView tv = new TextView(this);// יוצר טקסט וויו חדש במסך היסטוריית המשחיקם
        tv.setText(text);// מציב את הטקסט שקיבלנו בטקסט וויו החדש שיצרנו
        tv.setTextColor(0xFFB0BEC5); // צבע אפור
        tv.setTextSize(12);// כתב גודל 12
        return tv;
    }

    private TextView createBoldGoldText(String text) {
        //מקבך טקסט והופך אותו לטקסט וויו חדש לפי עיצוב מסויים
        TextView tv = new TextView(this);// יוצר טקסט וויו חדש במסך היסטוריית המשחיקם
        tv.setText(text);// מציב את הטקסט שקיבלנו בטקסט וויו החדש שיצרנו
        tv.setTextColor(0xFFFFD700); // צבע זהב
        tv.setTextSize(15);// כתב גודל 15
        tv.setTypeface(null, android.graphics.Typeface.BOLD); // פונט רגיל (null) וטקסט בכתב bold
        return tv;
    }
}
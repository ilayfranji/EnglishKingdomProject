package com.ilay.englishkingdom.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ilay.englishkingdom.R;

public class PracticeActivity extends AppCompatActivity {

    private TextView tvBack;
    private CardView cardTrivia;
    private CardView cardWordSearch;
    private CardView cardWordMatch;
    private TextView btnTriviaInfo;
    private TextView btnWordSearchInfo;
    private TextView btnWordMatchInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        tvBack = findViewById(R.id.tvBack);
        cardTrivia = findViewById(R.id.cardTrivia);
        cardWordSearch = findViewById(R.id.cardWordSearch);
        cardWordMatch = findViewById(R.id.cardWordMatch);
        btnTriviaInfo = findViewById(R.id.btnTriviaInfo);
        btnWordSearchInfo = findViewById(R.id.btnWordSearchInfo);
        btnWordMatchInfo = findViewById(R.id.btnWordMatchInfo);

        tvBack.setOnClickListener(v -> finish()); // סוגר את מסך התרגול ועובר למסך הבית

        // קורא למתודה showTriviaModeDialog()
        cardTrivia.setOnClickListener(v -> showTriviaModeDialog());

        // מעביר למסך התפזורת
        cardWordSearch.setOnClickListener(v ->
                startActivity(new Intent(this, WordSearchActivity.class)));

        // מעביר למסך ההתאמת מילה לתמונה
        cardWordMatch.setOnClickListener(v ->
                startActivity(new Intent(this, WordMatchActivity.class)));

        // לחיצה על כל info קורא למתודה שכתובה אצלו
        btnTriviaInfo.setOnClickListener(v -> showTriviaInfo());
        btnWordSearchInfo.setOnClickListener(v -> showWordSearchInfo());
        btnWordMatchInfo.setOnClickListener(v -> showWordMatchInfo());
    }

    private void showTriviaModeDialog() {
        // מציגים דיאלוג לבחירה בסוג משחק טריוויה
        new AlertDialog.Builder(this)
                .setTitle("Choose Trivia Mode")
                .setItems(new String[]{
                        "Classic - 10 questions",
                        "Speed - 1 minute challenge"
                }, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, TriviaActivity.class)); // מצב רגיל
                    } else {
                        startActivity(new Intent(this, SpeedTriviaActivity.class)); // מצב מהיר
                    }
                })
                .setNegativeButton("Cancel", null)// סוגר את הדיאלוג
                .show();
    }


    private void showTriviaInfo() {
        // דיאלוג הסבר בעברית על 2 סוגי הטריוויה
        new AlertDialog.Builder(this)
                .setTitle("טריוויה - איך משחקים?")
                .setMessage(
                        "מצב קלאסי:\n" +
                                "מוצגת לך מילה באנגלית ועליך לבחור את התרגום הנכון לעברית מתוך 3 אפשרויות. " +
                                "המשחק מורכב מ-10 שאלות. בסוף המשחק יוצג לך הניקוד שלך והזמן שלקח לך.\n\n" +
                                "מצב מהיר:\n" +
                                "יש לך דקה אחת בלבד לענות על כמה שיותר שאלות. " +
                                "לאחר כל תשובה המשחק עובר אוטומטית לשאלה הבאה אחרי חצי שנייה. " +
                                "הניקוד הגבוה ביותר שלך נשמר בפרופיל."
                )
                .setPositiveButton("הבנתי!", null)
                .show();
    }

    private void showWordSearchInfo() {
        // הסבר על משחק התפזורת
        new AlertDialog.Builder(this)
                .setTitle("חיפוש מילים - איך משחקים?")
                .setMessage(
                        "מוצגת לך רשת של אותיות. עליך למצוא מילים באנגלית שמוסתרות בתוך הרשת.\n\n" +
                                "המילים מוסתרות לרוחב (משמאל לימין) או לגובה (מלמעלה למטה).\n\n" +
                                "כדי לסמן מילה - לחץ על האות הראשונה וגרור עד האות האחרונה. " +
                                "מילה שנמצאה תצבע בירוק ותימחק מרשימת המילים למטה.\n\n" +
                                "מצא את כל המילים כמה שיותר מהר!"
                )
                .setPositiveButton("הבנתי!", null)
                .show();
    }

    private void showWordMatchInfo() {
        // הסבר על משחק התאמת מילה לתמונה
        new AlertDialog.Builder(this)
                .setTitle("התאמת מילים - איך משחקים?")
                .setMessage(
                        "בכל שלב יוצגו לך שתי תמונות ורשימה של 6 מילים באנגלית.\n\n" +
                                "עליך להתאים את המילה הנכונה לכל תמונה.\n\n" +
                                "בחר מילה מהרשימה למטה ואז לחץ על התמונה שלדעתך מתאימה לה. " +
                                "אם צדקת התמונה תיצבע בירוק, אם טעית - תיצבע באדום לרגע.\n\n" +
                                "המשחק מורכב מ-3 שלבים. בכל שלב יש תמונות חדשות!"
                )
                .setPositiveButton("הבנתי!", null)
                .show();
    }
}
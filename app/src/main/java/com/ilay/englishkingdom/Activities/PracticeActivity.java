package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open TriviaActivity, SpeedTriviaActivity, WordSearchActivity and WordMatchActivity
import android.os.Bundle; // Used when creating the activity
import android.widget.TextView; // Used for the back button and info buttons

import androidx.appcompat.app.AlertDialog; // Used for mode selection and info dialogs
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used for the game cards

import com.ilay.englishkingdom.R; // Used to reference XML resources

public class PracticeActivity extends AppCompatActivity {
    // This screen shows all available games
    // Trivia card shows a mode selection dialog (Classic vs Speed)
    // Word Search and Word Match cards open their games directly
    // Each card has an info button that explains how to play in Hebrew

    private TextView tvBack; // Back arrow to go back to HomeActivity
    private CardView cardTrivia; // Trivia card - tapping shows Classic vs Speed selection
    private CardView cardWordSearch; // Word Search card - opens game directly
    private CardView cardWordMatch; // Word Match card - opens game directly
    private TextView btnTriviaInfo; // Info button for Trivia
    private TextView btnWordSearchInfo; // Info button for Word Search
    private TextView btnWordMatchInfo; // Info button for Word Match

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

        tvBack.setOnClickListener(v -> finish()); // Go back to HomeActivity

        // Trivia card shows mode selection - Classic or Speed
        cardTrivia.setOnClickListener(v -> showTriviaModeDialog());

        // Word Search opens directly
        cardWordSearch.setOnClickListener(v ->
                startActivity(new Intent(this, WordSearchActivity.class)));

        // Word Match opens directly
        cardWordMatch.setOnClickListener(v ->
                startActivity(new Intent(this, WordMatchActivity.class)));

        // Info buttons - show how to play in Hebrew
        btnTriviaInfo.setOnClickListener(v -> showTriviaInfo());
        btnWordSearchInfo.setOnClickListener(v -> showWordSearchInfo());
        btnWordMatchInfo.setOnClickListener(v -> showWordMatchInfo());
    }

    // ==================== TRIVIA MODE SELECTION ====================

    private void showTriviaModeDialog() {
        // Shows a dialog letting the user pick between Classic and Speed Trivia
        new AlertDialog.Builder(this)
                .setTitle("Choose Trivia Mode")
                .setItems(new String[]{
                        "🧠 Classic — 10 questions",
                        "⚡ Speed — 1 minute challenge"
                }, (dialog, which) -> {
                    if (which == 0) {
                        startActivity(new Intent(this, TriviaActivity.class)); // Classic mode
                    } else {
                        startActivity(new Intent(this, SpeedTriviaActivity.class)); // Speed mode
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== INFO DIALOGS ====================

    private void showTriviaInfo() {
        // Explains both Trivia modes in Hebrew
        new AlertDialog.Builder(this)
                .setTitle("🧠 טריוויה - איך משחקים?")
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
        // Explains Word Search in Hebrew
        new AlertDialog.Builder(this)
                .setTitle("🔍 חיפוש מילים - איך משחקים?")
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
        // Explains Word Match in Hebrew
        new AlertDialog.Builder(this)
                .setTitle("🖼️ התאמת מילים - איך משחקים?")
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
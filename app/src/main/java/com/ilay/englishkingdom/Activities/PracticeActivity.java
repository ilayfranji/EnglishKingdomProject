package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open TriviaActivity, SpeedTriviaActivity and WordSearchActivity
import android.os.Bundle; // Used when creating the activity
import android.widget.TextView; // Used for the back button and info buttons

import androidx.appcompat.app.AlertDialog; // Used for the mode selection dialog and info dialogs
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used for the game cards

import com.ilay.englishkingdom.R; // Used to reference XML resources

public class PracticeActivity extends AppCompatActivity {
    // This screen shows the available games
    // Tapping the Trivia card shows a dialog to pick Classic or Speed mode
    // Tapping the Word Search card opens the game directly
    // Each card has an info button that explains how to play in Hebrew

    private TextView tvBack; // Back arrow to go back to HomeActivity
    private CardView cardTrivia; // The Trivia game card - tapping shows mode selection
    private CardView cardWordSearch; // The Word Search game card
    private TextView btnTriviaInfo; // Info button next to Trivia card
    private TextView btnWordSearchInfo; // Info button next to Word Search card

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        tvBack = findViewById(R.id.tvBack);
        cardTrivia = findViewById(R.id.cardTrivia);
        cardWordSearch = findViewById(R.id.cardWordSearch);
        btnTriviaInfo = findViewById(R.id.btnTriviaInfo);
        btnWordSearchInfo = findViewById(R.id.btnWordSearchInfo);

        tvBack.setOnClickListener(v -> finish()); // Go back to HomeActivity

        // Tapping the Trivia card shows a dialog to pick Classic or Speed mode
        // This way both modes are accessible from the same card without taking extra screen space
        cardTrivia.setOnClickListener(v -> showTriviaModeDialog());

        // Tapping the Word Search card opens the game directly - no mode selection needed
        cardWordSearch.setOnClickListener(v ->
                startActivity(new Intent(this, WordSearchActivity.class)));

        // Info button for Trivia - explains both modes in Hebrew
        btnTriviaInfo.setOnClickListener(v -> showTriviaInfo());

        // Info button for Word Search - explains how to play in Hebrew
        btnWordSearchInfo.setOnClickListener(v -> showWordSearchInfo());
    }

    // ==================== TRIVIA MODE SELECTION ====================

    private void showTriviaModeDialog() {
        // Shows a dialog with two options: Classic Trivia or Speed Trivia
        // Classic = 10 questions, user taps Next between questions
        // Speed = 1 minute, questions auto-advance after 0.5 seconds
        new AlertDialog.Builder(this)
                .setTitle("Choose Trivia Mode")
                .setItems(new String[]{
                        "🧠 Classic — 10 questions",
                        "⚡ Speed — 1 minute challenge"
                }, (dialog, which) -> {
                    if (which == 0) {
                        // Classic mode - open the regular TriviaActivity
                        startActivity(new Intent(this, TriviaActivity.class));
                    } else {
                        // Speed mode - open SpeedTriviaActivity
                        startActivity(new Intent(this, SpeedTriviaActivity.class));
                    }
                })
                .setNegativeButton("Cancel", null) // User changed their mind - close dialog
                .show();
    }

    // ==================== INFO DIALOGS ====================

    private void showTriviaInfo() {
        // Shows a dialog explaining both Trivia modes in Hebrew
        // We stop the card click from also firing when the info button is tapped
        // by not calling cardTrivia's click listener here
        new AlertDialog.Builder(this)
                .setTitle("🧠 טריוויה - איך משחקים?")
                .setMessage(
                        "מצב קלאסי:\n" +
                                "מוצגת לך מילה באנגלית ועליך לבחור את התרגום הנכון לעברית מתוך 3 אפשרויות. " +
                                "המשחק מורכב מ-10 שאלות. בסוף המשחק יוצג לך הניקוד שלך והזמן שלקח לך.\n\n" +
                                "מצב מהיר:\n" +
                                "יש לך דקה אחת בלבד לענות על כמה שיותר שאלות. " +
                                "לאחר כל תשובה המשחק מעביר אוטומטית לשאלה הבאה. " +
                                "הניקוד הגבוה ביותר שלך נשמר בפרופיל."
                )
                .setPositiveButton("הבנתי!", null)
                .show();
    }

    private void showWordSearchInfo() {
        // Shows a dialog explaining Word Search in Hebrew
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
}
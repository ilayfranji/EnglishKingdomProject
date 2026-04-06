package com.ilay.englishkingdom.Activities;

import android.os.Bundle; // Used when creating the activity
import android.view.View; // Used to show and hide the loading text and scroll view
import android.widget.LinearLayout; // Used to hold the history cards for each game type
import android.widget.ScrollView; // Used to show/hide the scrollable content
import android.widget.TextView; // Used for back button, title, loading text and game entries

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used to show each game entry as a card

import com.google.firebase.auth.FirebaseAuth; // Used to get the current user
import com.google.firebase.firestore.FirebaseFirestore; // Used to read game history from Firestore
import com.google.firebase.firestore.Query; // Used to sort history by timestamp newest first
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single history entry
import com.ilay.englishkingdom.R; // Used to reference XML resources

public class GameHistoryActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow to go back to ProfileActivity
    private TextView tvLoading; // Shows "Loading..." while fetching from Firestore
    private ScrollView scrollView; // The scrollable container - hidden until data is loaded
    private LinearLayout triviaContainer; // Where Classic Trivia game cards get added
    private LinearLayout speedTriviaContainer; // Where Speed Trivia game cards get added
    private LinearLayout wordSearchContainer; // Where Word Search game cards get added

    // ==================== FIREBASE ====================

    private FirebaseFirestore db; // Our database connection
    private FirebaseAuth mAuth; // Used to get the current user's ID

    // ==================== LIFECYCLE ====================

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

        tvBack.setOnClickListener(v -> finish());

        // If somehow a guest got here close immediately - guests have no history
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        loadHistory(); // Load all game history from Firestore
    }

    // ==================== LOAD HISTORY ====================

    private void loadHistory() {
        String userId = mAuth.getCurrentUser().getUid();

        // Fetch all game history sorted by timestamp newest first
        // so the most recent games always appear at the top
        db.collection("users").document(userId)
                .collection("gameHistory")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    // Hide loading and show the scrollable content
                    tvLoading.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);

                    // These track whether we found any games of each type
                    // If we didn't find any we show a placeholder message instead
                    boolean hasTrivia = false;
                    boolean hasSpeedTrivia = false;
                    boolean hasWordSearch = false;

                    // Loop through every history entry and create a card for it
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type = doc.getString("type"); // "TRIVIA", "SPEEDTRIVIA" or "WORDSEARCH"

                        if ("TRIVIA".equals(type)) {
                            // Classic Trivia game - add card to the trivia section
                            addTriviaCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("score"),
                                    doc.getString("duration")
                            );
                            hasTrivia = true;

                        } else if ("SPEEDTRIVIA".equals(type)) {
                            // Speed Trivia game - add card to the speed trivia section
                            addSpeedTriviaCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("score")
                                    // No duration for speed trivia - it's always 60 seconds
                            );
                            hasSpeedTrivia = true;

                        } else if ("WORDSEARCH".equals(type)) {
                            // Word Search game - add card to the word search section
                            addWordSearchCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("wordsFound"),
                                    doc.getString("duration")
                            );
                            hasWordSearch = true;
                        }
                    }

                    // Show placeholder messages for game types with no history yet
                    if (!hasTrivia) {
                        addEmptyMessage(triviaContainer, "No classic trivia games played yet.");
                    }
                    if (!hasSpeedTrivia) {
                        addEmptyMessage(speedTriviaContainer, "No speed trivia games played yet.");
                    }
                    if (!hasWordSearch) {
                        addEmptyMessage(wordSearchContainer, "No word search games played yet.");
                    }
                })
                .addOnFailureListener(e ->
                        tvLoading.setText("Error loading history. Please try again."));
    }

    // ==================== EMPTY MESSAGE ====================

    private void addEmptyMessage(LinearLayout container, String message) {
        // Adds a grey placeholder message to a container when no games of that type exist yet
        // We use a helper method to avoid repeating this code 3 times
        TextView empty = new TextView(this);
        empty.setText(message);
        empty.setTextColor(0xFFB0BEC5); // Grey color
        empty.setTextSize(13);
        empty.setPadding(8, 8, 8, 8);
        container.addView(empty);
    }

    // ==================== ADD CLASSIC TRIVIA CARD ====================

    private void addTriviaCard(String date, String time, String score, String duration) {
        // Creates a card showing one Classic Trivia game's results
        CardView card = createCard(); // Create the base card

        LinearLayout inner = createInnerLayout(); // Create the inner layout

        // Date and time row e.g. "📅 28/03/2026  🕐 17:45"
        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));

        // Score row e.g. "Score: 7/10" in gold bold text
        inner.addView(createBoldGoldText("Score: " + score));

        // Duration row e.g. "⏱ Time: 0:45:230"
        inner.addView(createSmallText("⏱ Time: " + duration));

        card.addView(inner);
        triviaContainer.addView(card); // Add card to the trivia section
    }

    // ==================== ADD SPEED TRIVIA CARD ====================

    private void addSpeedTriviaCard(String date, String time, String score) {
        // Creates a card showing one Speed Trivia game's results
        // Speed Trivia doesn't show duration because it's always 60 seconds
        CardView card = createCard();

        LinearLayout inner = createInnerLayout();

        // Date and time row
        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));

        // Score row - shows how many questions were answered correctly
        inner.addView(createBoldGoldText("Correct answers: " + score));

        // Fixed duration - speed trivia is always 60 seconds
        inner.addView(createSmallText("⏱ Duration: 60 seconds"));

        card.addView(inner);
        speedTriviaContainer.addView(card); // Add card to the speed trivia section
    }

    // ==================== ADD WORD SEARCH CARD ====================

    private void addWordSearchCard(String date, String time, String wordsFound, String duration) {
        // Creates a card showing one Word Search game's results
        CardView card = createCard();

        LinearLayout inner = createInnerLayout();

        // Date and time row
        inner.addView(createSmallText("📅 " + date + "  🕐 " + time));

        // Words found row e.g. "Words found: 8/11"
        inner.addView(createBoldGoldText("Words found: " + wordsFound));

        // Duration row
        inner.addView(createSmallText("⏱ Time: " + duration));

        card.addView(inner);
        wordSearchContainer.addView(card); // Add card to the word search section
    }

    // ==================== CARD HELPERS ====================

    // These helper methods create the card and text views so we don't repeat
    // the same styling code inside every addTriviaCard/addSpeedTriviaCard/addWordSearchCard method

    private CardView createCard() {
        // Creates a styled dark blue card with rounded corners and a small shadow
        CardView card = new CardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 12); // 12px bottom margin between cards
        card.setLayoutParams(params);
        card.setRadius(12); // Rounded corners
        card.setCardBackgroundColor(0xFF1A237E); // Dark blue
        card.setCardElevation(4); // Small shadow
        return card;
    }

    private LinearLayout createInnerLayout() {
        // Creates a vertical LinearLayout with padding to hold the card's text views
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 12, 16, 12);
        return inner;
    }

    private TextView createSmallText(String text) {
        // Creates a small grey text view - used for date, time and duration
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFB0BEC5); // Grey color
        tv.setTextSize(12);
        return tv;
    }

    private TextView createBoldGoldText(String text) {
        // Creates a bold gold text view - used for the main result (score/words found)
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFFFFD700); // Gold color
        tv.setTextSize(15);
        tv.setTypeface(null, android.graphics.Typeface.BOLD); // Bold
        return tv;
    }
}
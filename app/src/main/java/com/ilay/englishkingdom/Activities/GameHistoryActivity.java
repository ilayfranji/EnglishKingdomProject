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
import com.google.firebase.firestore.Query; // Used to sort history by timestamp
import com.google.firebase.firestore.QueryDocumentSnapshot; // Represents a single history entry
import com.ilay.englishkingdom.R; // Used to reference XML resources

public class GameHistoryActivity extends AppCompatActivity {

    // ==================== UI ELEMENTS ====================

    private TextView tvBack; // Back arrow to go back to ProfileActivity
    private TextView tvLoading; // Shows "Loading..." while fetching from Firestore
    private ScrollView scrollView; // The scrollable container - hidden until data is loaded
    private LinearLayout triviaContainer; // Where Trivia game cards get added
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
        wordSearchContainer = findViewById(R.id.wordSearchContainer);

        tvBack.setOnClickListener(v -> finish()); // Go back to ProfileActivity

        // If somehow a guest got here close immediately
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        loadHistory(); // Load all game history from Firestore
    }

    // ==================== LOAD HISTORY ====================

    private void loadHistory() {
        String userId = mAuth.getCurrentUser().getUid();

        // Fetch all game history documents sorted by timestamp
        // orderBy("timestamp", DESCENDING) means newest games appear first
        db.collection("users").document(userId)
                .collection("gameHistory")
                .orderBy("timestamp", Query.Direction.DESCENDING) // Newest first
                .get()
                .addOnSuccessListener(snapshots -> {
                    // Hide loading text and show the scrollable content
                    tvLoading.setVisibility(View.GONE);
                    scrollView.setVisibility(View.VISIBLE);

                    boolean hasTrivia = false; // Track if we found any trivia games
                    boolean hasWordSearch = false; // Track if we found any word search games
                    boolean hasSpeedTrivia=false; // Track if we found any speed trivia games

                    // Loop through every history entry and create a card for it
                    for (QueryDocumentSnapshot doc : snapshots) {
                        String type = doc.getString("type"); // "TRIVIA" or "WORDSEARCH"

                        if ("TRIVIA".equals(type)) {
                            // This is a Trivia game entry - add it to the trivia section
                            addTriviaCard(
                                    doc.getString("date"), // e.g. "28/03/2026"
                                    doc.getString("time"), // e.g. "17:45"
                                    doc.getString("score"), // e.g. "7/10"
                                    doc.getString("duration") // e.g. "0:45:230"
                            );
                            hasTrivia = true;

                        } else if ("WORDSEARCH".equals(type)) {
                            // This is a Word Search game entry - add it to the word search section
                            addWordSearchCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("wordsFound"), // e.g. "8/11"
                                    doc.getString("duration")
                            );
                            hasWordSearch = true;
                        } else if ("SPEEDTRIVIA".equals(type)) {
                            // This is a Speed Trivia game entry - add it to the speed trivia section
                            addSpeedTriviaCard(
                                    doc.getString("date"),
                                    doc.getString("time"),
                                    doc.getString("score")
                            );
                            hasSpeedTrivia = true;
                        }

                    }

                    // If no trivia games found show a placeholder message
                    if (!hasTrivia) {
                        TextView empty = new TextView(this);
                        empty.setText("No trivia games played yet.");
                        empty.setTextColor(0xFFB0BEC5); // Grey color
                        empty.setTextSize(13);
                        empty.setPadding(8, 8, 8, 8);
                        triviaContainer.addView(empty);
                    }

                    // If no word search games found show a placeholder message
                    if (!hasWordSearch) {
                        TextView empty = new TextView(this);
                        empty.setText("No word search games played yet.");
                        empty.setTextColor(0xFFB0BEC5); // Grey color
                        empty.setTextSize(13);
                        empty.setPadding(8, 8, 8, 8);
                        wordSearchContainer.addView(empty);
                    }
                    // If no speed trivia games found show a placeholder message
                    if (!hasSpeedTrivia) {
                        TextView empty = new TextView(this);
                        empty.setText("No spped trivia games played yet.");
                        empty.setTextColor(0xFFB0BEC5); // Grey color
                        empty.setTextSize(13);
                        empty.setPadding(8, 8, 8, 8);
                        triviaContainer.addView(empty);
                    }
                })
                .addOnFailureListener(e -> {
                    // Something went wrong reading from Firestore
                    tvLoading.setText("Error loading history. Please try again.");
                });
    }


    // ==================== ADD TRIVIA CARD ====================

    private void addTriviaCard(String date, String time, String score, String duration) {
        // Creates a card showing one Trivia game's results and adds it to the trivia section

        // Create the card
        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12); // Bottom margin between cards
        card.setLayoutParams(cardParams);
        card.setRadius(12); // Rounded corners
        card.setCardBackgroundColor(0xFF1A237E); // Dark blue background
        card.setCardElevation(4); // Small shadow

        // Create the inner layout that holds all the text
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL); // Stack items vertically
        inner.setPadding(16, 12, 16, 12); // Padding inside the card

        // Date and time row
        TextView tvDateTime = new TextView(this);
        tvDateTime.setText("📅 " + date + "  🕐 " + time); // e.g. "📅 28/03/2026  🕐 17:45"
        tvDateTime.setTextColor(0xFFB0BEC5); // Grey color
        tvDateTime.setTextSize(12);

        // Score row
        TextView tvScore = new TextView(this);
        tvScore.setText("Score: " + score); // e.g. "Score: 7/10"
        tvScore.setTextColor(0xFFFFD700); // Gold color
        tvScore.setTextSize(15);
        tvScore.setTypeface(null, android.graphics.Typeface.BOLD); // Bold text

        // Duration row
        TextView tvDuration = new TextView(this);
        tvDuration.setText("⏱ Time: " + duration); // e.g. "⏱ Time: 0:45:230"
        tvDuration.setTextColor(0xFFB0BEC5); // Grey color
        tvDuration.setTextSize(12);

        // Add all text views to the inner layout
        inner.addView(tvDateTime);
        inner.addView(tvScore);
        inner.addView(tvDuration);

        // Add the inner layout to the card
        card.addView(inner);

        // Add the card to the trivia section
        triviaContainer.addView(card);
    }

    // ==================== ADD WORD SEARCH CARD ====================

    private void addWordSearchCard(String date, String time, String wordsFound, String duration) {
        // Creates a card showing one Word Search game's results and adds it to the word search section
        // Same structure as addTriviaCard but shows wordsFound instead of score

        CardView card = new CardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(0, 0, 0, 12);
        card.setLayoutParams(cardParams);
        card.setRadius(12);
        card.setCardBackgroundColor(0xFF1A237E); // Dark blue background
        card.setCardElevation(4);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(16, 12, 16, 12);

        // Date and time row
        TextView tvDateTime = new TextView(this);
        tvDateTime.setText("📅 " + date + "  🕐 " + time);
        tvDateTime.setTextColor(0xFFB0BEC5);
        tvDateTime.setTextSize(12);

        // Words found row
        TextView tvWords = new TextView(this);
        tvWords.setText("Words found: " + wordsFound); // e.g. "Words found: 8/11"
        tvWords.setTextColor(0xFFFFD700); // Gold color
        tvWords.setTextSize(15);
        tvWords.setTypeface(null, android.graphics.Typeface.BOLD);

        // Duration row
        TextView tvDuration = new TextView(this);
        tvDuration.setText("⏱ Time: " + duration);
        tvDuration.setTextColor(0xFFB0BEC5);
        tvDuration.setTextSize(12);

        inner.addView(tvDateTime);
        inner.addView(tvWords);
        inner.addView(tvDuration);

        card.addView(inner);
        wordSearchContainer.addView(card);
    }

}
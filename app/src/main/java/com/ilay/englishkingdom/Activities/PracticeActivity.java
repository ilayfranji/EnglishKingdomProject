package com.ilay.englishkingdom.Activities;

import android.content.Intent; // Used to open Trivia and Word Search screens
import android.os.Bundle; // Used when creating the activity
import android.widget.TextView; // Used for the back button and title

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens
import androidx.cardview.widget.CardView; // Used for the game cards

import com.ilay.englishkingdom.R; // Used to reference XML resources
public class PracticeActivity extends AppCompatActivity {
    // This screen is simple - it just shows two cards
    // Trivia card and Word Search card
    // Tapping each one opens the correct game screen

    private TextView tvBack; // Back arrow to go back to HomeActivity
    private CardView cardTrivia; // The Trivia game card
    private CardView cardWordSearch; // The Word Search game card

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_practice);

        tvBack = findViewById(R.id.tvBack);
        cardTrivia = findViewById(R.id.cardTrivia);
        cardWordSearch = findViewById(R.id.cardWordSearch);

        tvBack.setOnClickListener(v -> finish()); // Go back to HomeActivity

        // Open TriviaActivity when Trivia card is tapped
        cardTrivia.setOnClickListener(v ->
                startActivity(new Intent(this, TriviaActivity.class)));

        // Open WordSearchActivity when Word Search card is tapped
        cardWordSearch.setOnClickListener(v ->
                startActivity(new Intent(this, WordSearchActivity.class)));
    }
}
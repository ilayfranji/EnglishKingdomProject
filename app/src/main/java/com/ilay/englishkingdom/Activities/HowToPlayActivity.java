package com.ilay.englishkingdom.Activities;

import android.os.Bundle; // Used when creating the activity
import android.widget.TextView; // Used for the back button and title

import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.ilay.englishkingdom.R; // Used to reference XML resources

public class HowToPlayActivity extends AppCompatActivity {
    // This screen explains how to use the app step by step
    // It is a simple scrollable screen with steps and icons
    // No Firebase needed here - it's just static content

    private TextView tvBack; // Back arrow to go back to HomeActivity

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_to_play);

        tvBack = findViewById(R.id.tvBack);

        // finish() closes this screen and goes back to HomeActivity
        tvBack.setOnClickListener(v -> finish());
    }
}
package com.ilay.englishkingdom.Activities;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ilay.englishkingdom.R;

public class HowToPlayActivity extends AppCompatActivity {

    private TextView tvBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_how_to_play);

        tvBack = findViewById(R.id.tvBack);

        // סוגר את המסך בלחיצה על back
        tvBack.setOnClickListener(v -> finish());
    }
}
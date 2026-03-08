package com.ilay.englishkingdom.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.ilay.englishkingdom.R;


public class SplashActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // Call the parent class onCreate
        setContentView(R.layout.activity_splash); // Connect this Java file to the XML layout

        // Handler waits for a delay before running code
        new Handler().postDelayed(new Runnable() {

            // run() is the code that will execute after the delay
            @Override
            public void run() {
                // Intent is like a message that says "go to this screen"
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);

                startActivity(intent); // Open the LoginActivity screen

                finish(); // Close the SplashActivity so user can't go back to it
            }

        }, 3000); // 3000 milliseconds = 3 seconds delay
    }
}
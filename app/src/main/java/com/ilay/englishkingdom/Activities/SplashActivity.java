package com.ilay.englishkingdom.Activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.appcompat.app.AppCompatActivity;

import com.ilay.englishkingdom.R;


public class SplashActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // קריאה ל-onCreate של מחלקת האב
        setContentView(R.layout.activity_splash); // חיבור קובץ הג'אווה הזה לעיצוב ה-XML

        // Handler ממתין השהייה מסוימת לפני הרצת הקוד
        new Handler().postDelayed(new Runnable() {

            // run() הוא הקוד שיבוצע לאחר סיום ההשהייה
            @Override
            public void run() {
                // Intent הוא כמו הודעה שאומרת "עבור למסך הזה"
                Intent intent = new Intent(SplashActivity.this, LoginActivity.class);

                startActivity(intent); // פתיחת מסך ה-LoginActivity

                finish(); // סגירת ה-SplashActivity כדי שהמשתמש לא יוכל לחזור אליו
            }

        }, 3000); // 3000 מילישניות = 3 שניות השהייה
    }
}
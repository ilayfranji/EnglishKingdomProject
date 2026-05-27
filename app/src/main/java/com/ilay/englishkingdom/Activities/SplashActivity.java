package com.ilay.englishkingdom.Activities;

import android.content.Context; // Used to check network connectivity
import android.content.Intent; // Used to navigate to LoginActivity
import android.net.ConnectivityManager; // Used to get network connection info
import android.net.NetworkInfo; // Used to check if network is connected
import android.os.Bundle; // Used when creating the activity
import android.os.Handler; // Used to delay navigation after splash
import android.os.Looper; // Used with Handler to run on main thread

import androidx.appcompat.app.AlertDialog; // Used to show the no internet dialog
import androidx.appcompat.app.AppCompatActivity; // The base class for all screens

import com.ilay.englishkingdom.R; // Used to reference XML resources

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // מתודה לבדיקת אינטרנט ישר כשמסך הספלאש נפתח
        checkInternetAndProceed();
    }

    private void checkInternetAndProceed() {
        if (isInternetAvailable()) {        // אם יש חיבור לאינטרנט
            // מסך ספלאש עובד רגיל ואחרי 3 שניות מעבר למסך הבא (על ידי האנדלר) תוך סגירת מסך הספלאש
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivity(new Intent(this, LoginActivity.class));
                finish(); // סגירת המסך שלא נוכל לחזור אליו
            }, 3000); // 3 שניות
        } else {
            // אם אין חיבור לאינטרנט, תוצג הודעה למשתמש שאין חיבור לאינטרנט והוא לא יוכל להיכנס לאפליקציה עד שלא יתחבר לאינטרנט
            showNoInternetDialog();
        }
    }

    private boolean isInternetAvailable() {
        // מפעילים את שירותי הרשת של אנדרואיד, מנהל ויודע מה המצב הרשתי של המכשיר
        ConnectivityManager cm = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        if (cm == null) return false; // אם אין חיבור לאינטרנט מחזיר false למתודה checkInternetAndProceed()

        // מחזיר לנו נתונים על החיבור לאינטרנט (אם מחובר על ידי וויפי או על ידי חבילת גלישה סלולרית)
        // מחזיר null אם אין שום רשת או חבילת גלישה סלולרית שמחוברת למכשיר
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

        // אם החיבור שונה מnull וקיים חיבור לאינטרנט מחזיר true לפעולה checkInternetAndProceed()
        return activeNetwork != null && activeNetwork.isConnected();
    }

    private void showNoInternetDialog() {
        // מוצג דיאלוג שאומר שאין אינטרנט (מוצג רק אם אין חיבור)
        // האפשרות היחידה ללחוץ בדיאלוג היא "נסה שוב" ובכך להריץ שוב את הבדיקה אם יש אינטרנט זמין (קורא למתודה  isInternetAvailable() )
        new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("English Kingdom requires an internet connection to work.\n\nPlease connect to the internet and try again.")
                .setPositiveButton("Try Again", (dialog, which) -> {
                    // לחיצה על נסה שוב
                    checkInternetAndProceed();
                })
                .setCancelable(false) // לא ניתן לבטל את הדיאלוג על ידי לחיצה מחוץ לדיאלוג
                .show();
    }
}
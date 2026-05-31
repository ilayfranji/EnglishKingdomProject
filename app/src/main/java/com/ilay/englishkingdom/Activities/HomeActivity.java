package com.ilay.englishkingdom.Activities;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.R;
import com.ilay.englishkingdom.Receivers.ReminderReceiver;
import com.ilay.englishkingdom.Utils.PermissionManager;

import java.util.Calendar;

public class HomeActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private TextView tvMenu;
    private TextView tvQuote;
    private CardView cardLearn;
    private CardView cardPractice;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private SharedPreferences sharedPreferences;


    private String[] quotes;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // טעינת מערך המשפטים הרנדומלים
        quotes = getResources().getStringArray(R.array.motivational_quotes);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // אתחול הSP המאובטח לצורך שמירת נתונים על המכשיר
        try {
            // בניית מבטח ההצפנה
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();
            sharedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "EnglishKingdomPrefs",
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // הצפנת המפתחות (סוג השדה שנשמר, למשל email)
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // הצפנת הערך שנשמר
            );
        } catch (Exception e) {
            // אם לא הצלחנו לשמור בSP מוצפן נשמור בSP רגיל
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE);
        }

        tvWelcome = findViewById(R.id.tvWelcome);
        tvMenu = findViewById(R.id.tvMenu);
        tvQuote = findViewById(R.id.tvQuote);
        cardLearn = findViewById(R.id.cardLearn);
        cardPractice = findViewById(R.id.cardPractice);

        // קריאה למתודות
        loadUserName();
        showRandomQuote();
        scheduleReminder();
        PermissionManager.askNotificationPermission(this); // מבקש הרשאה לשלוח התראות למשתמש (רק מאנדרואיד 13+)

        // בלחיצה על התפריט קוראים למתודה שמציגה את התפריט
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v);
            }
        });

        cardLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LearnActivity.class));
            }
        });

        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, PracticeActivity.class));
            }
        });
    }

    private void loadUserName() {
        // אם המשתמש לא מחובר נציג הודת שלום אורח
        if (mAuth.getCurrentUser() == null) {
            tvWelcome.setText("Welcome, Guest!");
            return;
        }

        String userId = mAuth.getCurrentUser().getUid(); // שמירת המזהה הייחודי של המשתמש

        db.collection("users").document(userId).get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {// אם קיים שם פרטי למשתמש המחובר
                        String firstName = document.getString("firstName");// שומרים אותו
                        tvWelcome.setText("Welcome, " + firstName + "!"); // מציגים בהודעה על המסך, שלום (שם המשתמש)
                    } else {
                        tvWelcome.setText("Welcome!"); // אם איכשהו אין למשתמש שם פרטי בחשבון שלו מציגים הודעה ללא שם
                    }
                })
                .addOnFailureListener(e -> {
                    tvWelcome.setText("Welcome!"); // אם לא הצלחנו להביא את פרטי המשתמש מהפייר סטור נציג הודעת שלום ללא שם
                });
    }

    private void showRandomQuote() {
        //בחירת אינדקס רנדומלי במערך על ידי math.random והכפלה שלו במספר הערכים במערך,
        //  מקבלים מספר עשרוני ואותו ממירים לטיפוס שלם
        int randomIndex = (int) (Math.random() * quotes.length);
        tvQuote.setText(quotes[randomIndex]);// הצגת המשפט
    }













    /// להמשיך מפה!!!
    private void showMenu(View v) {
        // Shows the hamburger popup menu with Profile, How to Play and Logout options
        PopupMenu popupMenu = new PopupMenu(this, v);
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();

            if (id == R.id.menu_profile) {
                // Open profile screen
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                return true;

            } else if (id == R.id.menu_how_to_play) {
                // Open how to play screen
                startActivity(new Intent(HomeActivity.this, HowToPlayActivity.class));
                return true;

            } else if (id == R.id.menu_logout) {
                logoutUser(); // Sign out and go back to login screen
                return true;
            }
            return false;
        });

        popupMenu.show(); // Display the menu
    }

    // ==================== LOGOUT ====================

    private void logoutUser() {
        // Signs out the user and clears all locally saved data
        mAuth.signOut(); // Sign out from Firebase

        // Clear remember me data from local storage
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("rememberMe", false);
        editor.remove("email");
        editor.apply();

        // Go back to the login screen
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // Close HomeActivity so user can't press back to return here
    }

    // ==================== SCHEDULE REMINDER ====================

    private void scheduleReminder() {
        // Schedules a daily alarm that fires at 11:00 AM
        // If the user restarts their phone just before 11 AM and it comes back on after 11 AM
        // BootReceiver will fire the alarm immediately when the phone finishes booting
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // This intent points to ReminderReceiver - it runs when the alarm fires
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the target time to today at 11:00 AM
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 11); // 11 AM
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // If 11:00 AM already passed today schedule for tomorrow
        // For example if the user opens the app at 2:00 PM we don't fire immediately
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // Move to tomorrow at 11:00 AM
        }

        // Android 12+ requires permission to schedule exact alarms
        // If permission is not granted we fall back to setAndAllowWhileIdle()
        // which is not perfectly exact but still fires when the app is closed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                // Permission granted - schedule exact alarm so it fires at exactly 11:00 AM
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            } else {
                // Permission not granted - use non-exact fallback
                // Still fires when app is closed but might be a few minutes late
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            // Android 11 and below - always exact no permission needed
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
}
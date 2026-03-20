package com.ilay.englishkingdom.Activities;

import android.content.Intent; // משמש לניווט בין מסכים
import android.content.SharedPreferences; // משמש לניקוי נתוני "זכור אותי" בהתנתקות
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle; // משמש בעת יצירת ה-Activity
import android.view.View; // משמש לרפרנס של אלמנטי ממשק משתמש (UI)
import android.widget.PopupMenu; // משמש עבור תפריט הפופ-אפ (המבורגר)
import android.widget.TextView; // משמש עבור תיבות טקסט

import androidx.appcompat.app.AppCompatActivity; // מחלקת הבסיס לכל המסכים
import androidx.cardview.widget.CardView; // משמש עבור תצוגות כרטיס (CardViews)
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences; // משמש להצפנת האחסון המקומי שלנו
import androidx.security.crypto.MasterKey; // משמש ליצירת מפתח ההצפנה

import com.google.firebase.auth.FirebaseAuth; // משמש לקבלת המשתמש הנוכחי והתנתקות
import com.google.firebase.firestore.FirebaseFirestore; // משמש לקבלת נתוני משתמש מ-Firestore
import com.ilay.englishkingdom.R; // משמש לרפרנס של משאבי ה-XML שלנו
import android.app.AlarmManager; // Used to schedule the daily alarm
import android.app.PendingIntent; // Used to tell AlarmManager which receiver to wake up
import android.content.Context; // Used to get system services
import com.ilay.englishkingdom.Receivers.ReminderReceiver; // Our reminder receiver

import java.util.Calendar;

public class HomeActivity extends AppCompatActivity {

    // הצהרה על כל אלמנטי הממשק
    private TextView tvWelcome; // טקסט הודעת הברוכים הבאים
    private TextView tvMenu; // כפתור תפריט ההמבורגר
    private TextView tvQuote; // טקסט הציטוט המוטיבציוני
    private CardView cardLearn; // כרטיס הלמידה
    private CardView cardPractice; // כרטיס התרגול

    private FirebaseAuth mAuth; // החיבור שלנו ל-Firebase Authentication
    private FirebaseFirestore db; // החיבור שלנו למסד הנתונים Firestore
    private SharedPreferences sharedPreferences; // האחסון המקומי המוצפן שלנו

    private String[] quotes; // מערך של ציטוטי מוטיבציה שנטענים מ-strings.xml

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        // getResources().getStringArray() טוען את מערך המחרוזות מ-strings.xml
        // R.array.motivational_quotes הוא ה-ID של מערך הציטוטים שלנו ב-strings.xml
        quotes = getResources().getStringArray(R.array.motivational_quotes);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // אתחול EncryptedSharedPreferences לאחסון נתונים בבטחה
        try {
            // MasterKey הוא מפתח ההצפנה - AES256_GCM היא שיטת הצפנה חזקה מאוד
            MasterKey masterKey = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            // EncryptedSharedPreferences עובד כמו SharedPreferences רגיל אבל מצפין הכל
            sharedPreferences = EncryptedSharedPreferences.create(
                    this,
                    "EnglishKingdomPrefs", // שם קובץ האחסון המקומי שלנו
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // מצפין את המפתחות
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // מצפין את הערכים
            );
        } catch (Exception e) {
            // אם ההצפנה נכשלת, חזור ל-SharedPreferences רגיל
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE);
        }

        // חיבור כל משתנה ג'אווה לתצוגת ה-XML שלו
        tvWelcome = findViewById(R.id.tvWelcome);
        tvMenu = findViewById(R.id.tvMenu);
        tvQuote = findViewById(R.id.tvQuote);
        cardLearn = findViewById(R.id.cardLearn);
        cardPractice = findViewById(R.id.cardPractice);

        loadUserName(); // טעינת שם המשתמש מ-Firestore
        showRandomQuote(); // הצגת ציטוט מוטיבציה אקראי
        scheduleReminder();// Schedule the daily 9:00 AM reminder notification
        askNotificationPermission(); // Ask for notification permission on Android 13+

        // לחיצה על כפתור התפריט - הצגת תפריט הפופ-אפ
        tvMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMenu(v); // העברת ה-view כדי שהתפריט יופיע לידו
            }
        });

        // לחיצה על כרטיס הלמידה - ניווט ל-LearnActivity
        // קודם לכן זה היה שגוי כי מאזין הלחיצה היה מקונן בתוך עצמו!
        // הלחיצה החיצונית הגדירה מאזין לחיצה חדש במקום לנווט בפועל
        // עכשיו זה מנווט בצורה נכונה ל-LearnActivity בעת לחיצה
        cardLearn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, LearnActivity.class)); // מעבר ל-LearnActivity
            }
        });

        // Practice card click - opens the Practice screen where user can pick Trivia or Word Search
        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Start PracticeActivity when the Practice card is tapped
                startActivity(new Intent(HomeActivity.this, PracticeActivity.class));
            }
        });
    }

    private void loadUserName() { // טוען את השם הפרטי של המשתמש מ-Firestore
        if (mAuth.getCurrentUser() == null) { // אם המשתמש הוא אורח (לא מחובר)
            tvWelcome.setText("Welcome, Guest! 👋"); // הצגת ברוכים הבאים כללי
            return; // עצירת המתודה - אין צורך למשוך נתונים מ-Firestore
        }

        String userId = mAuth.getCurrentUser().getUid(); // קבלת ה-ID הייחודי של המשתמש המחובר

        db.collection("users")
                .document(userId) // קבלת המסמך עם ה-ID של המשתמש הזה
                .get() // משיכה מ-Firestore
                .addOnSuccessListener(document -> {
                    if (document.exists()) { // אם המסמך קיים ב-Firestore
                        String firstName = document.getString("firstName"); // קבלת שדה firstName
                        tvWelcome.setText("Welcome, " + firstName + "! 👋"); // הגדרת ברוכים הבאים עם השם האמיתי
                    } else {
                        tvWelcome.setText("Welcome! 👋"); // ברירת מחדל אם המסמך לא קיים
                    }
                })
                .addOnFailureListener(e -> {
                    tvWelcome.setText("Welcome! 👋"); // ברירת מחדל אם Firestore נכשל
                });
    }

    private void showRandomQuote() { // בוחר ומציג ציטוט מוטיבציה אקראי
        // Math.random() מחזיר מספר בין 0.0 ל-1.0
        // הכפלה ב-quotes.length נותנת לנו מספר בין 0 למספר הציטוטים
        // (int) ממיר את זה למספר שלם - זה האינדקס האקראי שלנו
        int randomIndex = (int) (Math.random() * quotes.length);
        tvQuote.setText(quotes[randomIndex]); // הצגת הציטוט באינדקס האקראי
    }

    private void showMenu(View v) { // מציג את תפריט הפופ-אפ (המבורגר)
        PopupMenu popupMenu = new PopupMenu(this, v); // יצירת פופ-אפ - מופיע ליד ה-view שנלחץ
        // Inflate פירושו קריאת קובץ ה-XML ובניית התפריט ממנו
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());

        popupMenu.setOnMenuItemClickListener(item -> { // רץ כשלוחצים על פריט כלשהו בתפריט
            int id = item.getItemId(); // קבלת ה-ID של הפריט שנלחץ

            if (id == R.id.menu_profile) {
                // Open ProfileActivity
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                return true;

            } else if (id == R.id.menu_how_to_play) {
                // Open HowToPlayActivity
                startActivity(new Intent(HomeActivity.this, HowToPlayActivity.class));
                return true;

            } else if (id == R.id.menu_logout) {
                logoutUser();
                return true;
            }

            return false; // false אומר שלא טיפלנו בלחיצה הזו
        });

        popupMenu.show(); // הצגת התפריט
    }

    private void logoutUser() { // מנתק את המשתמש וחוזר למסך ההתחברות
        mAuth.signOut(); // התנתקות מ-Firebase

        // ניקוי נתוני "זכור אותי" מ-SharedPreferences
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("rememberMe", false); // הגדרת rememberMe ל-false
        editor.remove("email"); // הסרת האימייל השמור
        editor.apply(); // שמירת השינויים

        // ניווט ל-LoginActivity
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // סגירת HomeActivity כדי שהמשתמש לא יוכל ללחוץ "חזור" כדי לחזור
    }

    private void scheduleReminder() {
        // This schedules the daily 9:00 AM alarm when the app opens
        // If the alarm is already scheduled, setRepeating just updates it - no duplicates

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Create intent pointing to ReminderReceiver - this is what fires at 9:00 AM
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0, // Request code - 0 is fine since we only have one alarm
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Set the time to 9:00 AM
        Calendar calendar = Calendar.getInstance(); // Start with current date and time
        calendar.set(Calendar.HOUR_OF_DAY, 9); // Change hour to 9
        calendar.set(Calendar.MINUTE, 0); // Change minute to 0
        calendar.set(Calendar.SECOND, 0); // Change second to 0

        // If 9:00 AM already passed today, move to tomorrow
        // For example if it's 10:00 AM we don't want to fire immediately
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // Move forward 1 day
        }

        // Schedule the alarm to repeat every 24 hours starting at 9:00 AM
        // RTC_WAKEUP = wake up the phone even if screen is off
        // INTERVAL_DAY = exactly 24 hours
        alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(), // When to fire the first time
                AlarmManager.INTERVAL_DAY, // How often to repeat
                pendingIntent
        );
    }

    private void askNotificationPermission() {
        // On Android 13+ (API 33+) we must explicitly ask the user for notification permission
        // On older versions notifications are allowed by default - no need to ask
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            // Check if permission is already granted
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission not granted yet - ask the user
                // Android shows a system popup "Allow English Kingdom to send notifications?"
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        200); // 200 = our request code for notification permission
            }
        }
        // On Android 12 and below we don't need to ask - notifications work automatically
    }
}
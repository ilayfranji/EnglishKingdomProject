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


    private void showMenu(View v) {
        PopupMenu popupMenu = new PopupMenu(this, v);// יצירת תפריט במסך הבית, עם הוויו שקיבלנו כפרמטר
        popupMenu.getMenuInflater().inflate(R.menu.home_menu, popupMenu.getMenu());//עושים ניפוח של עיצוב התפריט שיצרנו לאובייקטים שניתן ללחוץ עליהם ולהשתמש בהם בג'אווה

        popupMenu.setOnMenuItemClickListener(item -> {// יוצרים מאזין לחיצה לכל אופציה בתפריט
            int id = item.getItemId();// שומרים את המזהה הייחודי של האופציה שנלחצה

            if (id == R.id.menu_profile) {
                // פותח את מסך הפרופיל
                startActivity(new Intent(HomeActivity.this, ProfileActivity.class));
                return true;// מחזיר לאנדרואיד שהלחיצה על המניו טופלה ואין צורך לבדוק לחיצות נוספות

            } else if (id == R.id.menu_how_to_play) {
                // פותח את מסך איך לשחק
                startActivity(new Intent(HomeActivity.this, HowToPlayActivity.class));
                return true;

            } else if (id == R.id.menu_logout) {
                logoutUser(); // קורא לפעולה logoutUser()
                return true;
            }
            return false;// אם כלום לא נלחץ, מחזירים לאנדרואיד תשובה ששום דבר לא נלחץ
        });

        popupMenu.show(); // הצגת התפריט
    }

    private void logoutUser() {
        mAuth.signOut(); // ניתוק המשתמש ממאגר הנתונים

        // מעדכנים את הSP ומוחקים את כל מה שהיה שמור שם
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("rememberMe", false);
        editor.remove("email");
        editor.apply();

        // החזרה למסך ההתחברות
        Intent intent = new Intent(HomeActivity.this, LoginActivity.class);
        startActivity(intent);
        finish(); // סגירת מסך הבית
    }


    private void scheduleReminder() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);// בקשה מאנדרואיד לקבל גישה לשירות המערכת הרשמי
        // שאחראי על ניהול שעונים מעוררים ומשימות מתוזמנות בטלפון

        Intent intent = new Intent(this, ReminderReceiver.class);// יצירת בקשה של המסך הנוכחי להפעיל את הקוד שיש במחלקה ReminderReceiver
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,// מזהה ייחודי לתזכורת היומית
                intent,// האינטנט שיצרנו למעלה שמפעיל את הקוד ברמיינדר רסיבר
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                // דגל 1 - מונע כפל התראות, אם יש כבר שעון במערכת והנתונים שונו אז הוא רק יתעדכן בנתונים החדשים
                // דגל 2 - יצירת אבטחה לאינטנט שאף אחד לא יוכל לפגוע בו ולהרוס אותו
        );

        Calendar calendar = Calendar.getInstance();// יצירת רובייקט עם התאריך והשעה המדוייקים כרגע
        calendar.set(Calendar.HOUR_OF_DAY, 11); // הגדרת הזמן של התאריך והשעה של היום לשעה 11:00 בבוקר
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // בדיקה אם הזמן הנוכחי כבר מאוחר יותר מהשעה 11:00 בבוקר, אם כן נשלח הודעה מחר
        if (calendar.getTimeInMillis() < System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1); // משנים את האובייקט שאחראי על השעה הנוכחית (מוסיפים לו יום)
        }

        //בדיקה אם גרסת האנדוראיד של המשתמש גדולה או זהה לאנדרואיד 12
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {//בודק האם קיים אישור לשלוח הודעות בשעות מדוייקות
                alarmManager.setExactAndAllowWhileIdle(// קוראים למתודה ששולחת התראות בזמן מדוייק לגמרי (אם כתוב 11:00 היא תשלח ב11:00)
                        AlarmManager.RTC_WAKEUP,// סוג השעון שלפיו עובדת המתודה, שולח התראה בכל מצב, גם אם המסך כבוי או שהמכשיר במצב שינה
                        calendar.getTimeInMillis(),// השעה שתישלח ההודעה
                        pendingIntent// המעטפת שמגדירה מה יופעל כשהזמן יגיע
                );
            } else {
                // אם אין הרשאה לשלוח התראות מדוייקות נשלח בערך השעה 11:00
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.getTimeInMillis(),
                        pendingIntent
                );
            }
        } else {
            // אם הגרסא מתחת לאנדרואיד 12 ניתן ישר לאפשר שליחת התראות מדוייקות
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent
            );
        }
    }
}
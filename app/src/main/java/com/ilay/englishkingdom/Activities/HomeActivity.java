package com.ilay.englishkingdom.Activities;

import android.content.Intent; // משמש לניווט בין מסכים
import android.content.SharedPreferences; // משמש לניקוי נתוני "זכור אותי" בהתנתקות
import android.os.Bundle; // משמש בעת יצירת ה-Activity
import android.view.View; // משמש לרפרנס של אלמנטי ממשק משתמש (UI)
import android.widget.PopupMenu; // משמש עבור תפריט הפופ-אפ (המבורגר)
import android.widget.TextView; // משמש עבור תיבות טקסט

import androidx.appcompat.app.AppCompatActivity; // מחלקת הבסיס לכל המסכים
import androidx.cardview.widget.CardView; // משמש עבור תצוגות כרטיס (CardViews)
import androidx.security.crypto.EncryptedSharedPreferences; // משמש להצפנת האחסון המקומי שלנו
import androidx.security.crypto.MasterKey; // משמש ליצירת מפתח ההצפנה

import com.google.firebase.auth.FirebaseAuth; // משמש לקבלת המשתמש הנוכחי והתנתקות
import com.google.firebase.firestore.FirebaseFirestore; // משמש לקבלת נתוני משתמש מ-Firestore
import com.ilay.englishkingdom.R; // משמש לרפרנס של משאבי ה-XML שלנו

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

        // לחיצה על כרטיס התרגול - TODO: ניווט למסך התרגול
        cardPractice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // TODO: מעבר למסך התרגול (נבנה זאת בהמשך)
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
}
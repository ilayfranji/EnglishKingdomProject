package com.ilay.englishkingdom.Activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.ilay.englishkingdom.R;

public class LoginActivity extends AppCompatActivity {

    // הצהרה על כל רכיבי ממשק המשתמש - מוצהרים כאן כדי שנוכל להשתמש בהם בכל הפונק'
    private EditText etEmail;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnGuest;
    private CheckBox cbRememberMe;
    private TextView tvForgotPassword;
    private TextView tvRegister;

    private FirebaseAuth mAuth; // חיבור לאימות של פיירבייס
    private SharedPreferences sharedPreferences; // מידע מקומי שנשמר בהצפנה

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance(); // אתחול אובייקט ה-Authentication של Firebase לצורך ניהול משתמשים

        // אתחול EncryptedSharedPreferences לשמירת נתונים בצורה מאובטחת על המכשיר
        try {
            // MasterKey הוא מפתח ההצפנה שיגן על הנתונים שלנו
            // AES256_GCM היא שיטת הצפנה חזקה מאוד המשמשת בנקים וממשלות
            MasterKey masterKey = new MasterKey.Builder(this) // "this" מייצג את האקטיביטי הנוכחית
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM) // הגדרת סוג ההצפנה
                    .build(); // בניית המפתח

            // יצירת ה-EncryptedSharedPreferences - עובד בדיוק כמו SharedPreferences רגיל אך מצפין הכל
            sharedPreferences = EncryptedSharedPreferences.create(
                    this, // הקשר (Context) של האקטיביטי הנוכחית
                    "EnglishKingdomPrefs", // שם הקובץ לאחסון המקומי שלנו
                    masterKey, // מפתח ההצפנה שיצרנו הרגע
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, // מצפין את המפתחות (Keys)
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM // מצפין את הערכים (Values)
            );
        } catch (Exception e) { // אם משהו משתבש בתהליך ההצפנה
            sharedPreferences = getSharedPreferences("EnglishKingdomPrefs", MODE_PRIVATE); // חזרה ל-SharedPreferences רגיל כגיבוי
        }

        // חיבור כל משתנה Java לרכיב ה-XML שלו באמצעות ה-ID שנתנו לו ב-XML
        etEmail = findViewById(R.id.etEmail); // חיבור שדה האימייל
        etPassword = findViewById(R.id.etPassword); // חיבור שדה הסיסמה
        btnLogin = findViewById(R.id.btnLogin); // חיבור כפתור ההתחברות
        btnGuest = findViewById(R.id.btnGuest); // חיבור כפתור האורח
        cbRememberMe = findViewById(R.id.cbRememberMe); // חיבור תיבת הסימון "זכור אותי"
        tvForgotPassword = findViewById(R.id.tvForgotPassword); // חיבור טקסט "שכחתי סיסמה"
        tvRegister = findViewById(R.id.tvRegister); // חיבור טקסט "הרשמה"

        // בדיקה אם המשתמש סימן בעבר "זכור אותי"
        boolean rememberMe = sharedPreferences.getBoolean("rememberMe", false); // קבלת הערך השמור, ברירת המחדל היא false
        if (rememberMe && mAuth.getCurrentUser() != null) { // אם "זכור אותי" מסומן וגם המשתמש עדיין מחובר ב-Firebase
            goToHome(); // דלג על מסך ההתחברות ועבור ישירות למסך הבית
            return; // הפסקת הרצת שאר הקוד בתוך onCreate
        }

        // טעינת האימייל השמור במידה והוא קיים
        String savedEmail = sharedPreferences.getString("email", ""); // קבלת האימייל השמור, ברירת מחדל היא מחרוזת ריקה
        if (!savedEmail.isEmpty()) { // אם קיים אימייל שמור
            etEmail.setText(savedEmail); // מילוי שדה האימייל באופן אוטומטי
        }

        // הגדרת מאזין ללחיצה על כפתור ההתחברות
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // רץ כאשר לוחצים על כפתור ההתחברות
                loginUser(); // קריאה למתודת loginUser
            }
        });

        // הגדרת מאזין ללחיצה על כפתור האורח
        btnGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // רץ כאשר לוחצים על כפתור האורח
                showGuestWarning(); // קריאה למתודת showGuestWarning
            }
        });

        // הגדרת מאזין ללחיצה על טקסט "שכחתי סיסמה"
        tvForgotPassword.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { // רץ כאשר לוחצים על שכחתי סיסמה
                resetPassword(); // קריאה למתודת resetPassword
            }
        });

        // הגדרת מאזין ללחיצה על טקסט ההרשמה
        tvRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ניקוי כל השדות לפני מעבר למסך הרשמה
                // זה מבטיח שהשדות יהיו ריקים כשהמשתמש יחזור למסך ההתחברות
                etEmail.setText(""); // ניקוי שדה אימייל
                etPassword.setText(""); // ניקוי שדה סיסמה
                etEmail.setError(null); // הסרת התראת שגיאה משדה האימייל
                etPassword.setError(null); // הסרת התראת שגיאה משדה הסיסמה
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class)); // מעבר ל-RegisterActivity
            }
        });
    }

    private void loginUser() { // מתודה זו מטפלת בלוגיקה של ההתחברות
        String email = etEmail.getText().toString().trim(); // קבלת טקסט האימייל והסרת רווחים מיותרים
        String password = etPassword.getText().toString().trim(); // קבלת טקסט הסיסמה והסרת רווחים מיותרים

        boolean hasError = false; // דגל למעקב אם קיימת שגיאה באחד השדות - אם true, נעצור את הפעולה

        // אימות אימייל - בדיקה אם השדה ריק
        if (email.isEmpty()) {
            etEmail.setError("Email is required"); // הצגת שגיאה ישירות מתחת לשדה האימייל
            hasError = true; // סימון שקיימת שגיאה
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // בדיקה אם פורמט האימייל תקין
            etEmail.setError("Please enter a valid email"); // הצגת שגיאה מתחת לשדה האימייל
            hasError = true; // סימון שקיימת שגיאה
        }

        // אימות סיסמה - בדיקה אם השדה ריק
        if (password.isEmpty()) {
            etPassword.setError("Password is required"); // הצגת שגיאה מתחת לשדה הסיסמה
            hasError = true; // סימון שקיימת שגיאה
        } else if (password.length() < 6) { // בדיקה אם הסיסמה היא באורך של 6 תווים לפחות
            etPassword.setError("Password must be at least 6 characters"); // הצגת שגיאה מתחת לשדה הסיסמה
            hasError = true; // סימון שקיימת שגיאה
        }

        if (hasError) return; // אם קיימת שגיאה באחד השדות, הפסקת המתודה - כל השגיאות יוצגו בבת אחת

        // כל הבדיקות עברו בהצלחה - נבקש מ-Firebase לבצע התחברות עם אימייל וסיסמה
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> { // addOnCompleteListener ממתין ל-Firebase שיסיים את הפעולה
                    if (task.isSuccessful()) { // אם ההתחברות הצליחה

                        // בדיקה אם המשתמש אימת את האימייל שלו
                        if (mAuth.getCurrentUser().isEmailVerified()) { // isEmailVerified() מחזיר true אם המשתמש לחץ על קישור האימות
                            // האימייל מאומת - שמירת "זכור אותי" ומעבר למסך הבית
                            SharedPreferences.Editor editor = sharedPreferences.edit(); // פתיחת SharedPreferences לעריכה
                            if (cbRememberMe.isChecked()) { // אם תיבת "זכור אותי" מסומנת
                                editor.putBoolean("rememberMe", true); // שמירה שזכור אותי = true
                                editor.putString("email", email); // שמירת האימייל
                            } else { // אם "זכור אותי" לא מסומן
                                editor.putBoolean("rememberMe", false); // שמירה שזכור אותי = false
                                editor.remove("email"); // מחיקת אימייל שמור במידה והיה
                            }
                            editor.apply(); // החלת כל השינויים ב-SharedPreferences
                            goToHome(); // מעבר למסך הבית

                        } else { // אם האימייל עדיין לא אומת
                            mAuth.signOut(); // ניתוק מיידי של המשתמש כדי שלא יוכל לגשת לאפליקציה

                            // הצגת הודעה קופצת המורה למשתמש לאמת את האימייל תחילה
                            new AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("Email Not Verified") // כותרת ההודעה
                                    .setMessage("Please verify your email before logging in. Check your inbox.") // תוכן ההודעה
                                    .setPositiveButton("Resend Email", (dialog, which) -> { // כפתור לשליחה חוזרת של אימייל האימות
                                        // התחברות זמנית רק כדי לשלוח שוב את אימייל האימות
                                        mAuth.signInWithEmailAndPassword(email, password)
                                                .addOnSuccessListener(authResult -> {
                                                    authResult.getUser().sendEmailVerification(); // שליחה חוזרת של אימייל האימות
                                                    mAuth.signOut(); // ניתוק מיידי לאחר השליחה
                                                    Toast.makeText(LoginActivity.this, "Verification email sent! Check your inbox 📧", Toast.LENGTH_LONG).show();
                                                });
                                    })
                                    .setNegativeButton("OK", null) // סגירת ההודעה הקופצת
                                    .show(); // הצגת ההודעה
                        }

                    } else { // אם ההתחברות נכשלה
                        Exception exception = task.getException(); // קבלת החרגת השגיאה (Exception) מ-Firebase

                        if (exception instanceof FirebaseAuthInvalidUserException) {
                            // FirebaseAuthInvalidUserException היא שגיאה ספציפית של Firebase
                            // היא אומרת שלא קיים חשבון עם האימייל הזה במערכת
                            etEmail.setError("No user found, please create an account first"); // הצגת שגיאה מתחת לשדה האימייל
                            etEmail.requestFocus(); // העברת הסמן לשדה האימייל

                        } else if (exception instanceof FirebaseAuthInvalidCredentialsException) {
                            // FirebaseAuthInvalidCredentialsException אומרת שהפרטים שגויים
                            // בגרסאות חדשות של Firebase, המערכת לא אומרת אם האימייל או הסיסמה הם השגויים
                            // זה מכוון מטעמי אבטחה - כדי למנוע מהאקרים לדעת אילו אימיילים רשומים
                            // לכן נציג הודעה משולבת עבור אימייל או סיסמה שגויים
                            Toast.makeText(LoginActivity.this, "Incorrect email or password, please try again", Toast.LENGTH_LONG).show();

                        } else if (exception != null && exception.getMessage() != null && exception.getMessage().contains("network")) {
                            // שגיאת רשת - אין חיבור לאינטרנט
                            Toast.makeText(LoginActivity.this, "No internet connection", Toast.LENGTH_LONG).show();

                        } else {
                            // בעיית Firebase - משהו השתבש בצד של Firebase
                            // זה יכול להיות בעיית שרת או כל שגיאה בלתי צפויה אחרת
                            Toast.makeText(LoginActivity.this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void resetPassword() { // מתודה זו שולחת אימייל לאיפוס סיסמה
        String email = etEmail.getText().toString().trim(); // קבלת האימייל משדה הקלט

        if (email.isEmpty()) { // אם שדה האימייל ריק
            etEmail.setError("Please enter your email first"); // הצגת שגיאה מתחת לשדה האימייל
            etEmail.requestFocus(); // העברת הסמן לשדה האימייל
            return; // עצירת המתודה
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { // אם פורמט האימייל לא תקין
            etEmail.setError("Please enter a valid email"); // הצגת שגיאה מתחת לשדה האימייל
            etEmail.requestFocus(); // העברת הסמן לשדה האימייל
            return; // עצירת המתודה
        }

        // בקשה מ-Firebase לשלוח אימייל איפוס סיסמה למשתמש
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> { // המתנה לסיום הפעולה ב-Firebase
                    if (task.isSuccessful()) { // אם האימייל נשלח בהצלחה
                        Toast.makeText(LoginActivity.this, "Reset email sent! Check your inbox 📧", Toast.LENGTH_LONG).show();
                    } else { // אם משהו השתבש
                        Toast.makeText(LoginActivity.this, "Error sending reset email, please try again", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void showGuestWarning() { // מתודה זו מציגה הודעת אזהרה לפני המשך כמשתמש אורח
        new AlertDialog.Builder(this) // יצירת הודעה קופצת חדשה
                .setTitle("Continue as Guest") // קביעת כותרת ההודעה
                .setMessage("As a guest you can access the Learn section and Practice games, but your records will not be saved.") // הודעת האזהרה
                .setPositiveButton("Continue", (dialog, which) -> { // כפתור "המשך" - רץ בעת לחיצה
                    goToHome(); // מעבר למסך הבית כאורח
                })
                .setNegativeButton("Cancel", null) // כפתור "ביטול" - סוגר את ההודעה ולא עושה כלום
                .show(); // הצגת ההודעה הקופצת על המסך
    }

    private void goToHome() { // מתודה זו מבצעת ניווט למסך הבית
        Intent intent = new Intent(LoginActivity.this, HomeActivity.class); // יצירת Intent למעבר ל-HomeActivity
        startActivity(intent); // פתיחת HomeActivity
        finish(); // סגירת LoginActivity כדי שהמשתמש לא יוכל ללחוץ על "חזור" ולחזור למסך ההתחברות
    }
}
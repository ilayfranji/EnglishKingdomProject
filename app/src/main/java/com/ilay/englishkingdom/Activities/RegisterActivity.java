package com.ilay.englishkingdom.Activities;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Activities.Dialogs.ImagePickerHelper;
import com.ilay.englishkingdom.Models.User;
import com.ilay.englishkingdom.Models.UserRole;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    // ==================== רכיבי ממשק משתמש (UI) ====================

    private EditText etFirstName; // שדה קלט לשם פרטי
    private EditText etLastName; // שדה קלט לשם משפחה
    private EditText etEmail; // שדה קלט לאימייל
    private EditText etPassword; // שדה קלט לסיסמה
    private EditText etConfirmPassword; // שדה קלט לאימות סיסמה
    private Button btnRegister; // כפתור הרשמה
    private TextView tvLogin; // קישור "כבר יש לך חשבון? התחבר"
    private ImageView imgProfilePicture; // תמונת הפרופיל העגולה (אוואטר)

    // ==================== פיירבייס (FIREBASE) ====================

    private FirebaseAuth mAuth; // החיבור שלנו לשירות האימות של פיירבייס
    private FirebaseFirestore db; // החיבור שלנו לבסיס הנתונים פיירסטור

    // ==================== טיפול בתמונות ====================

    // ImagePickerHelper מטפל בכל הלוגיקה של מצלמה/גלריה/הרשאות
    // אותו הלפר שבו אנו משתמשים ב-LearnActivity - כותבים את הלוגיקה פעם אחת ומשתמשים שוב
    private ImagePickerHelper imagePicker;

    private Uri selectedImageUri = null; // ה-URI של התמונה שהמשתמש בחר - null אומר שאין תמונה

    // ==================== משגרי תוצאות אקטיביטי (ACTIVITY RESULT LAUNCHERS) ====================

    // אלו חייבים להימצא כאן בתוך ה-Activity - אנדרואיד לא מאפשר ליצור
    // אותם בתוך מחלקות עזר. אנו מעבירים אותם לתוך ה-ImagePickerHelper.

    // משגר גלריה - פותח גלריה, התוצאה חוזרת לכאן ומועברת ל-ImagePickerHelper
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> imagePicker.onGalleryResult(uri)); // העברת התוצאה ל-ImagePickerHelper

    // משגר מצלמה - פותח מצלמה, התוצאה חוזרת לכאן ומועברת ל-ImagePickerHelper
    private final ActivityResultLauncher<Uri> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.TakePicture(),
            success -> imagePicker.onCameraResult(success)); // העברת התוצאה ל-ImagePickerHelper

    // ==================== מחזור חיים (LIFECYCLE) ====================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // חיבור כל משתנה Java לרכיב ה-XML שלו
        etFirstName = findViewById(R.id.etFirstName);
        etLastName = findViewById(R.id.etLastName);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        tvLogin = findViewById(R.id.tvLogin);
        imgProfilePicture = findViewById(R.id.imgProfilePicture);

        // יצירת ImagePickerHelper - כאשר תמונה נבחרת, ה-callback הזה רץ
        // אנו מעדכנים את selectedImageUri וטוענים את התמונה לתוך העיגול של האוואטר
        imagePicker = new ImagePickerHelper(this,
                (uri, fromGallery) -> {
                    // זה רץ לאחר שהמשתמש בוחר או מצלם תמונה
                    selectedImageUri = uri; // שמירת ה-URI כדי שנוכל להעלות אותו במהלך ההרשמה
                    // circleCrop() הופך את התמונה לעגולה כדי להתאים לעיצוב האוואטר שלנו
                    Glide.with(this).load(uri).circleCrop().into(imgProfilePicture);
                },
                galleryLauncher,
                cameraLauncher);

        // לחיצה על האוואטר פותחת אפשרויות שונות בהתאם לשאלה האם תמונה כבר נבחרה
        imgProfilePicture.setOnClickListener(v -> {
            if (selectedImageUri != null) {
                // תמונה כבר נבחרה - הצגת אפשרויות שינוי/מחיקה
                showPhotoOptionsDialog();
            } else {
                // עדיין אין תמונה - הצגת בחירת מצלמה/גלריה ישירות
                imagePicker.show();
            }
        });

        btnRegister.setOnClickListener(v -> registerUser()); // אימות והרשמה בעת לחיצה
        tvLogin.setOnClickListener(v -> finish()); // סגירת ה-RegisterActivity וחזרה להתחברות
    }

    // ==================== תוצאת בקשת הרשאות ====================

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // העברת תוצאת ההרשאה ל-ImagePickerHelper
        // ה-ImagePickerHelper ביקש את ההרשאה ולכן הוא יודע מה לעשות עם התוצאה
        imagePicker.onPermissionResult(requestCode, grantResults);
    }

    // ==================== אפשרויות תמונה ====================

    private void showPhotoOptionsDialog() {
        // מוצג כאשר המשתמש לוחץ על תמונת פרופיל שכבר נבחרה
        // "שינוי" = פתיחת הבחירה מחדש, "מחיקה" = הסרת התמונה וחזרה לאוואטר ברירת המחדל
        new AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(new String[]{"Change Photo", "Delete Photo"}, (dialog, which) -> {
                    if (which == 0) { // נלחץ "שנה תמונה"
                        imagePicker.show(); // פתיחת בחירת מצלמה/גלריה שוב
                    } else { // נלחץ "מחק תמונה"
                        selectedImageUri = null; // ניקוי ה-URI של התמונה הנבחרת
                        // החזרת האוואטר לאייקון ברירת המחדל האפור
                        imgProfilePicture.setImageResource(R.drawable.ic_default_avatar);
                    }
                })
                .setNegativeButton("Cancel", null) // null = פשוט לסגור, לשמור על התמונה הנוכחית
                .show();
    }

    // ==================== הרשמת משתמש ====================

    private void registerUser() {
        // קריאה וניקוי (trim) של כל שדות הקלט
        // trim() מסיר רווחים מקריים בתחילת או בסוף המחרוזת
        String firstName = etFirstName.getText().toString().trim();
        String lastName = etLastName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPassword = etConfirmPassword.getText().toString().trim();

        boolean hasError = false; // אנו משתמשים בדגל זה כדי להציג את כל השגיאות בבת אחת במקום אחת-אחת

        // ---- אימות שם פרטי ----
        if (firstName.isEmpty()) {
            etFirstName.setError("First name is required");
            hasError = true;
        } else if (!firstName.matches("[a-zA-Z]+")) { // רק אותיות - ללא מספרים או סמלים
            etFirstName.setError("First name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(firstName.charAt(0))) {
            // charAt(0) מקבל את התו הראשון - isUpperCase בודק אם זו אות גדולה
            etFirstName.setError("First name must start with a capital letter");
            hasError = true;
        }

        // ---- אימות שם משפחה ----
        if (lastName.isEmpty()) {
            etLastName.setError("Last name is required");
            hasError = true;
        } else if (!lastName.matches("[a-zA-Z]+")) {
            etLastName.setError("Last name must contain letters only");
            hasError = true;
        } else if (!Character.isUpperCase(lastName.charAt(0))) {
            etLastName.setError("Last name must start with a capital letter");
            hasError = true;
        }

        // ---- אימות אימייל ----
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            // Patterns.EMAIL_ADDRESS הוא תבנית מובנית באנדרואיד לאימות פורמט אימייל
            etEmail.setError("Please enter a valid email");
            hasError = true;
        }

        // ---- אימות סיסמה ----
        if (password.isEmpty()) {
            etPassword.setError("Password is required");
            hasError = true;
        } else if (password.length() < 6) {
            // פיירבייס דורש סיסמאות באורך של 6 תווים לפחות
            etPassword.setError("Password must be at least 6 characters");
            hasError = true;
        }

        // ---- אימות אישור סיסמה ----
        if (confirmPassword.isEmpty()) {
            etConfirmPassword.setError("Please confirm your password");
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            // .equals() משווה את תוכן הטקסט בפועל ולא את רפרנס האובייקט
            etConfirmPassword.setError("Passwords do not match");
            hasError = true;
        }

        if (hasError) return; // עצור כאן - אל תירשם אם יש שגיאות כלשהן

        // כל הבדיקות עברו - בדוק אם נבחרה תמונת פרופיל
        if (selectedImageUri != null) {
            // נבחרה תמונת פרופיל - העלה אותה קודם ואז הירשם
            uploadProfilePictureAndRegister(firstName, lastName, email, password);
        } else {
            // אין תמונת פרופיל - הירשם ישירות עם מחרוזת ריקה
            createFirebaseAccount(firstName, lastName, email, password, "");
        }
    }

    // ==================== העלאת תמונת פרופיל ====================

    private void uploadProfilePictureAndRegister(String firstName, String lastName,
                                                 String email, String password) {
        Toast.makeText(this, "Uploading profile picture...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // ה-Preset הלא-חתום שלנו ב-Cloudinary
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // אין מה לעשות כשהעלאה מתחילה
                    @Override public void onProgress(String id, long bytes, long total) {} // ניתן להוסיף כאן סרגל התקדמות
                    @Override public void onReschedule(String id, ErrorInfo e) {} // אין מה לעשות אם יש תזמון מחדש

                    @Override
                    public void onSuccess(String id, Map result) {
                        // ההעלאה הסתיימה - קבלת כתובת ה-HTTPS המאובטחת מ-Cloudinary
                        String profilePictureUrl = (String) result.get("secure_url");
                        // כעת צור את חשבון הפיירבייס עם כתובת ה-URL של התמונה
                        createFirebaseAccount(firstName, lastName, email, password, profilePictureUrl);
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // העלאה נכשלה - עדיין בצע הרשמה אבל ללא תמונת פרופיל
                        Toast.makeText(RegisterActivity.this, "Image upload failed, registering without photo.", Toast.LENGTH_LONG).show();
                        createFirebaseAccount(firstName, lastName, email, password, "");
                    }
                }).dispatch(); // dispatch() מתחיל בפועל את ההעלאה
    }

    // ==================== יצירת חשבון פיירבייס ====================

    private void createFirebaseAccount(String firstName, String lastName,
                                       String email, String password, String profilePictureUrl) {
        // יוצר את חשבון ה-Firebase Authentication עם אימייל וסיסמה
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) { // החשבון נוצר בהצלחה
                        FirebaseUser user = mAuth.getCurrentUser(); // קבלת המשתמש שזה עתה נוצר

                        // שליחת אימייל אימות - המשתמש חייב לאמת לפני שיוכל להתחבר
                        user.sendEmailVerification()
                                .addOnCompleteListener(emailTask -> {
                                    if (!emailTask.isSuccessful()) {
                                        Toast.makeText(this, "Could not send verification email", Toast.LENGTH_LONG).show();
                                    }
                                });

                        saveUserToFirestore(user.getUid(), firstName, lastName, email, profilePictureUrl);

                    } else { // יצירת החשבון נכשלה - בדוק מה השתבש
                        Exception exception = task.getException();
                        if (exception instanceof FirebaseAuthUserCollisionException) {
                            // האימייל כבר רשום - הצג שגיאה בשדה האימייל
                            etEmail.setError("This email is already registered, please login instead");
                            etEmail.requestFocus(); // העברת הסמן לשדה האימייל כדי שהמשתמש יראה את השגיאה
                        } else if (exception != null && exception.getMessage() != null
                                && exception.getMessage().contains("network")) {
                            Toast.makeText(this, "No internet connection", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    // ==================== שמירת משתמש ב-FIRESTORE ====================

    private void saveUserToFirestore(String userId, String firstName, String lastName,
                                     String email, String profilePictureUrl) {
        // יצירת אובייקט User - נקי יותר משימוש ב-HashMap גולמי
        // שדות מחלקת ה-User ממופים ישירות לשדות המסמך ב-Firestore
        User user = new User(
                userId,                     // idFS - זהה ל-UID של Firebase Auth
                firstName,                  // שם פרטי
                lastName,                   // שם משפחה
                email,                      // אימייל
                UserRole.USER.name(),       // תפקיד - תמיד USER ברישום עצמי
                profilePictureUrl,          // תמונת פרופיל - URL מ-Cloudinary או מחרוזת ריקה
                System.currentTimeMillis()  // createdAt - זמן נוכחי במילישניות
        );

        // שימוש ב-UID של Firebase Auth כמזהה המסמך (Document ID)
        // זה מקל על מציאת נתוני משתמש מאוחר יותר באמצעות ה-UID בלבד
        db.collection("users").document(userId).set(user)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Account created! Please verify your email", Toast.LENGTH_LONG).show();
                        finish(); // סגירת ה-RegisterActivity וחזרה ל-LoginActivity
                    } else {
                        Toast.makeText(this, "A Firebase error occurred, please try again later", Toast.LENGTH_LONG).show();
                    }
                });
    }
}
package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.FirebaseFirestore;
import com.ilay.englishkingdom.Models.Category;
import com.ilay.englishkingdom.R;

import java.util.Map;

public class AddCategoryDialog {
    // למחלקה הזו יש תפקיד אחד: להציג את דיאלוג הוספת הקטגוריה ולשמור את הקטגוריה החדשה
    // היא מטפלת ב: הצגת הטופס, אימות קלט, העלאת תמונה ושמירה ל-Firestore
    // LearnActivity פשוט קוראת ל-addDialog.show() - שורה אחת במקום 100+

    // ==================== ממשק CALLBACK ====================

    public interface OnCategoryAddedListener {
        // נקרא לאחר שהקטגוריה נשמרה בהצלחה ב-Firestore
        // LearnActivity משתמשת בזה כדי לדעת מתי השמירה הסתיימה
        void onCategoryAdded();
    }

    // ==================== שדות (FIELDS) ====================

    private final Activity activity; // נחוץ כדי לנפח (inflate) תצוגות ולהציג הודעות טוסט
    private final ImagePickerHelper imagePicker; // מטפל במצלמה/גלריה - אנחנו רק קוראים ל-imagePicker.show()
    private final OnCategoryAddedListener listener; // את מי לעדכן כשהקטגוריה נשמרת
    private final FirebaseFirestore db; // חיבור למסד הנתונים שלנו

    private Uri selectedImageUri = null; // התמונה שהמנהל בחר - null אומר שעדיין אין תמונה
    private ImageView imgPreview; // רפרנס ל-ImageView של התצוגה המקדימה בתוך הדיאלוג
    private Button btnAddImage; // רפרנס לכפתור הוספת תמונה בתוך הדיאלוג
    private AlertDialog openDialog; // רפרנס לדיאלוג עצמו כדי שנוכל לסגור אותו אחרי השמירה

    // ==================== בנאי (CONSTRUCTOR) ====================

    public AddCategoryDialog(Activity activity, ImagePickerHelper imagePicker,
                             OnCategoryAddedListener listener) {
        // הבנאי שומר את כל הדברים שאנחנו צריכים כדי לבצע את העבודה
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== תצוגה (SHOW) ====================

    public void show() {
        selectedImageUri = null; // איפוס התמונה מכל פעם קודמת שהדיאלוג נפתח

        // Inflate = קריאת קובץ ה-XML של העיצוב ובניית אובייקטי ה-View ממנו
        View view = activity.getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etNameHebrew = view.findViewById(R.id.etCategoryNameHebrew);
        imgPreview = view.findViewById(R.id.imgPreview); // שמירת רפרנס - נחוץ ב-onImagePicked()
        btnAddImage = view.findViewById(R.id.btnAddImage); // שמירת רפרנס - נחוץ ב-onImagePicked()

        // כשמנהל לוחץ על כפתור הוספת תמונה, הצג את בחירת המצלמה/גלריה
        btnAddImage.setOnClickListener(v -> imagePicker.show());

        // בניית הדיאלוג עם null עבור הכפתור החיובי כדי שנוכל לטפל בלחיצה ידנית
        // null = אל תסגור אוטומטית כשלוחצים על "הוסף" - אנחנו רוצים להשאיר אותו פתוח אם יש שגיאות
        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Add New Category")
                .setView(view)
                .setPositiveButton("Add", null)
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        // דריסת לחיצת כפתור ה-"Add" אחרי ה-show() כדי שנשלוט מתי הדיאלוג נסגר
        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String nameHebrew = etNameHebrew.getText().toString().trim();
            boolean hasError = false; // אנחנו משתמשים בדגל הזה כדי להציג את כל השגיאות בבת אחת

            // אימות שם באנגלית
            if (name.isEmpty()) { etName.setError("Required"); hasError = true; }
            else if (!name.matches("[a-zA-Z ]+")) { etName.setError("English only"); hasError = true; }

            // אימות שם בעברית
            if (nameHebrew.isEmpty()) { etNameHebrew.setError("Required"); hasError = true; }
            else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { etNameHebrew.setError("Hebrew only"); hasError = true; }

            // אימות שנבחרה תמונה
            if (selectedImageUri == null) {
                Toast.makeText(activity, "Please add an image", Toast.LENGTH_SHORT).show();
                hasError = true;
            }

            if (hasError) return; // עצור כאן - השאר את הדיאלוג פתוח כדי שהמנהל יוכל לתקן שגיאות

            uploadAndSave(name, nameHebrew); // הכל תקין - העלה תמונה ואז שמור ב-Firestore
        });
    }

    // ==================== CALLBACK לבחירת תמונה ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity קוראת לזה אחרי ש-ImagePickerHelper מחזיר צילום
        // LearnActivity היא ה"גשר" בין ImagePickerHelper לבין הדיאלוג הזה
        // מכיוון שה-launchers חייבים לחיות בתוך ה-Activity
        selectedImageUri = uri; // שמירת ה-URI כדי שנוכל להעלות אותו כשלוחצים על "Add"

        // עדכן את ממשק המשתמש רק אם הדיאלוג פתוח וגלוי כרגע
        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE); // הצג את התצוגה המקדימה של התמונה
            Glide.with(activity).load(uri).into(imgPreview); // טען את התמונה שנבחרה לתוך התצוגה המקדימה
            btnAddImage.setVisibility(View.GONE); // הסתר את כפתור הוספת תמונה - התמונה כבר נבחרה

            // הפוך את התצוגה המקדימה ללחיצה כדי שהמנהל יוכל לשנות או למחוק את התמונה
            // בלי זה, לחיצה על התמונה אחרי הבחירה לא תעשה כלום
            imgPreview.setOnClickListener(v -> showSelectedImageOptions(uri));
        }
    }

    // ==================== אפשרויות עבור תמונה שנבחרה ====================

    private void showSelectedImageOptions(Uri uri) {
        // מוצג כשמנהל לוחץ על התצוגה המקדימה של התמונה בתוך דיאלוג ההוספה
        // זה מאפשר למנהל לשנות (לבחור תמונה אחרת) או למחוק (להסיר את התמונה לחלוטין)
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"🔄 Change", "🗑️ Delete"}, (dialog, which) -> {
                    if (which == 0) { // נלחץ Change
                        imagePicker.show(); // פתח שוב את בחירת המצלמה/גלריה כדי לבחור תמונה אחרת
                    } else { // נלחץ Delete
                        selectedImageUri = null; // נקה את ה-URI - לא נבחרה יותר תמונה
                        imgPreview.setVisibility(View.GONE); // הסתר את התצוגה המקדימה
                        imgPreview.setOnClickListener(null); // הסר את מאזין הלחיצה - אין יותר על מה ללחוץ
                        btnAddImage.setVisibility(View.VISIBLE); // הצג שוב את כפתור הוספת תמונה
                    }
                })
                .setNegativeButton("Cancel", null) // null = פשוט סגור, השאר את התמונה כפי שהיא
                .show();
    }

    // ==================== העלאה ושמירה ====================

    private void uploadAndSave(String name, String nameHebrew) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom") // ה-unsigned preset שלנו ב-Cloudinary
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {} // אין מה לעשות כשהעלאה מתחילה
                    @Override public void onProgress(String id, long bytes, long total) {} // ניתן להוסיף כאן סרגל התקדמות
                    @Override public void onReschedule(String id, ErrorInfo e) {} // אין מה לעשות אם ההעלאה מתוזמנת מחדש

                    @Override
                    public void onSuccess(String id, Map result) {
                        // ההעלאה הסתיימה - קבל את כתובת ה-HTTPS של התמונה שהועלתה מ-Cloudinary
                        String url = (String) result.get("secure_url");
                        // צור אובייקט קטגוריה חדש - 0 מילים כי היא חדשה לגמרי
                        Category category = new Category(null, name, nameHebrew, url, 0);
                        // שמירה ל-Firestore - הפעולה add() מייצרת אוטומטית מזהה מסמך (ID)
                        db.collection("categories").add(category)
                                .addOnSuccessListener(r -> {
                                    Toast.makeText(activity, "Category added! 🎉", Toast.LENGTH_SHORT).show();
                                    if (openDialog != null) openDialog.dismiss(); // סגור את הדיאלוג
                                    if (listener != null) listener.onCategoryAdded(); // עדכן את LearnActivity
                                })
                                .addOnFailureListener(e -> Toast.makeText(activity, "Error saving category", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        // ההעלאה נכשלה - הצג את השגיאה, השאר את הדיאלוג פתוח כדי שהמנהל יוכל לנסות שוב
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch(); // dispatch() למעשה מתחיל את ההעלאה
    }
}
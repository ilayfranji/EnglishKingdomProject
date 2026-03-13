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

public class EditCategoryDialog {
    // למחלקה הזו יש תפקיד אחד: להציג את דיאלוג עריכת הקטגוריה ולעדכן את הקטגוריה הקיימת
    // דומה מאוד ל-AddCategoryDialog אבל ממלא מראש את הנתונים הקיימים ומבצע עדכון במקום הוספה
    // LearnActivity פשוט קוראת ל-editCategoryDialog.show(category) - שורה אחת במקום 100+

    // ==================== ממשק CALLBACK ====================

    public interface OnCategoryEditedListener {
        // נקרא לאחר שהקטגוריה עודכנה בהצלחה ב-Firestore
        void onCategoryEdited();
    }

    // ==================== שדות (FIELDS) ====================

    private final Activity activity; // נחוץ כדי לנפח תצוגות ולהציג הודעות טוסט
    private final ImagePickerHelper imagePicker; // מטפל במצלמה/גלריה
    private final OnCategoryEditedListener listener; // את מי לעדכן כשהקטגוריה מעודכנת
    private final FirebaseFirestore db; // חיבור למסד הנתונים שלנו

    private Uri selectedImageUri = null; // תמונה חדשה שנבחרה - null אומר להשאיר את התמונה הקיימת
    private ImageView imgPreview; // רפרנס לתצוגה המקדימה של התמונה בתוך הדיאלוג
    private Button btnAddImage; // רפרנס לכפתור הוספת תמונה בתוך הדיאלוג
    private AlertDialog openDialog; // רפרנס לדיאלוג כדי שנוכל לסגור אותו אחרי השמירה
    private Category currentCategory; // הקטגוריה שנערכת כעת - אנחנו צריכים את ה-ID שלה ואת כתובת התמונה הקיימת

    // ==================== בנאי (CONSTRUCTOR) ====================

    public EditCategoryDialog(Activity activity, ImagePickerHelper imagePicker,
                              OnCategoryEditedListener listener) {
        this.activity = activity;
        this.imagePicker = imagePicker;
        this.listener = listener;
        this.db = FirebaseFirestore.getInstance();
    }

    // ==================== תצוגה (SHOW) ====================

    public void show(Category category) {
        // מקבל את הקטגוריה לעריכה כפרמטר כדי שנוכל למלא מראש את הערכים הנוכחיים שלה
        this.currentCategory = category; // שמירת רפרנס - נחוץ ב-updateFirestore()
        selectedImageUri = null; // איפוס - null פירושו "שמור על התמונה הקיימת" אלא אם המנהל יבחר חדשה

        View view = activity.getLayoutInflater().inflate(R.layout.dialog_category, null);
        EditText etName = view.findViewById(R.id.etCategoryName);
        EditText etNameHebrew = view.findViewById(R.id.etCategoryNameHebrew);
        imgPreview = view.findViewById(R.id.imgPreview);
        btnAddImage = view.findViewById(R.id.btnAddImage);

        // מילוי מראש עם הערכים הקיימים כדי שהמנהל יראה מה שמור כרגע
        etName.setText(category.getCategoryName());
        etNameHebrew.setText(category.getCategoryNameHebrew());

        // הצגת תמונת הקטגוריה הקיימת מכיוון שכבר יש לה אחת
        imgPreview.setVisibility(View.VISIBLE);
        imgPreview.setScaleType(ImageView.ScaleType.FIT_CENTER); // תמונה מלאה ללא חיתוך
        imgPreview.setAdjustViewBounds(true); // שמירה על פרופורציות התמונה
        Glide.with(activity).load(category.getImage()).into(imgPreview); // טעינה מכתובת ה-Cloudinary
        btnAddImage.setVisibility(View.GONE); // הסתרת כפתור הוספת תמונה - התמונה כבר קיימת

        // לחיצה על התמונה הקיימת מציגה אפשרויות שינוי / מחיקה
        imgPreview.setOnClickListener(v -> showImageOptions());

        openDialog = new AlertDialog.Builder(activity)
                .setTitle("Edit Category")
                .setView(view)
                .setPositiveButton("Save", null) // null = טיפול ידני בלחיצה
                .setNegativeButton("Cancel", null)
                .create();

        openDialog.show();

        openDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String nameHebrew = etNameHebrew.getText().toString().trim();
            boolean hasError = false;

            if (name.isEmpty()) { etName.setError("Required"); hasError = true; }
            else if (!name.matches("[a-zA-Z ]+")) { etName.setError("English only"); hasError = true; }

            if (nameHebrew.isEmpty()) { etNameHebrew.setError("Required"); hasError = true; }
            else if (!nameHebrew.matches("[\\u0590-\\u05FF ]+")) { etNameHebrew.setError("Hebrew only"); hasError = true; }

            if (hasError) return;

            if (selectedImageUri != null) {
                // המנהל בחר תמונה חדשה - העלה אותה קודם ואז עדכן את Firestore עם הכתובת החדשה
                uploadAndUpdate(name, nameHebrew);
            } else {
                // אין תמונה חדשה - שמור על כתובת ה-Cloudinary הקיימת מאובייקט הקטגוריה
                updateFirestore(name, nameHebrew, currentCategory.getImage());
                openDialog.dismiss();
            }
        });
    }

    // ==================== CALLBACK לבחירת תמונה ====================

    public void onImagePicked(Uri uri) {
        // LearnActivity קוראת לזה אחרי ש-ImagePickerHelper מחזיר צילום
        selectedImageUri = uri; // שמירה - יועלה כשלוחצים על Save

        if (imgPreview != null && openDialog != null && openDialog.isShowing()) {
            imgPreview.setVisibility(View.VISIBLE);
            Glide.with(activity).load(uri).into(imgPreview); // הצגת התמונה שנבחרה לאחרונה
            btnAddImage.setVisibility(View.GONE);
        }
    }

    // ==================== אפשרויות תמונה ====================

    private void showImageOptions() {
        // מוצג כשמנהל לוחץ על התמונה הקיימת בדיאלוג העריכה
        // Change = בחירת תמונה חדשה, Delete = הסרת התמונה לחלוטין
        new AlertDialog.Builder(activity)
                .setTitle("Image Options")
                .setItems(new String[]{"🔄 Change", "🗑️ Delete"}, (dialog, which) -> {
                    if (which == 0) { // נלחץ Change
                        imagePicker.show(); // פתיחת בחירת מצלמה/גלריה
                    } else { // נלחץ Delete
                        selectedImageUri = null; // ניקוי כל בחירת תמונה חדשה
                        imgPreview.setVisibility(View.GONE); // הסתרת התצוגה המקדימה
                        btnAddImage.setVisibility(View.VISIBLE); // הצגת כפתור הוספת תמונה שוב
                    }
                })
                .setNegativeButton("Cancel", null) // null = פשוט סגור, השאר את התמונה כפי שהיא
                .show();
    }

    // ==================== העלאה ועדכון ====================

    private void uploadAndUpdate(String name, String nameHebrew) {
        Toast.makeText(activity, "Uploading...", Toast.LENGTH_SHORT).show();

        MediaManager.get().upload(selectedImageUri)
                .option("upload_preset", "EnglishKingdom")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id, long bytes, long total) {}
                    @Override public void onReschedule(String id, ErrorInfo e) {}

                    @Override
                    public void onSuccess(String id, Map result) {
                        // העלאה הסתיימה - קבל את כתובת ה-Cloudinary החדשה ועדכן את Firestore
                        String url = (String) result.get("secure_url");
                        updateFirestore(name, nameHebrew, url);
                        if (openDialog != null) openDialog.dismiss(); // סגירת הדיאלוג
                    }

                    @Override
                    public void onError(String id, ErrorInfo e) {
                        Toast.makeText(activity, "Upload failed: " + e.getDescription(), Toast.LENGTH_SHORT).show();
                    }
                }).dispatch();
    }

    // ==================== עדכון FIRESTORE ====================

    private void updateFirestore(String name, String nameHebrew, String imageUrl) {
        // update() משנה רק את השדות שרשמנו - wordCount ושדות אחרים נשארים ללא שינוי
        db.collection("categories").document(currentCategory.getIdFS())
                .update("categoryName", name, "categoryNameHebrew", nameHebrew, "image", imageUrl)
                .addOnSuccessListener(v -> {
                    Toast.makeText(activity, "Category updated! ✅", Toast.LENGTH_SHORT).show();
                    if (listener != null) listener.onCategoryEdited(); // עדכון LearnActivity
                })
                .addOnFailureListener(e -> Toast.makeText(activity, "Error updating category", Toast.LENGTH_SHORT).show());
    }
}
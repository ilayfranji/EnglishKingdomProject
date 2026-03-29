package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;

import com.ilay.englishkingdom.Utils.PermissionManager;

public class ImagePickerHelper {
    // למחלקה הזו יש תפקיד אחד: להציג את בחירת המצלמה/גלריה ולהחזיר את התמונה שנבחרה
    // היא משמשת את AddCategoryDialog, EditCategoryDialog, ו-RegisterActivity
    // כך שאנחנו כותבים את לוגיקת המצלמה/גלריה/הרשאות פעם אחת כאן במקום 3 פעמים

    // ==================== ממשק CALLBACK ====================

    public interface OnImagePickedListener {
        // זהו "callback" - כמו מספר טלפון שאנחנו מתקשרים אליו כשהתמונה מוכנה
        // המחלקה שיוצרת את ImagePickerHelper חייבת לממש את המתודה הזו
        // כשנבחרת תמונה, אנחנו קוראים ל-listener.onImagePicked() כדי להחזיר את התמונה
        void onImagePicked(Uri uri, boolean fromGallery);
    }

    // ==================== שדות (FIELDS) ====================

    private final Activity activity; // נחוץ להצגת דיאלוגים והודעות טוסט
    private final OnImagePickedListener listener; // את מי לעדכן כשהתמונה נבחרת
    private final ActivityResultLauncher<String> galleryLauncher; // פותח את אפליקציית הגלריה
    private final ActivityResultLauncher<Uri> cameraLauncher; // פותח את אפליקציית המצלמה

    private Uri pendingCameraUri = null; // ה-URI של הקובץ הזמני שנוצר לפני הפעלת המצלמה
    // אנחנו שומרים אותו כאן כי המצלמה צריכה קובץ לשמור אליו לפני שהיא נפתחת
    // ואנחנו צריכים לזכור אותו עבור הרגע שבו המצלמה מחזירה תוצאה

    private boolean waitingForCamera = false; // true = ביקשנו הרשאת מצלמה, ממתינים לתגובת המשתמש
    private boolean waitingForGallery = false; // true = ביקשנו הרשאת גלריה, ממתינים לתגובת המשתמש
    // אנחנו צריכים את הדגלים האלו כי אחרי בקשת הרשאה, אנדרואיד קורא ל-onRequestPermissionsResult()
    // ואנחנו צריכים לדעת מה ניסינו לעשות לפני בקשת ההרשאה

    // ==================== בנאי (CONSTRUCTOR) ====================

    public ImagePickerHelper(Activity activity, OnImagePickedListener listener,
                             ActivityResultLauncher<String> galleryLauncher,
                             ActivityResultLauncher<Uri> cameraLauncher) {
        // הבנאי שומר את כל הדברים שאנחנו צריכים כדי לבצע את העבודה
        // ה-launchers מועברים מה-Activity כי הם חייבים להיווצר
        // בתוך ה-Activity באמצעות registerForActivityResult() - אנדרואיד דורש זאת
        // אנחנו לא יכולים ליצור אותם כאן בתוך מחלקת עזר (helper class)
        this.activity = activity;
        this.listener = listener;
        this.galleryLauncher = galleryLauncher;
        this.cameraLauncher = cameraLauncher;
    }

    // ==================== הצגת הבחירה (SHOW PICKER) ====================

    public void show() {
        // מציג פופ-אפ עם שתי אפשרויות: מצלמה או גלריה
        // נקרא בכל פעם שהמנהל לוחץ על "הוסף תמונה" או "שנה תמונה"
        new AlertDialog.Builder(activity)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) { // אינדקס 0 = נלחצה מצלמה
                        if (PermissionManager.hasCameraPermission(activity)) {
                            // הרשאה כבר ניתנה - הפעל מצלמה מיד
                            launchCamera();
                        } else {
                            // הרשאה עדיין לא ניתנה - שאל את המשתמש
                            // אנחנו מגדירים waitingForCamera = true כדי שכשתחזור תוצאת ההרשאה
                            // נדע שעלינו להפעיל את המצלמה
                            waitingForCamera = true;
                            PermissionManager.requestCameraPermission(activity);
                        }
                    } else { // אינדקס 1 = נלחצה גלריה
                        if (PermissionManager.hasGalleryPermission(activity)) {
                            // הרשאה כבר ניתנה - הפעל גלריה מיד
                            launchGallery();
                        } else {
                            // הרשאה עדיין לא ניתנה - שאל את המשתמש
                            waitingForGallery = true;
                            PermissionManager.requestGalleryPermission(activity);
                        }
                    }
                })
                .setNegativeButton("Cancel", null) // null = פשוט סגור, אל תעשה כלום
                .show();
    }

    // ==================== תוצאת הרשאה (PERMISSION RESULT) ====================

    public void onPermissionResult(int requestCode, int[] grantResults) {
        // מתודת onRequestPermissionsResult() של ה-Activity קוראת למתודה הזו
        // מכיוון ש-ImagePickerHelper הוא זה שביקש את ההרשאה
        // והוא זה שיודע מה לעשות אחרי קבלת התוצאה

        // בדוק אם המשתמש לחץ על "אפשר" (PERMISSION_GRANTED) או "דחה"
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == PermissionManager.CAMERA_PERMISSION_CODE) {
            // זו התגובה לבקשת הרשאת המצלמה שלנו
            if (granted && waitingForCamera) {
                // המשתמש אישר מצלמה והמתנו להפעיל אותה - הפעל כעת
                launchCamera();
            } else if (!granted) {
                // המשתמש דחה הרשאת מצלמה - הצג הודעה
                Toast.makeText(activity, "Camera permission denied.", Toast.LENGTH_SHORT).show();
            }
            waitingForCamera = false; // איפוס הדגל - אנחנו כבר לא ממתינים

        } else if (requestCode == PermissionManager.GALLERY_PERMISSION_CODE) {
            // זו התגובה לבקשת הרשאת הגלריה שלנו
            if (granted && waitingForGallery) {
                // המשתמש אישר גלריה והמתנו להפעיל אותה - הפעל כעת
                launchGallery();
            } else if (!granted) {
                Toast.makeText(activity, "Gallery permission denied.", Toast.LENGTH_SHORT).show();
            }
            waitingForGallery = false; // איפוס הדגל
        }
    }

    // ==================== CALLBACKS לתוצאות LAUNCHER ====================

    public void onGalleryResult(Uri uri) {
        // ה-galleryLauncher של ה-Activity קורא לזה אחרי שהמשתמש בוחר תמונה
        // אנחנו מעבירים את התוצאה למי שמקשיב (דיאלוג הוספה, דיאלוג עריכה, הרשמה)
        if (uri != null) listener.onImagePicked(uri, true); // true = הגיע מהגלריה
    }

    public void onCameraResult(boolean success) {
        // ה-cameraLauncher של ה-Activity קורא לזה אחרי שהמשתמש מצלם תמונה
        // success = true אומר שהתמונה צולמה, false אומר שהמשתמש ביטל
        if (success && pendingCameraUri != null) {
            listener.onImagePicked(pendingCameraUri, false); // false = הגיע מהמצלמה
        } else {
            pendingCameraUri = null; // המשתמש ביטל - נקה את ה-URI הזמני
        }
    }

    // ==================== GETTERS ====================

    public Uri getPendingCameraUri() {
        // LearnActivity צריכה את זה כדי לשמור/לשחזר את ה-URI כשאנדרואיד הורג את ה-activity
        // (למשל כשהמצלמה נפתחת אנדרואיד עשוי להרוג את האפליקציה כדי לפנות זיכרון)
        return pendingCameraUri;
    }

    public void setPendingCameraUri(Uri uri) {
        // LearnActivity קוראת לזה כדי לשחזר את ה-URI אחרי שאנדרואיד בונה מחדש את ה-activity
        pendingCameraUri = uri;
    }

    // ==================== עוזרי עזר פרטיים (PRIVATE HELPERS) ====================

    private void launchCamera() {
        // יוצר קובץ זמני תחילה, ואז פותח את המצלמה המכוונת לקובץ הזה
        // המצלמה צריכה לדעת איפה לשמור את התמונה לפני שהיא נפתחת
        pendingCameraUri = createTempUri();
        cameraLauncher.launch(pendingCameraUri);
    }

    private void launchGallery() {
        // פותח את הגלריה - "image/*" אומר לקבל כל סוג תמונה (jpg, png וכו')
        galleryLauncher.launch("image/*");
    }

    private Uri createTempUri() {
        // יוצר קובץ ריק זמני בתיקיית ה-cache הפרטית של האפליקציה שלנו
        // System.currentTimeMillis() הופך את שם הקובץ לייחודי בכל פעם
        java.io.File photo = new java.io.File(activity.getCacheDir(),
                "temp_photo_" + System.currentTimeMillis() + ".jpg");
        // FileProvider הופך את נתיב הקובץ ל-URI מאובטח שאפליקציית המצלמה יכולה לכתוב אליו
        // בלי FileProvider המצלמה תיחסם משמירה (כלל אבטחה של אנדרואיד)
        return androidx.core.content.FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".provider", photo);
    }
}
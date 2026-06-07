package com.ilay.englishkingdom.Activities.Dialogs;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;

import com.ilay.englishkingdom.Utils.PermissionManager;

public class ImagePickerHelper {
    //המחלקה מציגה את התמונה שנבחרה מהמצלמה או הגלריה ומחזירה אותה למסך שמבקש


    public interface OnImagePickedListener {
        // זה callback, כמו מספר טלפון שאנחנו מתקשרים אליו כשהתמונה מוכנה
        // המחלקה שיוצרת את ImagePickerHelper חייבת לממש את המתודה הזו
        // כשנבחרת תמונה, אנחנו קוראים לlistener.onImagePicked() כדי להחזיר את התמונה
        void onImagePicked(Uri uri, boolean fromGallery);
    }

    private final Activity activity; //  לצורך הצגת דיאלוגים והודעות טוסט
    private final OnImagePickedListener listener; // את מי לעדכן כשהתמונה נבחרת
    private final ActivityResultLauncher<String> galleryLauncher; // פותח את אפליקציית הגלריה
    private final ActivityResultLauncher<Uri> cameraLauncher; // פותח את אפליקציית המצלמה

    private Uri pendingCameraUri = null; // הכתובת של התמונה שתגיע מהמצלמה כדי שלא תימחק

    private boolean waitingForCamera = false; // true = ביקשנו הרשאת מצלמה, ממתינים לתגובת המשתמש
    private boolean waitingForGallery = false; // true = ביקשנו הרשאת גלריה, ממתינים לתגובת המשתמש

    // אנחנו צריכים את הדגלים האלו כי אחרי בקשת הרשאה, אנדרואיד קורא ל-onRequestPermissionsResult()
    // ואנחנו צריכים לדעת מה ניסינו לעשות לפני בקשת ההרשאה



    //יוצרים ImagePickerHelper חדש
    public ImagePickerHelper(Activity activity, OnImagePickedListener listener,
                             ActivityResultLauncher<String> galleryLauncher,
                             ActivityResultLauncher<Uri> cameraLauncher) {
        // הבנאי שומר את כל הדברים שאנחנו צריכים כדי לבצע את העבודה
        this.activity = activity;
        this.listener = listener;
        this.galleryLauncher = galleryLauncher;
        this.cameraLauncher = cameraLauncher;
    }

    // ==================== הצגת הבחירה (SHOW PICKER) ====================

    public void show() {
        //מציג את דיאלוג הבחירה
        new AlertDialog.Builder(activity)
                .setTitle("Choose Image Source")
                .setItems(new String[]{"Camera", "Gallery"}, (dialog, which) -> {
                    if (which == 0) { // אינדקס 0 = נלחצה מצלמה
                        if (PermissionManager.hasCameraPermission(activity)) {
                            // בודק אם יש הרשאה לפתיחת מצלמה, אם כן נקרא למתודה launchCamera
                            launchCamera();
                        } else {
                            //אם אין הרשאה, נבקש הרשאה ונשים "תזכורת" שהתשובה להרשאה שביקשנו תהיה עבור המצלמה
                            waitingForCamera = true;
                            PermissionManager.requestCameraPermission(activity);
                        }
                    } else { // אינדקס 1 = נלחצה גלרייה
                        if (PermissionManager.hasGalleryPermission(activity)) {
                            // בודק אם יש הרשאה לפתיחת גלרייה, אם כן נקרא למתודה launchGallery
                            launchGallery();
                        } else {
                            //אם אין הרשאה, נבקש הרשאה ונשים "תזכורת" שהתשובה להרשאה שביקשנו תהיה עבור הגלרייה
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

        // בדיקה אם יש תשובה להרשאה אחת לפחות ואם ההרשאה אושרה
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        if (requestCode == PermissionManager.CAMERA_PERMISSION_CODE) { // האם הקוד שווה לקוד של הרשאת מצלמה
            //אם התנאי מעל התקיים ויש אישור לפי תוצאות ההרשאות שקיבלנו ואנחנו מחכים לתשובה על מצלמה
            if (granted && waitingForCamera) {
                // נקרא למתודה launchCamera
                launchCamera();
            } else if (!granted) {
                // אם המשתמש דחה את ההרשאה מציגים הודעה מתאימה
                Toast.makeText(activity, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
            waitingForCamera = false; // "תזכורת" שאנחנו לא מחכים לתשובה יותר על הרשאת מצלמה

        } else if (requestCode == PermissionManager.GALLERY_PERMISSION_CODE) {
            // האם הקוד שווה לקוד של הרשאת גלרייה
            if (granted && waitingForGallery) {
                //אם התנאי מעל התקיים ויש אישור לפי תוצאות ההרשאות שקיבלנו ואנחנו מחכים לתשובה על גלרייה
                launchGallery(); // נקרא למתודה launchGallery
            } else if (!granted) { // אם המשתמש לחץ דחה מציגים הודעה מתאימה
                Toast.makeText(activity, "Gallery permission denied", Toast.LENGTH_SHORT).show();
            }
            waitingForGallery = false; // "תזכורת" שאנחנו לא מחכים לתשובה יותר על הרשאת גלרייה
        }
    }

    // ==================== CALLBACKS לתוצאות LAUNCHER ====================

    public void onGalleryResult(Uri uri) {
        // האקטביטי קורא לפעולה הזאת ומעביר לה את התמונה מהגלרייה
        if (uri != null) listener.onImagePicked(uri, true); // true = הגיע מהגלריה
        // מעבירים את התמונה בחזרה לאקטיביטי
    }

    public void onCameraResult(boolean success) {
        // האקטביטי קורא לפעולה הזאת ומעביר לה את המידע האם הצלחנו לצלם תמונה או לא
        // success = true אומר שהתמונה צולמה, false אומר שהמשתמש ביטל
        if (success && pendingCameraUri != null) {// אם הצלחנו לצלם ויש מקום שבו התמונה נשמרה
            listener.onImagePicked(pendingCameraUri, false); // false = הגיע מהמצלמה
            // מעבירים את התמונה מהמצלמה לאקטיביטי
        } else {
            pendingCameraUri = null; // אם אין תמונה נמחק את המקום שבו נשמרה התמונה
        }
    }

    public Uri getPendingCameraUri() {
        //מביא את הכתובת שבו שמורה התמונה מהמצלמה
        return pendingCameraUri;
    }

    public void setPendingCameraUri(Uri uri) {
        // מציבים את הכתובת של התמונה שצולמה מהמצלמה ב"תא האחסון" שיצרנו
        pendingCameraUri = uri;
    }

    private void launchCamera() {
        // יוצר קובץ זמני תחילה, ואז פותח את המצלמה המכוונת לקובץ הזה
        // המצלמה צריכה לדעת איפה לשמור את התמונה לפני שהיא נפתחת
        pendingCameraUri = createTempUri();
        cameraLauncher.launch(pendingCameraUri);
    }

    private void launchGallery() {
        // פותח את הגלריה. "image/*" אומר לקבל כל סוג תמונה (jpg, png וכו')
        galleryLauncher.launch("image/*");
    }

    private Uri createTempUri() {
        // יוצר קובץ ריק זמני בתיקיית ה-cache הפרטית של האפליקציה שלנו
        // System.currentTimeMillis() הופך את שם הקובץ לייחודי בכל פעם
        java.io.File photo = new java.io.File(activity.getCacheDir(),
                "temp_photo_" + System.currentTimeMillis() + ".jpg");
        // FIleProvider מאפשר כניסה חד פעמית לקובץ שיצרנו רק לשים את התמונה בו
        // בלי FileProvider לא נצליח לשמור את התמונה (כלל אבטחה של אנדרואיד)
        return androidx.core.content.FileProvider.getUriForFile(activity,
                activity.getPackageName() + ".provider", photo);
        //הכנסת התמונה לתיקייה החדשה שנוצרה
    }
}
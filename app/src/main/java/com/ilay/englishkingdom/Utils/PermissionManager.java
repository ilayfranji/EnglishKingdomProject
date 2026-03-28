package com.ilay.englishkingdom.Utils;

import android.app.Activity;
import android.content.pm.PackageManager; // משמש לבדיקה אם ההרשאה ניתנה
import android.os.Build;

import androidx.core.app.ActivityCompat; // משמש לבקשת הרשאות
import androidx.core.content.ContextCompat; // משמש לבדיקת סטטוס ההרשאה

public class PermissionManager {
    // מחלקה זו מטפלת בכל בקשות ההרשאות באפליקציה במקום אחד
    // במקום לחזור על קוד ההרשאות בכל Activity, אנחנו פשוט קוראים למחלקה זו
    // חשבו על זה כעל עוזר שיודע איך לבקש מהמשתמש הרשאות בצורה מנומסת

    // ==================== קודי בקשה (REQUEST CODES) ====================
    // כל בקשת הרשאה צריכה מספר ייחודי כדי שנדע לאיזו בקשה המשתמש הגיב
    // כשאנדרואיד מעדכן אותנו בתשובת המשתמש, הוא מעביר בחזרה את המספר הזה
    // אנו משתמשים בקבועים (final) כדי שהמספרים האלו לא ישתנו בטעות

    public static final int CAMERA_PERMISSION_CODE = 101; // מזהה עבור בקשות הרשאת מצלמה
    public static final int GALLERY_PERMISSION_CODE = 102; // מזהה עבור בקשות הרשאת גלריה

    // ==================== בדיקת הרשאת מצלמה ====================

    public static boolean hasCameraPermission(Activity activity) {
        // מחזיר true אם הרשאת המצלמה כבר ניתנה, false אם לא
        // ContextCompat.checkSelfPermission בודק את הסטטוס הנוכחי של הרשאה
        // PERMISSION_GRANTED אומר שהמשתמש כבר אישר אותה
        return ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // ==================== בדיקת הרשאת גלריה ====================

    public static boolean hasGalleryPermission(Activity activity) {
        // הרשאת גלריה שונה בהתאם לגרסת האנדרואיד
        // אנדרואיד 13 ומעלה (API 33+) משתמש ב-READ_MEDIA_IMAGES (הרשאה ספציפית יותר)
        // אנדרואיד 12 ומטה משתמש ב-READ_EXTERNAL_STORAGE (הרשאה רחבה יותר)
        // Build.VERSION.SDK_INT נותן לנו את מספר גרסת האנדרואיד של המכשיר הנוכחי
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // אנדרואיד 13+ - בדוק READ_MEDIA_IMAGES
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // אנדרואיד 12 ומטה - בדוק READ_EXTERNAL_STORAGE
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    // ==================== בקשת הרשאת מצלמה ====================

    public static void requestCameraPermission(Activity activity) {
        // מבקש מהמשתמש לאפשר גישה למצלמה
        // אנדרואיד מציג פופ-אפ מערכת: "האם לאפשר ל-English Kingdom לצלם תמונות ווידאו?"
        // תשובת המשתמש (אפשר/דחה) חוזרת בתוך onRequestPermissionsResult()
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE); // מעביר את המזהה שלנו כדי שנדע שזה עבור המצלמה
    }

    // ==================== בקשת הרשאת גלריה ====================

    public static void requestGalleryPermission(Activity activity) {
        // מבקש מהמשתמש לאפשר גישה לגלריה/אחסון
        // בדומה למצלמה - אנדרואיד מציג פופ-אפ מערכת והתוצאה חוזרת ב-onRequestPermissionsResult()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // אנדרואיד 13+ - בקש READ_MEDIA_IMAGES
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},
                    GALLERY_PERMISSION_CODE);
        } else {
            // אנדרואיד 12 ומטה - בקש READ_EXTERNAL_STORAGE
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    GALLERY_PERMISSION_CODE);
        }
    }
    public static void askNotificationPermission(Activity activity){
        // On Android 13+ (API 33+) we must explicitly ask the user for notification permission
        // On older versions notifications are allowed by default - no need to ask
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            // Check if permission is already granted
            if (ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission not granted yet - ask the user
                // Android shows a system popup "Allow English Kingdom to send notifications?"
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        200); // 200 = our request code for notification permission
            }
        }
        // On Android 12 and below we don't need to ask - notifications work automatically
    }
}
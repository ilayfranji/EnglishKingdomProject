package com.ilay.englishkingdom.Utils;

import android.app.Activity;
import android.content.pm.PackageManager; // משמש לבדיקה אם ההרשאה ניתנה
import android.os.Build;

import androidx.core.app.ActivityCompat; // משמש לבקשת הרשאות
import androidx.core.content.ContextCompat; // משמש לבדיקת סטטוס ההרשאה

public class PermissionManager {
    //לצורך טיפול בכל ההרשאות שהאפליקציה מבקשת


    //קודי הרשאות
    public static final int CAMERA_PERMISSION_CODE = 101; // מזהה עבור בקשות הרשאת מצלמה
    public static final int GALLERY_PERMISSION_CODE = 102; // מזהה עבור בקשות הרשאת גלריה
    public static final int NOTIFICATION_PERMISSION_CODE = 200; // מזהה עבור בקשות הרשאת התראות


    public static boolean hasCameraPermission(Activity activity) {
        //בודק את הסטטוס הנוכחי של ההרשאה ומחזיר true אם היא קיימת
        return ContextCompat.checkSelfPermission(activity,
                android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }


    public static boolean hasGalleryPermission(Activity activity) {
        //אם גרסת האנדוראיד מעל 13
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // בדוק אם יש הרשאה לעבוד עם תמונות
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
        } else {
            // אם האנדרואיד הוא 12 ומטה בדוק אם יש הרשאה לעבוד עם קבצים חיצוניים
            return ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }


    public static void requestCameraPermission(Activity activity) {
        // מבקש מהמשתמש לאפשר גישה למצלמה
        // תשובת המשתמש חוזרת בתוך onRequestPermissionsResult() למסך שבו ביקשנו את ההרשאה
        ActivityCompat.requestPermissions(activity,
                new String[]{android.Manifest.permission.CAMERA},
                CAMERA_PERMISSION_CODE); // מעביר את המזהה שלנו כדי שנדע שזה עבור המצלמה
    }


    public static void requestGalleryPermission(Activity activity) {
        // מבקש מהמשתמש לאפשר גישה לגלריה/אחסון
        // אנדרואיד מציג פופ-אפ מערכת והתוצאה חוזרת ב-onRequestPermissionsResult()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // אם אנחנו באנדרואיד 13 ומעלה נבקש הרשאה ספציפית
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_MEDIA_IMAGES},
                    GALLERY_PERMISSION_CODE);
        } else {
            // אנדרואיד 12 ומטה נבקש הרשאה יותר כללית
            ActivityCompat.requestPermissions(activity,
                    new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    GALLERY_PERMISSION_CODE);
        }
    }
    public static void askNotificationPermission(Activity activity){
        //מבקשים הרשאה רק אם אנחנו גרסא 13 ומעלה
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            // בדיקה אם אין לנו אישור להרשאה
            if (ContextCompat.checkSelfPermission(activity,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                //בקשת הרשאה
                // אנדרואיד מציג פופ-אפ מערכת והתוצאה חוזרת ב-onRequestPermissionsResult()
                ActivityCompat.requestPermissions(activity,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
            }
        }
    }
}
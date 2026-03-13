package com.ilay.englishkingdom; // החבילה שבה הקובץ הזה נמצא - בחבילה הראשית, לא ב-Activities

import android.app.Application; // Application היא מחלקת בסיס של אנדרואיד שרצה לפני שכל Activity מתחילה

import com.cloudinary.android.MediaManager; // MediaManager היא המחלקה הראשית של Cloudinary - אנו משתמשים בה להעלאת תמונות

import java.util.HashMap; // HashMap הוא מבנה נתונים המאחסן זוגות של מפתח-ערך - כמו מילון
import java.util.Map; // Map הוא הממשק (interface) ש-HashMap מממש

// AppInitializer מרחיב את Application - זה אומר שאנדרואיד יריץ את המחלקה הזו ראשונה לפני שכל מסך נפתח
// תפקידה הוא לאתחל ולהגדיר את כל מה שהאפליקציה צריכה לפני שכל מסך נפתח
public class AppInitializer extends Application {

    @Override
    public void onCreate() { // onCreate רץ אוטומטית כשהאפליקציה מתחילה לראשונה - לפני כל Activity
        super.onCreate(); // תמיד קרא ל-onCreate של האב תחילה - זה נדרש

        initCloudinary(); // קריאה למתודה שלנו להגדרת Cloudinary
    }

    private void initCloudinary() {
        Map<String, Object> config = new HashMap<>();
        config.put("cloud_name", "djbp6p30q"); // שם הענן שלנו ב-Cloudinary - מזהה את החשבון שלנו
        config.put("api_key", "124468166385282"); // מפתח ה-API נדרש על ידי ה-SDK - הוא אינו סוד
        config.put("api_secret", "Oea9YawwemULIaW8SNZD936pL6U"); // נדרש על ידי ה-SDK לצורך אתחול
        // למרות שאנחנו כוללים את ה-secret כאן, ההעלאות שלנו עדיין אינן חתומות (unsigned)
        // מכיוון שאנו משתמשים ב-upload_preset לא חתום בקריאות ההעלאה שלנו
        // זה אומר שלא נעשה שימוש בתכונות בתשלום ואנחנו נשארים במסלול החינמי
        config.put("secure", true); // שימוש ב-HTTPS עבור כל כתובות ה-URL של התמונות

        MediaManager.init(this, config);
    }
}
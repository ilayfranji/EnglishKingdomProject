package com.ilay.englishkingdom;

import android.app.Application; // Application היא מחלקת בסיס של אנדרואיד שרצה לפני שכל Activity מתחילה

import com.cloudinary.android.MediaManager; // MediaManager היא המחלקה הראשית של Cloudinary - אנו משתמשים בה להעלאת תמונות

import java.util.HashMap; // HashMap הוא מבנה נתונים המאחסן זוגות של מפתח-ערך - כמו מילון
import java.util.Map; // Map הוא הממשק (interface) ש-HashMap מממש

public class AppInitializer extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        initCloudinary(); // קריאה למתודה שלנו להגדרת Cloudinary
    }

    private void initCloudinary() {
        Map<String, Object> config = new HashMap<>();// יצירת האש מאפ שדרכו נעביר את כל הנתונים שצריך להביא לקלאודינארי
        config.put("cloud_name", "djbp6p30q"); //הוספת תיקיית הענן ואת המזהה הייחודי שלה להאש מאפ
        config.put("api_key", "124468166385282"); // הוספת API קי של הענן להאש מאפ, משמש כמאין תז שמזהה את האפליקציה מול הענן
        config.put("api_secret", "Oea9YawwemULIaW8SNZD936pL6U"); //הוספת api סודי,משמש לאימות ואבטחת הקשר מול השרת
        config.put("secure", true); // דואג שכל התמונות יעברו דרך קישורים מאובטחים ככה שאנדרואיד לא תחסום אותם כשיחזרו

        MediaManager.init(this, config);//הפעלת האתחול הרשמי של קלאודינארי באפליקציה תוך שליחת ההקשר הנוכחי והאש מאפ שהכנו
    }
}